package edu.uci.eecs.crowdsafe.analysis.loader;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import utils.AnalysisUtil;

import com.google.common.io.LittleEndianDataInputStream;

import edu.uci.eecs.crowdsafe.analysis.datasource.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.analysis.datasource.ExecutionTraceStreamType;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.InvalidTagException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.MultipleEdgeException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.OverlapModuleException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.TagNotFoundException;
import edu.uci.eecs.crowdsafe.analysis.graph.debug.DebugUtils;
import edu.uci.eecs.crowdsafe.analysis.graph.representation.Edge;
import edu.uci.eecs.crowdsafe.analysis.graph.representation.EdgeType;
import edu.uci.eecs.crowdsafe.analysis.graph.representation.MetaNodeType;
import edu.uci.eecs.crowdsafe.analysis.graph.representation.ModuleDescriptor;
import edu.uci.eecs.crowdsafe.analysis.graph.representation.ModuleGraph;
import edu.uci.eecs.crowdsafe.analysis.graph.representation.Node;
import edu.uci.eecs.crowdsafe.analysis.graph.representation.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.analysis.graph.representation.VersionedTag;

public class ExecutionGraphDataLoader {

	public static ProcessExecutionGraph loadProcessGraph(File dir) {
		ProcessExecutionGraph graph = new ProcessExecutionGraph();
		graph.normalizeTags();
	}

	/**
	 * Assume the module file is organized in the follwoing way: Module USERENV.dll: 0x722a0000 - 0x722b7000
	 * 
	 * @param fileName
	 * @return
	 * @throws OverlapModuleException
	 */
	public static List<ModuleDescriptor> loadModules(
			ExecutionTraceDataSource traceDataSource)
			throws OverlapModuleException {
		ArrayList<ModuleDescriptor> res = new ArrayList<ModuleDescriptor>();
		try {
			BufferedReader br = new BufferedReader(
					new InputStreamReader(
							traceDataSource
									.getDataInputStream(ExecutionTraceStreamType.MODULE)));
			try {
				String line;
				while ((line = br.readLine()) != null) {
					int beginIdx, endIdx;
					String name;
					long beginAddr, endAddr;

					if (!line.startsWith("Loaded")) {
						continue;
					}

					// Should change the index correspondingly if the
					// module file format is changed
					beginIdx = line.indexOf(" ");
					beginIdx = line.indexOf(" ", beginIdx + 1);
					endIdx = line.indexOf(":", 0);
					name = line.substring(beginIdx + 1, endIdx);
					name = name.toLowerCase();

					beginIdx = line.indexOf("x", endIdx);
					endIdx = line.indexOf(" ", beginIdx);
					beginAddr = Long.parseLong(
							line.substring(beginIdx + 1, endIdx), 16);

					beginIdx = line.indexOf("x", endIdx);
					endAddr = Long.parseLong(line.substring(beginIdx + 1), 16);

					ModuleDescriptor mod = new ModuleDescriptor(name,
							beginAddr, endAddr);
					if (!res.contains(mod)) {
						res.add(mod);
					}
				}
				// Check if there is any overlap between different modules
				for (int i = 0; i < res.size(); i++) {
					for (int j = i + 1; j < res.size(); j++) {
						ModuleDescriptor mod1 = res.get(i), mod2 = res.get(j);
						if ((mod1.beginAddr < mod2.beginAddr && mod1.endAddr > mod2.beginAddr)
								|| (mod1.beginAddr < mod2.endAddr && mod1.endAddr > mod2.endAddr)) {
							String msg = "Module overlap happens!\n" + mod1
									+ " & " + mod2;
							// throw new OverlapModuleException(msg);
						}
					}
				}

				Collections.sort(res);
				return res;

			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return null;
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
	private Map<VersionedTag, Node> loadGraphNodes(
			ExecutionTraceDataSource traceDataSource)
			throws InvalidTagException {
		Map<VersionedTag, Node> hashLookupTable = new HashMap<VersionedTag, Node>();

		// Just a ad-hoc patch for the bug in the graph lookup file
		Map<Long, Integer> tag2CurrentVersion_ = new HashMap<Long, Integer>();

		try {
			// TODO: close all these files when finished
			LittleEndianDataInputStream input = new LittleEndianDataInputStream(
					traceDataSource
							.getDataInputStream(ExecutionTraceStreamType.GRAPH_HASH));
			long tag = 0, tagOriginal = 0, hash = 0;
			String nodeModuleName;

			while (true) {
				tagOriginal = input.readLong();
				hash = input.readLong();

				tag = getTagEffectiveValue(tagOriginal);
				int versionNumber = getNodeVersionNumber(tagOriginal), metaNodeVal = getNodeMetaVal(tagOriginal);
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
							isValidGraph = false;
							String msg = "Duplicate tags: "
									+ versionedTag
									+ " -> "
									+ Long.toHexString(hashLookupTable.get(
											versionedTag).getHash()) + ":"
									+ Long.toHexString(hash) + "  "
									+ traceDataSource.toString();
							if (DebugUtils.ThrowInvalidTag) {
								throw new InvalidTagException(msg);
							}
						}
					}
				}
				nodeModuleName = AnalysisUtil.getModuleName(this, tag);

				// Be careful about the index here!!!
				// If the node is in a core module, the addModuleNode
				// function should fix the index.
				Node node = new Node(this, versionedTag, hash, nodes.size(),
						metaNodeType);

				if (isCoreModule(nodeModuleName)) {
					ModuleGraph moduleGraph;
					if (!moduleGraphs.containsKey(nodeModuleName)) {
						moduleGraph = new ModuleGraph(nodeModuleName, pid,
								modules);
						moduleGraph.setRunDir(runDir);
						moduleGraphs.put(nodeModuleName, moduleGraph);
					}
					moduleGraph = moduleGraphs.get(nodeModuleName);
					moduleGraph.addModuleNode(node);
					if (!node.getNormalizedTag().moduleName.equals("Unknown")) {
						moduleGraph.blockHashes.add(node.getHash());
					}
				} else {
					if (!node.getNormalizedTag().moduleName.equals("Unknown")) {
						blockHashes.add(node.getHash());
					}
					nodes.add(node);
					// Add it the the hash2Nodes mapping
					hash2Nodes.add(node);
				}
				hashLookupTable.put(versionedTag, node);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (EOFException e) {

		} catch (IOException e) {
			e.printStackTrace();
		}
		// Store the total block hash code
		for (VersionedTag versionedTag : hashLookupTable.keySet()) {
			totalBlockHashes.add(hashLookupTable.get(versionedTag).getHash());
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
	public void readCrossModuleEdges(ExecutionTraceDataSource traceDataSource,
			Map<VersionedTag, Node> hashLookupTable)
			throws MultipleEdgeException, InvalidTagException,
			TagNotFoundException {
		try {
			LittleEndianDataInputStream input = new LittleEndianDataInputStream(
					traceDataSource
							.getDataInputStream(ExecutionTraceStreamType.CROSS_MODULE_GRAPH));

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

				String node1ModName = AnalysisUtil.getModuleName(node1), node2ModName = AnalysisUtil
						.getModuleName(node2);

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

	public void readIntraModuleEdges(ExecutionTraceDataSource traceDataSource,
			Map<VersionedTag, Node> hashLookupTable)
			throws InvalidTagException, TagNotFoundException,
			MultipleEdgeException {
		try {
			LittleEndianDataInputStream input = new LittleEndianDataInputStream(
					traceDataSource
							.getDataInputStream(ExecutionTraceStreamType.MODULE_GRAPH));

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
				String node1ModName = AnalysisUtil.getModuleName(node1), node2ModName = AnalysisUtil
						.getModuleName(node2);
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
