package edu.uci.eecs.crowdsafe.analysis.data.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import utils.AnalysisUtil;

import edu.uci.eecs.crowdsafe.analysis.exception.graph.InvalidGraphException;

public class ExecutionGraphData {
	private final ProcessExecutionGraph process;

	// False means that the file doesn't exist or is in wrong format
	protected boolean isValidGraph = true;

	// nodes in an array in the read order from file
	protected List<Node> nodes;

	// Map from hash to ArrayList<Node>,
	// which also helps to find out the hash collisions
	protected NodeHashMap hash2Nodes;

	protected Set<Long> blockHashes;

	// Maps from post-processed relative tag to the node,
	// only for the sake of debugging and analysis
	public final Map<NormalizedTag, Node> normalizedTag2Node = new HashMap<NormalizedTag, Node>();

	public void addBlockHash(long hashcode) {
		blockHashes.add(hashcode);
	}
	
	public List<Node> getNodes() {
		return nodes;
	}

	public NodeList getNodesByHash(long l) {
		return hash2Nodes.get(l);
	}

	public Set<Long> getBlockHashes() {
		return blockHashes;
	}

	public void normalizeTags() {
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			long relativeTag = AnalysisUtil
					.getRelativeTag(process, n.getTag().tag);
			String moduleName = AnalysisUtil
					.getModuleName(process, n.getTag().tag);
			normalizedTag2Node.put(new NormalizedTag(moduleName, relativeTag),
					n);
		}
	}

	/**
	 * This function is called when the graph is constructed. It can contain any kind of analysis of the current
	 * ExecutionGraph and output it the the console.
	 * 
	 * It could actually contains information like: 1. Number of nodes & signature nodes in the main module and in every
	 * module. 2. Number of non-kernel modules in the main module.
	 */
	public void analyzeGraph() {
		// Output basic nodes info
		int nodeCnt = nodes.size(), realNodeCnt = nodes.size()
				- signature2Node.size();
		System.out.println("Number of nodes in the main module: " + nodeCnt);
		System.out.println("Number of real nodes in the main module: "
				+ realNodeCnt);
		System.out.println("Number of signature nodes in the main module: "
				+ signature2Node.size());
		for (String name : moduleGraphs.keySet()) {
			ModuleGraph mGraph = moduleGraphs.get(name);
			nodeCnt += mGraph.nodes.size();
			realNodeCnt += mGraph.nodes.size() - mGraph.signature2Node.size();
		}
		System.out.println("Number of nodes in all module: " + nodeCnt);
		System.out
				.println("Number of real nodes in all module: " + realNodeCnt);

		// List how many non-core modules in main
		ArrayList<String> modulesInMain = new ArrayList<String>();
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			String modName = n.getNormalizedTag().moduleName;
			if (modName.equals("Unknown") || modName.equals("")) {
				continue;
			}
			if (!modulesInMain.contains(modName)) {
				modulesInMain.add(modName);
			}
		}
		for (int i = 0; i < modulesInMain.size(); i++) {
			System.out.print(modulesInMain.get(i) + "  ");
		}
		System.out.println();
		System.out
				.println("Number of modules in main: " + modulesInMain.size());

		// Count the subgraph with the most reachable nodes
		int maxCnt = 0;
		long maxSigHash = -1;
		for (long sigHash : signature2Node.keySet()) {
			Node sigNode = signature2Node.get(sigHash);
			for (int k = 0; k < sigNode.getOutgoingEdges().size(); k++) {
				Node realEntryNode = sigNode.getOutgoingEdges().get(k)
						.getToNode();

				Queue<Node> bfsQueue = new LinkedList<Node>();
				int cnt = 0;
				for (int i = 0; i < nodes.size(); i++) {
					nodes.get(i).resetVisited();
				}
				bfsQueue.add(realEntryNode);

				while (bfsQueue.size() > 0) {
					Node n = bfsQueue.remove();
					n.setVisited();
					cnt++;
					for (int i = 0; i < n.getOutgoingEdges().size(); i++) {
						Node neighbor = n.getOutgoingEdges().get(i).getToNode();
						if (!neighbor.isVisited()) {
							bfsQueue.add(neighbor);
							neighbor.setVisited();
						}
					}
				}
				if (cnt > maxCnt) {
					maxCnt = cnt;
					maxSigHash = realEntryNode.getHash();
				}
			}
		}
		System.out.println(Long.toHexString(maxSigHash)
				+ " has the most reachable nodes (" + maxCnt + ").");
		HashSet<Node> accessibleNodes = getAccessibleNodes();
		System.out.println("Total reachable nodes: " + accessibleNodes.size());

		// TODO: config or parameter for this
		// Output the inaccessible nodes in the graph
		// for (int i = 0; i < nodes.size(); i++) {
		// Node n = nodes.get(i);
		// if (!accessibleNodes.contains(n)) {
		// System.out.println(n);
		// }
		// }
	}

	/**
	 * To validate the correctness of the graph. Basically it checks if entry points have no incoming edges, exit points
	 * have no outgoing edges. It might include more validation stuff later...
	 * 
	 * @return true means this is a valid graph, otherwise it's invalid
	 */
	public boolean validate() {
		// Check if the index of the node is correct
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			if (n.getIndex() != i) {
				System.out.println("Wrong index: " + n.getIndex());
				return false;
			}
		}

		outerLoop: for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			switch (n.getMetaNodeType()) {
				case ENTRY:
					if (n.getIncomingEdges().size() != 0) {
						throw new InvalidGraphException(
								"Exit point has outgoing edges!");
					}
					break;
				case EXIT:
					if (n.getOutgoingEdges().size() != 0) {
						System.out.println("");
						throw new InvalidGraphException(
								"Exit point has outgoing edges!");
					}
					break;
				default:
					break;
			}
		}
		return isValidGraph;
	}
}
