package edu.uci.eecs.crowdsafe.analysis.data.graph.execution;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import utils.AnalysisUtil;

import com.google.common.io.LittleEndianDataInputStream;

import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.analysis.data.graph.Edge;
import edu.uci.eecs.crowdsafe.analysis.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.analysis.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.analysis.datasource.ProcessTraceStreamType;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.InvalidGraphException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.InvalidTagException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.MultipleEdgeException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.TagNotFoundException;
import edu.uci.eecs.crowdsafe.analysis.log.graph.ProcessExecutionGraphSummary;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.DebugUtils;

public class ProcessGraphLoadSession {
	private final ProcessTraceDataSource dataSource;

	private ProcessExecutionGraph graph;
	private Map<ExecutionNode.Key, ExecutionNode> hashLookupTable;

	// Count how many wrong intra-module edges there are
	private int wrongIntraModuleEdgeCnt = 0;

	ProcessGraphLoadSession(ProcessTraceDataSource dataSource) {
		this.dataSource = dataSource;
	}

	ProcessExecutionGraph loadGraph() throws IOException {
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

		// Some other initialization and sanity checks
		for (ModuleGraphCluster cluster : graph.getAutonomousClusters()) {
			cluster.getGraphData().validate();
		}

		// Produce some analysis result for the graph
		ProcessExecutionGraphSummary.summarizeGraph(graph);

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
	private Map<ExecutionNode.Key, ExecutionNode> loadGraphNodes()
			throws IOException {
		Map<ExecutionNode.Key, ExecutionNode> hashLookupTable = new HashMap<ExecutionNode.Key, ExecutionNode>();

		LittleEndianDataInputStream input = new LittleEndianDataInputStream(
				dataSource
						.getDataInputStream(ProcessTraceStreamType.GRAPH_HASH));
		try {
			long tag = 0, tagOriginal = 0, hash = 0;

			while (input.available() > 0) {
				tagOriginal = input.readLong();
				hash = input.readLong();

				tag = AnalysisUtil.getTagEffectiveValue(tagOriginal);
				int versionNumber = AnalysisUtil
						.getNodeVersionNumber(tagOriginal);
				int metaNodeVal = AnalysisUtil.getNodeMetaVal(tagOriginal);
				ExecutionNode.Key versionedTag = new ExecutionNode.Key(tag,
						versionNumber);

				MetaNodeType metaNodeType = MetaNodeType.values()[metaNodeVal];

				// Tags don't duplicate in lookup file
				if (hashLookupTable.containsKey(versionedTag)) {
					ExecutionNode existingNode = hashLookupTable
							.get(versionedTag);
					if (existingNode.getHash() != hash) {
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
				SoftwareDistributionUnit nodeSoftwareUnit = graph.getModules()
						.getModule(tag).unit;

				ExecutionNode node = new ExecutionNode(graph, metaNodeType,
						tag, versionNumber, hash);

				ModuleGraphCluster moduleCluster = graph
						.getModuleGraphCluster(nodeSoftwareUnit);
				ModuleGraph moduleGraph = moduleCluster
						.getModuleGraph(nodeSoftwareUnit);
				if (moduleGraph == null) {
					moduleGraph = new ModuleGraph(graph, nodeSoftwareUnit);
					moduleCluster.addModule(moduleGraph);
				}
				moduleCluster.addModuleNode(node);
				hashLookupTable.put(versionedTag, node);
				graph.addBlockHash(hashLookupTable.get(versionedTag).getHash());
			}
		} finally {
			input.close();
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
	public void readCrossModuleEdges() throws IOException {
		LittleEndianDataInputStream input = new LittleEndianDataInputStream(
				dataSource
						.getDataInputStream(ProcessTraceStreamType.CROSS_MODULE_GRAPH));
		try {
			while (input.available() > 0) {
				long fromTag = input.readLong();
				long toTag = input.readLong();
				long signatureHash = input.readLong();

				ExecutionNode.Key fromKey = AnalysisUtil.getNodeKey(fromTag);
				ExecutionNode.Key toKey = AnalysisUtil.getNodeKey(toTag);
				EdgeType edgeType = AnalysisUtil.getTagEdgeType(fromTag);
				int edgeOrdinal = AnalysisUtil.getEdgeOrdinal(fromTag);

				ExecutionNode fromNode = hashLookupTable.get(fromKey);
				ExecutionNode toNode = hashLookupTable.get(toKey);

				// Double check if tag1 and tag2 exist in the lookup file
				if (fromNode == null) {
					if (DebugUtils.ThrowTagNotFound) {
						throw new TagNotFoundException("0x"
								+ Long.toHexString(fromTag)
								+ " is missed in graph lookup file!");
					}
				}
				if (toNode == null) {
					if (DebugUtils.ThrowTagNotFound) {
						throw new TagNotFoundException("0x"
								+ Long.toHexString(toTag)
								+ " is missed in graph lookup file!");
					}
				}
				if (fromNode == null || toNode == null) {
					if (!DebugUtils.ThrowTagNotFound) {
						continue;
					}
				}

				Edge<ExecutionNode> existing = fromNode.getOutgoingEdge(toNode);
				if (existing == null) {
					SoftwareDistributionUnit fromUnit = graph.getModules()
							.getModule(fromNode.getTag()).unit;
					SoftwareDistributionUnit toUnit = graph.getModules()
							.getModule(toNode.getTag()).unit;
					// Be careful when dealing with the cross module nodes.
					// Cross-module edges are not added to any node, but the
					// edge from signature node to real entry node is preserved.
					// We only need to add the signature nodes to "nodes"
					fromNode.setMetaNodeType(MetaNodeType.MODULE_BOUNDARY);
					ModuleGraphCluster fromCluster = graph
							.getModuleGraphCluster(fromUnit);

					ModuleGraphCluster toCluster = graph
							.getModuleGraphCluster(toUnit);

					Edge<ExecutionNode> e = new Edge<ExecutionNode>(fromNode,
							toNode, edgeType, edgeOrdinal);
					if (fromCluster == toCluster) {
						fromNode.addOutgoingEdge(e);
						toNode.addIncomingEdge(e);
					} else {
						ExecutionNode exitNode = new ExecutionNode(graph,
								MetaNodeType.CLUSTER_EXIT, signatureHash, 0,
								signatureHash);
						fromCluster.addNode(exitNode);
						fromNode.setMetaNodeType(MetaNodeType.NORMAL);
						Edge<ExecutionNode> clusterExitEdge = new Edge<ExecutionNode>(
								fromNode, exitNode, EdgeType.CROSS_MODULE, 0);
						fromNode.addOutgoingEdge(clusterExitEdge);
						exitNode.addIncomingEdge(clusterExitEdge);

						ExecutionNode entryNode = toCluster
								.addClusterEntryNode(signatureHash);
						toNode.setMetaNodeType(MetaNodeType.NORMAL);
						Edge<ExecutionNode> clusterEntryEdge = new Edge<ExecutionNode>(
								entryNode, toNode, EdgeType.CROSS_MODULE, 0);
						entryNode.addOutgoingEdge(clusterEntryEdge);
						toNode.addIncomingEdge(clusterEntryEdge);
					}
				}
			}
		} finally {
			input.close();
		}
	}

	public void readIntraModuleEdges() throws IOException {
		LittleEndianDataInputStream input = new LittleEndianDataInputStream(
				dataSource
						.getDataInputStream(ProcessTraceStreamType.MODULE_GRAPH));

		try {
			while (input.available() > 0) {
				long tag1 = input.readLong();
				long tag2 = input.readLong();

				ExecutionNode.Key nodeKey1 = AnalysisUtil.getNodeKey(tag1);
				ExecutionNode.Key nodeKey2 = AnalysisUtil.getNodeKey(tag2);
				EdgeType edgeType = AnalysisUtil.getTagEdgeType(tag1);
				int edgeOrdinal = AnalysisUtil.getEdgeOrdinal(tag1);

				ExecutionNode node1 = hashLookupTable.get(nodeKey1), node2 = hashLookupTable
						.get(nodeKey2);

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
				SoftwareDistributionUnit node1Unit = graph.getModules()
						.getModule(node1.getTag()).unit;
				SoftwareDistributionUnit node2Unit = graph.getModules()
						.getModule(node2.getTag()).unit;
				if (node1Unit == SoftwareDistributionUnit.UNKNOWN
						|| node2Unit == SoftwareDistributionUnit.UNKNOWN) {
					continue;
				}

				if ((node1Unit != node2Unit)
						&& (graph.getModuleGraphCluster(node1Unit) != graph
								.getModuleGraphCluster(node2Unit))) {
					throw new InvalidGraphException(
							String.format(
									"Error: a normal edge [0x%x - 0x%x] crosses between module %s and %s",
									node1.getTag(),
									node2.getTag(),
									graph.getModuleGraphCluster(node1Unit).distribution.name,
									graph.getModuleGraphCluster(node2Unit).distribution.name));
				}

				Edge<ExecutionNode> existing = node1.getOutgoingEdge(node2);
				Edge<ExecutionNode> e = new Edge<ExecutionNode>(node1, node2,
						edgeType, edgeOrdinal);
				if (existing == null) {
					node1.addOutgoingEdge(e);
					node2.addIncomingEdge(e);
				} else {
					if (!existing.equals(e)) {
						// One wired case to deal with here:
						// A call edge (direct) and a continuation edge can
						// point to the same block
						if ((existing.getEdgeType() == EdgeType.DIRECT && e
								.getEdgeType() == EdgeType.CALL_CONTINUATION)
								|| (existing.getEdgeType() == EdgeType.CALL_CONTINUATION && e
										.getEdgeType() == EdgeType.DIRECT)) {
							existing.setEdgeType(EdgeType.DIRECT);
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
		} finally {
			input.close();
		}

		// Output the count for wrong edges if there is any
		if (wrongIntraModuleEdgeCnt > 0) {
			System.out.println("There are " + wrongIntraModuleEdgeCnt
					+ " cross-module edges in the intra-module edge file");
		}
	}
}
