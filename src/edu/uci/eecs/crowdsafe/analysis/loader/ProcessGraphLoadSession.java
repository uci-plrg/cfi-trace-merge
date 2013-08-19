package edu.uci.eecs.crowdsafe.analysis.loader;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import utils.AnalysisUtil;

import com.google.common.io.LittleEndianDataInputStream;

import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.analysis.data.graph.Edge;
import edu.uci.eecs.crowdsafe.analysis.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.ModuleDescriptor;
import edu.uci.eecs.crowdsafe.analysis.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.analysis.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.analysis.data.graph.Node;
import edu.uci.eecs.crowdsafe.analysis.data.graph.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.analysis.data.graph.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.analysis.data.graph.VersionedTag;
import edu.uci.eecs.crowdsafe.analysis.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.analysis.datasource.ProcessTraceStreamType;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.InvalidGraphException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.InvalidTagException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.MultipleEdgeException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.OverlapModuleException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.TagNotFoundException;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.DebugUtils;

public class ProcessGraphLoadSession {
	private final ProcessTraceDataSource dataSource;

	private ProcessExecutionGraph graph;
	private Map<VersionedTag, Node> hashLookupTable;

	ProcessGraphLoadSession(ProcessTraceDataSource dataSource) {
		this.dataSource = dataSource;
	}

	ProcessExecutionGraph loadGraph() {
		ProcessExecutionModuleSet modules = ProcessModuleLoader
				.loadModules(dataSource);
		graph = new ProcessExecutionGraph(dataSource, modules);

		try {
			// Construct the tag--hash lookup table
			hashLookupTable = loadGraphNodes();
			// Then read both intra- and cross- module edges from files
			readIntraModuleEdges();
			readCrossModuleEdges();
		} catch (InvalidTagException e) {
			throw new InvalidGraphException(e);
		} catch (TagNotFoundException e) {
			throw new InvalidGraphException(e);
		} catch (MultipleEdgeException e) {
			throw new InvalidGraphException(e);
		}

		// TODO: these occur on each ExecutionGraphData
		// Some other initialization and sanity checks
		validate();
		// Produce some analysis result for the graph
		analyzeGraph();

		graph.normalizeTags();
		return graph;
	}

	/**
	 * <p>
	 * The format of the lookup file is as the following:
	 * </p>
	 * <p>
	 * Each entry consists of 8-byte tag + 8-byte hash.
	 * </p>
	 * <p>
	 * 8-byte tag: 1-byte version number | 1-byte node type | 6-byte tag
	 * </p>
	 * 
	 * 
	 * @param lookupFiles
	 * @return
	 * @throws InvalidTagException
	 */
	private Map<VersionedTag, Node> loadGraphNodes() throws InvalidTagException {
		Map<VersionedTag, Node> hashLookupTable = new HashMap<VersionedTag, Node>();

		// Just a ad-hoc patch for the bug in the graph lookup file
		Map<Long, Integer> tag2CurrentVersion_ = new HashMap<Long, Integer>();

		try {
			// TODO: close all these files when finished
			LittleEndianDataInputStream input = new LittleEndianDataInputStream(
					dataSource
							.getDataInputStream(ProcessTraceStreamType.GRAPH_HASH));
			long tag = 0, tagOriginal = 0, hash = 0;
			String nodeModuleName;

			while (true) {
				tagOriginal = input.readLong();
				hash = input.readLong();

				tag = AnalysisUtil.getTagEffectiveValue(tagOriginal);
				int versionNumber = AnalysisUtil
						.getNodeVersionNumber(tagOriginal);
				int metaNodeVal = AnalysisUtil.getNodeMetaVal(tagOriginal);
				VersionedTag versionedTag = new VersionedTag(tag, versionNumber);
				// Only for the ad-hoc fix of graph lookup file
				if (!tag2CurrentVersion_.containsKey(tag)) {
					tag2CurrentVersion_.put(tag, 0);
				} else if (tag2CurrentVersion_.get(tag) < versionNumber) {
					tag2CurrentVersion_.put(tag, versionNumber);
				}

				MetaNodeType metaNodeType = MetaNodeType.values()[metaNodeVal];

				// Tags don't duplicate in lookup file
				if (hashLookupTable.containsKey(versionedTag)) {
					Node existingNode = hashLookupTable.get(versionedTag);
					if (existingNode.getHash() != hash) {
						// Patch the buggy tag version manually
						if (versionedTag.versionNumber == 0
								&& tag2CurrentVersion_.get(tag) > 0) {
							versionNumber = tag2CurrentVersion_.get(tag);
							versionedTag = new VersionedTag(tag, versionNumber);
						} else {
							String msg = "Duplicate tags: "
									+ versionedTag
									+ " -> "
									+ Long.toHexString(hashLookupTable.get(
											versionedTag).getHash()) + ":"
									+ Long.toHexString(hash) + "  "
									+ dataSource.toString();
							throw new InvalidTagException(msg);
						}
					}
				}
				SoftwareDistributionUnit nodeSoftwareUnit = graph.getModules()
						.getSoftwareUnit(tag);

				// TODO: identify the index
				Node node = new Node(graph, versionedTag, hash, metaNodeType);

				ModuleGraphCluster moduleCluster = graph
						.getModuleGraphCluster(nodeSoftwareUnit);
				ModuleGraph moduleGraph = moduleCluster
						.getModuleGraph(nodeSoftwareUnit);
				if (moduleGraph == null) {
					moduleGraph = new ModuleGraph(nodeSoftwareUnit);
					moduleCluster.addModule(moduleGraph);
				}
				moduleGraph.addModuleNode(node);
				if (nodeSoftwareUnit != SoftwareDistributionUnit.UNKNOWN) {
					moduleGraph.getGraphData().addBlockHash(node.getHash());
				}
				hashLookupTable.put(versionedTag, node);
				graph.addBlockHash(hashLookupTable.get(versionedTag).getHash());
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (EOFException e) {

		} catch (IOException e) {
			e.printStackTrace();
		}

		return hashLookupTable;
	}

	/**
	 * Before calling this function, you should have all the normal nodes added to the corresponding graph and their
	 * indexes fixed. The only thing this function should do is to add signature nodes when necessary and build the
	 * necessary edges between them and real entry nodes.
	 * 
	 * @param crossModuleEdgeFile
	 * @param hashLookupTable
	 * @throws MultipleEdgeException
	 * @throws InvalidTagException
	 * @throws TagNotFoundException
	 */
	public void readCrossModuleEdges()
			throws MultipleEdgeException, InvalidTagException,
			TagNotFoundException {
		try {
			LittleEndianDataInputStream input = new LittleEndianDataInputStream(
					dataSource
							.getDataInputStream(ProcessTraceStreamType.CROSS_MODULE_GRAPH));

			while (true) {
				long tag1 = input.readLong();
				long tag2 = input.readLong();
				long signatureHash = input.readLong();

				VersionedTag versionedTag1 = getVersionedTag(tag1), versionedTag2 = getVersionedTag(tag2);
				EdgeType edgeType = getTagEdgeType(tag1);
				int edgeOrdinal = getEdgeOrdinal(tag1);

				Node node1 = hashLookupTable.get(versionedTag1), node2 = hashLookupTable
						.get(versionedTag2);

				// Double check if tag1 and tag2 exist in the lookup file
				if (node1 == null) {
					if (DebugUtils.ThrowTagNotFound) {
						throw new TagNotFoundException("0x"
								+ Long.toHexString(tag1)
								+ " is missed in graph lookup file!");
					}
				}
				if (node2 == null) {
					if (DebugUtils.ThrowTagNotFound) {
						throw new TagNotFoundException("0x"
								+ Long.toHexString(tag2)
								+ " is missed in graph lookup file!");
					}
				}
				if (node1 == null || node2 == null) {
					if (!DebugUtils.ThrowTagNotFound) {
						continue;
					}
				}

				Edge existing = node1.getOutgoingEdge(node2);
				Edge e;

				String node1ModName = AnalysisUtil.getSoftwareUnit(node1), node2ModName = AnalysisUtil
						.getSoftwareUnit(node2);

				e = new Edge(node1, node2, edgeType, edgeOrdinal);

				if (existing == null) {
					// Be careful when dealing with the cross module nodes.
					// Cross-module edges are not added to any node, but the
					// edge from signature node to real entry node is preserved.
					// We onlly need to add the signature nodes to "nodes"
					node1.setMetaNodeType(MetaNodeType.MODULE_BOUNDARY);
					if (isCoreModule(node2ModName)) {
						ModuleGraph moduleGraph = moduleGraphs
								.get(node2ModName);
						// Make sure the signature node is added
						moduleGraph.addSignatureNode(signatureHash);
						Node sigNode = moduleGraph.signature2Node
								.get(signatureHash);

						node2.setMetaNodeType(MetaNodeType.NORMAl);
						Edge sigEntryEdge = new Edge(sigNode, node2,
								EdgeType.CrossKernelModule, 0);
						sigNode.addOutgoingEdge(sigEntryEdge);
						node2.addIncomingEdge(sigEntryEdge);
					} else if (isCoreModule(node1ModName)) {
						// Make sure the signature node is added
						addSignatureNode(signatureHash);
						Node sigNode = signature2Node.get(signatureHash);

						e = new Edge(sigNode, node2,
								EdgeType.CrossKernelModule, 0);
						sigNode.addOutgoingEdge(e);
						node2.addIncomingEdge(e);
					} else {
						node1.addOutgoingEdge(e);
						node2.addIncomingEdge(e);
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (EOFException e) {
			// System.out.println("Finiish reading the file: " + fileName);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Count how many wrong intra-module edges there are
	private int wrongIntraModuleEdgeCnt = 0;

	public void readIntraModuleEdges()
			throws InvalidTagException, TagNotFoundException,
			MultipleEdgeException {
		try {
			LittleEndianDataInputStream input = new LittleEndianDataInputStream(
					dataSource
							.getDataInputStream(ProcessTraceStreamType.MODULE_GRAPH));

			while (true) {
				long tag1 = input.readLong();
				long tag2 = input.readLong();

				VersionedTag versionedTag1 = getVersionedTag(tag1), versionedTag2 = getVersionedTag(tag2);
				EdgeType edgeType = getTagEdgeType(tag1);
				int edgeOrdinal = getEdgeOrdinal(tag1);

				Node node1 = hashLookupTable.get(versionedTag1), node2 = hashLookupTable
						.get(versionedTag2);

				// Double check if tag1 and tag2 exist in the lookup file
				if (node1 == null) {
					if (DebugUtils.ThrowTagNotFound) {
						throw new TagNotFoundException("0x"
								+ Long.toHexString(tag1)
								+ " is missed in graph lookup file!");
					}
				}
				if (node2 == null) {
					if (DebugUtils.ThrowTagNotFound) {
						throw new TagNotFoundException("0x"
								+ Long.toHexString(tag2)
								+ " is missed in graph lookup file!");
					}
				}
				if (node1 == null || node2 == null) {
					if (!DebugUtils.ThrowTagNotFound) {
						continue;
					}
				}

				// If one of the node locates in the "unknown" module,
				// simply discard those edges
				String node1ModName = AnalysisUtil.getSoftwareUnit(node1), node2ModName = AnalysisUtil
						.getSoftwareUnit(node2);
				if (node1ModName.equals("Unknown")
						|| node2ModName.equals("Unknown")) {
					continue;
				}

				if ((!node1ModName.equals(node2ModName))
						&& (isCoreModule(node1ModName) || isCoreModule(node2ModName))) {
					wrongIntraModuleEdgeCnt++;
					// Ignore those wrong edges at this point
					// System.out.println(node1 + "=>" + node2);
					continue;
				}

				Edge existing = node1.getOutgoingEdge(node2);
				Edge e = new Edge(node1, node2, edgeType, edgeOrdinal);
				if (existing == null) {
					node1.addOutgoingEdge(e);
					node2.addIncomingEdge(e);
				} else {
					if (!existing.equals(e)) {
						// One wired case to deal with here:
						// A call edge (direct) and a continuation edge can
						// point to the same block
						if ((existing.getEdgeType() == EdgeType.Direct && e
								.getEdgeType() == EdgeType.CallContinuation)
								|| (existing.getEdgeType() == EdgeType.CallContinuation && e
										.getEdgeType() == EdgeType.Direct)) {
							existing.setEdgeType(EdgeType.Direct);
						} else {
							String msg = "Multiple edges:\n" + "Edge1: "
									+ existing + "\n" + "Edge2: " + e;
							System.out.println(msg);
							if (DebugUtils.ThrowMultipleEdge) {
								throw new MultipleEdgeException(msg);
							}
						}
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (EOFException e) {
			// System.out.println("Finish reading the file: " + fileName);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Output the count for wrong edges if there is any
		if (wrongIntraModuleEdgeCnt > 0) {
			System.out.println("There are " + wrongIntraModuleEdgeCnt
					+ " cross-module edges in the intra-module edge file");
		}
	}
}
