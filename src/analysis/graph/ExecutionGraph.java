package analysis.graph;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

import sun.org.mozilla.javascript.Node;
import utils.AnalysisUtil;

public class ExecutionGraph {
	static public class Node {
		private long tag, hash;

		private ArrayList<Edge> edges = new ArrayList<Edge>();
		private boolean isVisited;
		// Index in the ArrayList<Node>
		private int index;
		// Divide the node into 3 groups
		private int fromWhichGraph = -1;

		int score = 0;

		public String toString() {
			return Long.toHexString(hash) + ":" + score + ":" + index;
		}

		public Node(Node anotherNode) {
			this(anotherNode.tag, anotherNode.hash);
			this.index = anotherNode.index;
			this.score = anotherNode.score;
			// FIXME: Not a deep copy yet, because we have edges...
		}

		public Node(long tag, long hash) {
			this.tag = tag;
			this.hash = hash;
			isVisited = false;
		}

		public Node(long tag) {
			this.tag = tag;
			isVisited = false;
		}

		/**
		 * In a single execution, tag is the only identifier for the node
		 */
		public boolean equals(Object o) {
			if (o == null)
				return false;
			if (o.getClass() != Node.class) {
				return false;
			}
			Node node = (Node) o;
			if (node.tag == tag)
				return true;
			else
				return false;
		}

		public int hashCode() {
			return ((Long) tag).hashCode() << 5 ^ ((Long) hash).hashCode();
		}
	}

	public static class Edge {
		Node node;
		boolean isDirect;
		int ordinal;
		boolean marked = false;

		public Edge(Node node, boolean isDirect, int ordinal) {
			this.node = node;
			this.isDirect = isDirect;
			this.ordinal = ordinal;
		}

		public Edge(Node node, int flag) {
			this.node = node;
			this.ordinal = flag % 256;
			isDirect = flag / 256 == 1;
		}

		public boolean equals(Object o) {
			if (o == null)
				return false;
			if (o.getClass() != Edge.class)
				return false;
			Edge e = (Edge) o;
			if (e.node.index == node.index && e.isDirect == isDirect
					&& e.ordinal == ordinal)
				return true;
			return false;

		}

		public int hashCode() {
			return node.index;
		}
	}

	public static class PairEdge {
		public Node parent, child;
		public boolean isDirect;
		public int ordinal;

		public PairEdge(Node parent, Node child, boolean isDirect, int ordinal) {
			this.parent = parent;
			this.child = child;
			this.isDirect = isDirect;
			this.ordinal = ordinal;
		}
	}
	
	public static class PairNode {
		public Node n1, n2;
		
		public PairNode(Node n1, Node n2) {
			this.n1 = n1;
			this.n2 = n2;
		}
	}

	private HashSet<Long> pairHashes;
	private HashSet<Long> blockHashes;
	private ArrayList<Long> pairHashInstances;
	private ArrayList<Long> blockHashInstances;

	private String pairHashFile;
	private String blockHashFile;

	// the edges of the graph comes with an ordinal
	private HashMap<Node, HashMap<Node, Integer>> adjacentList;

	private String runDirName;

	private String progName;

	private int pid;

	private static boolean hasConflict = false;

	// nodes in an array in the read order from file
	private ArrayList<Node> nodes;

	private HashMap<Long, Node> hashLookupTable;

	// Map from hash to ArrayList<Node>,
	// which also helps to find out the hash collisions
	private HashMap<Long, ArrayList<Node>> hash2Nodes;

	private HashSet<Long> blockHash;

	public void setBlockHash(String fileName) {
		blockHash = AnalysisUtil.getSetFromPath(fileName);
	}

	// if false, it means that the file doesn't exist or is in wrong format
	private boolean isValidGraph = true;

	public ExecutionGraph(ExecutionGraph anotherGraph) {
		this.runDirName = anotherGraph.runDirName;
		this.progName = anotherGraph.progName;
		// Copy the nodes, lookup table and hash2Nodes mapping
		// all at once
		nodes = new ArrayList<Node>(anotherGraph.nodes.size());
		hashLookupTable = new HashMap<Long, Node>();
		hash2Nodes = new HashMap<Long, ArrayList<Node>>();
		for (int i = 0; i < anotherGraph.nodes.size(); i++) {
			Node anotherNode = anotherGraph.nodes.get(i), thisNode = new Node(
					anotherNode);
			nodes.add(thisNode);
			// Copy the lookup table
			hashLookupTable.put(thisNode.tag, thisNode);
			// Copy the hash2Nodes
			if (hash2Nodes.get(thisNode.hash) == null) {
				hash2Nodes.put(thisNode.hash, new ArrayList());
			}
			if (!hash2Nodes.get(thisNode.hash).contains(thisNode)) {
				hash2Nodes.get(thisNode.hash).add(thisNode);
			}
		}

		// Copy the adjacentList
		adjacentList = new HashMap<Node, HashMap<Node, Integer>>();
		for (Node fromNode : anotherGraph.adjacentList.keySet()) {
			Node thisFromNode = hashLookupTable.get(fromNode.tag);
			HashMap<Node, Integer> map = new HashMap<Node, Integer>();

			for (Node toNode : anotherGraph.adjacentList.get(fromNode).keySet()) {
				Node thisToNode = hashLookupTable.get(toNode.tag);
				int edgeFlag = anotherGraph.adjacentList.get(fromNode).get(
						toNode);
				map.put(thisToNode, edgeFlag);
			}
			adjacentList.put(thisFromNode, map);
		}

		if (isSameGraph(this, anotherGraph))
			System.out.println("Graph copying error!");
	}

	public ExecutionGraph() {
		adjacentList = new HashMap<Node, HashMap<Node, Integer>>();
		hash2Nodes = new HashMap<Long, ArrayList<Node>>();
	}

	public ExecutionGraph(ArrayList<String> tagFiles,
			ArrayList<String> lookupFiles) {
		adjacentList = new HashMap<Node, HashMap<Node, Integer>>();
		hash2Nodes = new HashMap<Long, ArrayList<Node>>();

		this.progName = AnalysisUtil.getProgName(tagFiles.get(0));
		this.pid = AnalysisUtil.getPidFromFileName(tagFiles.get(0));
		readGraphLookup(lookupFiles);
		readGraph(tagFiles);
		if (!isValidGraph) {
			System.out.println("Pid " + pid + " is not a valid graph!");
		}
	}

	public String getProgName() {
		return progName;
	}

	private void setProgName(String progName) {
		this.progName = progName;
	}

	/**
	 * Use tag as an identifier, just a simple function to check the correctness
	 * of copying a graph
	 * 
	 * @param graph1
	 * @param graph2
	 * @return
	 */
	private static boolean isSameGraph(ExecutionGraph graph1,
			ExecutionGraph graph2) {
		if (graph1.nodes.size() != graph2.nodes.size())
			return false;
		if (!graph1.nodes.equals(graph2.nodes))
			return false;
		if (graph1.adjacentList.equals(graph2.adjacentList))
			return false;
		return true;
	}

	/**
	 * try to merge two graphs !!! Seems that every two graphs can be merged, so
	 * maybe there should be a way to evaluate how much the two graphs conflict
	 * One case is unmergeable: two direct branch nodes with same hash value but
	 * have different branch targets (Seems wired!!)
	 * 
	 * ####42696542a8bb5822 I am doing a trick here: programs in x86/linux seems
	 * to enter their main function after a very similar dynamic-loading
	 * process, at the end of which there is a indirect branch which jumps to
	 * the real main blocks. In the environment of this machine, the hash value
	 * of that 'final block' is 0x1d84443b9bf8a6b3. ####
	 * 
	 * FIXME Something's wrong here, the block that finally jumps to main is
	 * 0x4f1f7a5c30ae8622, and the previously found node is actually from the
	 * constructor of the program (__libc_csu_init). Things might get wrong
	 * here!!!
	 * 
	 * @param otherGraph
	 */
	private static final long specialHash = new BigInteger("4f1f7a5c30ae8622",
			16).longValue();
	private static final long beginHash = 0x5eee92;

	private static Node getCorrespondingNode(ExecutionGraph graph1,
			ExecutionGraph graph2, Node node2,
			HashMap<Integer, Integer> mergedNodes21,
			HashMap<Integer, Integer> mergedNodes12) {
		// First check if this is a node already merged
		if (mergedNodes21.get(node2.index) != null) {
			return graph1.nodes.get(mergedNodes21.get(node2.index));
		}

		// This node does not belongs to G1 and
		// is not yet added to G1
		ArrayList<Node> nodes1 = graph1.hash2Nodes.get(node2.hash);
		if (nodes1 == null)
			return null;

		ArrayList<Node> candidates = new ArrayList<Node>();
		for (int i = 0; i < nodes1.size(); i++) {
			int score = 0;
			if ((score = getContextSimilarity(nodes1.get(i), node2, searchDepth)) != -1) {
				if (!mergedNodes12.containsKey(nodes1.get(i).index)) {
					nodes1.get(i).score = score;
					candidates.add(nodes1.get(i));
				}
			}
		}
		if (candidates.size() > 1) {
			// Returns the candidate with highest score
			int pos = 0, score = 0;
			for (int i = 0; i < candidates.size(); i++) {
				if (mergedNodes12.containsKey(candidates.get(i).index)) {
					continue;
				}
				if (candidates.get(i).score > score) {
					pos = i;
					score = candidates.get(i).score;
				}
			}
			// If the highest score is 0, we can't believe
			// that they are the same node
			Node mostSimilarNode = candidates.get(pos);
			if (mostSimilarNode.score > 0) {
				return mostSimilarNode;
			} else {
				return null;
			}
		} else if (candidates.size() == 1) {
			// If the highest score is 0, we can't believe
			// that they are the same node
			Node mostSimilarNode = candidates.get(0);
			if (mostSimilarNode.score > 0) {
				return mostSimilarNode;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	private static Node getCorrespondingChildNode(Node parentNode1,
			Edge curNodeEdge, HashMap<Integer, Integer> mergedNodes12) {
		Node node1 = null, curNode = curNodeEdge.node;
		ArrayList<Node> candidates = new ArrayList<Node>();
		for (int i = 0; i < parentNode1.edges.size(); i++) {
			Edge e = parentNode1.edges.get(i);
			if (e.ordinal == curNodeEdge.ordinal) {
				if (e.isDirect != curNodeEdge.isDirect) {
					System.out.println("Different branch type happened!");
					hasConflict = true;
					break;
				} else if (e.isDirect) {
					if (e.node.hash != curNode.hash) {
						System.out
								.println("Direct branch has different targets!");
						hasConflict = true;
						break;
					} else {
						return e.node;
					}
				} else {
					if (e.node.hash == curNode.hash) {
						int score = -1;
						if ((score = getContextSimilarity(e.node, curNode,
								ExecutionGraph.searchDepth)) > 0) {
							if (!mergedNodes12.containsKey(e.node.index)) {
								e.node.score = score;
								candidates.add(e.node);
							}
						}
					}
				}
			}

		}
		if (candidates.size() == 0) {
			return null;
		} else {
			int pos = 0, score = -1;
			for (int i = 0; i < candidates.size(); i++) {
				if (candidates.get(i).score > score) {
					score = candidates.get(i).score;
					pos = i;
				}
			}
			return candidates.get(pos);
		}
	}

	private static ExecutionGraph buildMergedGraph(ExecutionGraph g1,
			ExecutionGraph g2, HashMap<Integer, Integer> mergedNodes12,
			HashMap<Integer, Integer> mergedNodes21) {
		ExecutionGraph mergedGraph = new ExecutionGraph();
		mergedGraph.nodes = new ArrayList<Node>(g1.nodes.size()
				+ g2.nodes.size() - mergedNodes12.size());
		mergedGraph.progName = g1.progName;
		// Copy nodes from G1
		for (int i = 0; i < g1.nodes.size(); i++) {
			Node n = new Node(g1.nodes.get(i));
			mergedGraph.nodes.add(n);
			// if (!mergedGraph.hash2Nodes.containsKey(n.hash)) {
			// mergedGraph.hash2Nodes.put(n.hash, )
			// }
			// if (mergedNodes12.containsKey(n.index)) {
			// n.fromWhichGraph = 0;
			// } else {
			// n.fromWhichGraph = 1;
			// }
		}
		// Copy edges from G1
		for (int i = 0; i < g1.nodes.size(); i++) {
			Node n1 = g1.nodes.get(i), n = mergedGraph.nodes.get(i);
			n.edges = new ArrayList<Edge>();
			for (int j = 0; j < n1.edges.size(); j++) {
				Edge e1 = n1.edges.get(j);
				n.edges.add(new Edge(mergedGraph.nodes.get(e1.node.index),
						e1.isDirect, e1.ordinal));
			}
		}

		// Copy nodes from G2
		HashMap<Integer, Integer> nodesFromG2 = new HashMap<Integer, Integer>();
		for (int i = 0; i < g2.nodes.size(); i++) {
			Node n2 = g2.nodes.get(i);
			if (!mergedNodes21.containsKey(n2)) {
				Node n = new Node(n2);
				n.index = mergedGraph.nodes.size();
				mergedGraph.nodes.add(n);
				nodesFromG2.put(n2.index, n.index);
			}
		}

		if (!addEdgeFromG2(mergedGraph, g2, mergedNodes21, nodesFromG2)) {
			System.out.println("There are conflicts when merging edges!");
			return null;
		}
		return mergedGraph;
	}

	private static boolean addEdgeFromG2(ExecutionGraph mergedGraph,
			ExecutionGraph g2, HashMap<Integer, Integer> mergedNodes21,
			HashMap<Integer, Integer> nodesFromG2) {

		// Merge edges from G2
		for (int i = 0; i < g2.nodes.size(); i++) {
			Node n2_1 = g2.nodes.get(i);
			for (int j = 0; j < n2_1.edges.size(); j++) {
				Edge e = n2_1.edges.get(j);
				Node n2_2 = e.node;
				if (mergedNodes21.containsKey(n2_1.index)
						&& mergedNodes21.containsKey(n2_2.index)) {
					// Both are shared nodes, need to check if there are
					// conflicts again!
					Node n_1 = mergedGraph.nodes.get(mergedNodes21
							.get(n2_1.index)), n_2 = mergedGraph.nodes
							.get(mergedNodes21.get(n2_2.index));
					Edge sharedEdge = null;
					for (int k = 0; k < n_1.edges.size(); k++) {
						if (n_1.edges.get(k).node.index == n_2.index) {
							sharedEdge = n_1.edges.get(k);
						}
					}
					if (sharedEdge == null) {
						n_1.edges.add(new Edge(n_2, e.isDirect, e.ordinal));
					} else {
						if (sharedEdge.isDirect != e.isDirect
								|| sharedEdge.ordinal != e.ordinal) {
							System.out
									.println("There are still some conflicts!");
							return false;
						}
					}
				} else if (mergedNodes21.containsKey(n2_1.index)
						&& !mergedNodes21.containsKey(n2_2.index)) {
					// First node is a shared node
					Node n_1 = mergedGraph.nodes.get(mergedNodes21
							.get(n2_1.index)), n_2 = mergedGraph.nodes
							.get(nodesFromG2.get(n2_2.index));
					n_1.edges.add(new Edge(n_2, e.isDirect, e.ordinal));
				} else if (!mergedNodes21.containsKey(n2_1.index)
						&& mergedNodes21.containsKey(n2_2.index)) {
					// Second node is a shared node
					Node n_1 = mergedGraph.nodes.get(nodesFromG2
							.get(n2_1.index)), n_2 = mergedGraph.nodes
							.get(mergedNodes21.get(n2_2.index));
					n_1.edges.add(new Edge(n_2, e.isDirect, e.ordinal));

				} else {
					// Both are new nodes from G2
					Node n_1 = mergedGraph.nodes.get(nodesFromG2
							.get(n2_1.index)), n_2 = mergedGraph.nodes
							.get(nodesFromG2.get(n2_2.index));
					n_1.edges.add(new Edge(n_2, e.isDirect, e.ordinal));
				}
			}
		}
		return true;
	}

	/**
	 * 
	 * @param graph1
	 * @param graph2
	 * @return
	 */
	public static ExecutionGraph mergeGraph(ExecutionGraph graph1,
			ExecutionGraph graph2) {
		// Merge based on the similarity of the first node ---- sanity check!
		if (graph1.nodes.get(0).hash != graph2.nodes.get(0).hash) {
			System.out
					.println("First node not the same, so wired and I can't merge...");
			return null;
		}
		// Checkout if the first main block equals to each other
		ArrayList<Node> mainBlocks1 = graph1.hash2Nodes
				.get(ExecutionGraph.specialHash), mainBlocks2 = graph2.hash2Nodes
				.get(ExecutionGraph.specialHash);
		if (mainBlocks1.size() == 1 && mainBlocks2.size() == 1) {
			if (mainBlocks1.get(0).edges.get(0).node.hash != mainBlocks2.get(0).edges
					.get(0).node.hash) {
				// System.out.println("First block not the same, not mergeable!");
				// return null;
			}
		} else {
			System.out
					.println("Important message: more than one block to hash has the same hash!!!");
		}

		// Reset isVisited field
		for (int i = 0; i < graph2.nodes.size(); i++) {
			graph2.nodes.get(i).isVisited = false;
		}

		// Record merged nodes, a pair of index represents the corresponding
		// nodes from both graphs
		HashMap<Integer, Integer> mergedNodes12 = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> mergedNodes21 = new HashMap<Integer, Integer>();
		// mergedNodes12.put(0, 0);
		// mergedNodes21.put(0, 0);

		hasConflict = false;

		for (int i = 0; i < graph2.nodes.size() && !hasConflict; i++) {
			Node n = graph2.nodes.get(i);
			if (n.isVisited)
				continue;

			// BFS on G2
			Queue<PairEdge> bfsQueue = new LinkedList<PairEdge>();
			PairEdge pairEdge = new PairEdge(null, n, false, -1);
			bfsQueue.add(pairEdge);

			while (bfsQueue.size() > 0 && !hasConflict) {
				pairEdge = bfsQueue.remove();
				Node parentNode = pairEdge.parent, curNode = pairEdge.child, node1 = null, parentNode1 = null;
				if (curNode.isVisited) {
					continue;
				}

				// Mark as visited and put outgoing nodes into the queue
				curNode.isVisited = true;
				for (Edge e : curNode.edges) {
					if (!e.node.isVisited) {
						bfsQueue.add(new PairEdge(curNode, e.node, e.isDirect,
								e.ordinal));
					}
				}

				// This is not the first node of a graph/subgraph
				Edge curNodeEdge = new Edge(curNode, pairEdge.isDirect,
						pairEdge.ordinal);
				if (parentNode != null) {
					parentNode1 = getCorrespondingNode(graph1, graph2,
							parentNode, mergedNodes21, mergedNodes12);
					if (parentNode1 == null) {
						node1 = getCorrespondingNode(graph1, graph2, curNode,
								mergedNodes21, mergedNodes12);
					} else {
						node1 = getCorrespondingChildNode(parentNode1,
								curNodeEdge, mergedNodes12);
					}
				} else {
					node1 = getCorrespondingNode(graph1, graph2, curNode,
							mergedNodes21, mergedNodes12);
				}
				if (node1 != null) {
					// node1 and curNode are mergeable
					if (mergedNodes12.get(node1.index) == null) {
						mergedNodes12.put(node1.index, curNode.index);
						mergedNodes21.put(curNode.index, node1.index);
					} else {
						int oldIndex2 = mergedNodes12.get(node1.index);
						mergedNodes12.put(node1.index, curNode.index);
						mergedNodes21.put(curNode.index, node1.index);
						mergedNodes21.remove(oldIndex2);
						graph2.nodes.get(oldIndex2).isVisited = false;
						bfsQueue.add(new PairEdge(null, graph2.nodes
								.get(oldIndex2), false, -1));
					}

				}

				if (curNode.hash == ExecutionGraph.specialHash) {
					if (curNode.edges.get(0).node.hash != node1.edges.get(0).node.hash) {
						// System.out.println("Different main blocks!");
						// hasConflict = true;
						// break;
					}
				}

			}
		}

		if (hasConflict) {
			System.out.println("Can't merge the two graphs!!");
			outputMergedGraphInfo(graph1, graph2, mergedNodes12, mergedNodes21);
			return null;
		} else {
			System.out.println("The two graphs merge!!");
			ExecutionGraph mergedGraph = buildMergedGraph(graph1, graph2,
					mergedNodes12, mergedNodes21);
			outputMergedGraphInfo(graph1, graph2, mergedNodes12, mergedNodes21);
			return mergedGraph;
		}
	}

	private static void outputMergedGraphInfo(ExecutionGraph graph1,
			ExecutionGraph graph2, HashMap<Integer, Integer> mergedNodes12,
			HashMap<Integer, Integer> mergedNodes21) {
		System.out.println(graph1.nodes.size());
		System.out.println(graph2.nodes.size());
		if (mergedNodes12.size() != mergedNodes21.size()) {
			System.out.println("Merging error");
		}
		System.out.println("Merged nodes: " + mergedNodes12.size());
		System.out.println("Merged nodes / G1 nodes: "
				+ (float) mergedNodes12.size() / graph1.nodes.size());
		System.out.println("Merged nodes / G2 nodes: "
				+ (float) mergedNodes12.size() / graph2.nodes.size());
		System.out.println();
	}

	// Search the nearby context to check the similarity of the
	// node1 and node2
	// Depth is how deep the query should try, by default depth == 5
	// Return value: the score of the similarity, -1 means definitely
	// not the same, 0 means might be
	private final static int searchDepth = 10;

	private static int getContextSimilarity(Node node1, Node node2, int depth) {
		if (node2.hash == ExecutionGraph.specialHash
				&& node1.hash == ExecutionGraph.specialHash)
			return 9999;
		if (depth <= 0)
			return 0;

		int score = 0;
		ArrayList<Edge> edges1 = node1.edges, edges2 = node2.edges;
		// One node does not have any outgoing edges!!
		// Just think that they might be similar...
		if (edges1.size() == 0 || edges2.size() == 0) {
			if (edges1.size() == 0 && edges2.size() == 0)
				return 1;
			else
				return 0;
		}

		boolean hasDirectBranch = false;
		int res = -1;
		int directMatches = 0, indirectMatches = 0;
		int directEdgeSize1 = 0, directEdgeSize2 = 0, indirectEdgeSize1 = 0, indirectEdgeSize2 = 0;
		for (int i = 0; i < edges1.size(); i++) {
			if (edges1.get(i).isDirect)
				directEdgeSize1++;
			else
				indirectEdgeSize1++;
		}
		for (int i = 0; i < edges2.size(); i++) {
			if (edges2.get(i).isDirect)
				directEdgeSize2++;
			else
				indirectEdgeSize2++;
		}
		// if (indirectEdgeSize1 != indirectEdgeSize2 || directEdgeSize1 !=
		// directEdgeSize2) {
		// return -1;
		// }

		for (int i = 0; i < edges1.size(); i++) {
			for (int j = 0; j < edges2.size(); j++) {
				Edge e1 = edges1.get(i), e2 = edges2.get(j);
				if (e1.ordinal == e2.ordinal) {
					if ((e1.isDirect && !e2.isDirect)
							|| (!e1.isDirect && e2.isDirect))
						return -1;
					if (e1.isDirect && e2.isDirect) {
						hasDirectBranch = true;
						if (e1.node.hash != e2.node.hash) {
							return -1;
						} else {
							res = getContextSimilarity(e1.node, e2.node,
									depth - 1);
							if (res == -1) {
								return -1;
							} else {
								score += res + 1;
								directMatches++;
							}
						}
					} else {
						// Trace down
						if (e1.node.hash == e2.node.hash) {
							res = getContextSimilarity(e1.node, e2.node,
									depth - 1);
							if (res != -1) {
								score += res + 1;
								indirectMatches++;
							}
						}
					}
				}
			}
		}

		if (!hasDirectBranch && score == 0)
			return -1;
		if (indirectMatches != indirectEdgeSize1) {
			return -1;
		}
		return score;
	}

	public void dumpHashCollision() {
		System.out.println(progName + "." + pid + " -> hash collision:");
		for (long hash : hash2Nodes.keySet()) {
			ArrayList<Node> nodes = hash2Nodes.get(hash);
			if (nodes.size() > 1) {

				if (nodes.get(0).hash == 0xff) {
					// System.out.println("Stop!");
					// for (int i = 0; i < nodes.size(); i++) {
					//
					// }
				}
				System.out.println("Hash collision for "
						+ Long.toHexString(nodes.get(0).hash) + ": "
						+ nodes.size());

			}
		}
	}

	public long outputFirstMain() {
		Node n = null;
		long firstMainHash = -1;
		for (int i = 0; i < nodes.size(); i++) {
			if (nodes.get(i).hash == specialHash) {
				n = nodes.get(i);
				if (adjacentList.get(n).size() > 1) {
					System.out.println("More than one target!");
					return adjacentList.get(n).size();
				} else {
					for (Node node : adjacentList.get(n).keySet()) {
						firstMainHash = node.hash;
						// System.out.println(Long.toHexString(firstMainHash));
					}
				}
				break;
			}
		}
		return firstMainHash;
	}

	public void dumpGraph(String fileName) {
		File file = new File(fileName);
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		file = new File(fileName + ".node");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		PrintWriter pwDotFile = null, pwNodeFile = null;

		try {
			pwDotFile = new PrintWriter(fileName);
			pwNodeFile = new PrintWriter(fileName + ".node");

			for (int i = 0; i < nodes.size(); i++) {
				pwNodeFile.println(Long.toHexString(nodes.get(i).hash));
			}

			pwDotFile.println("digraph runGraph {");
			long firstMainBlock = outputFirstMain();
			pwDotFile.println("# First main block: "
					+ Long.toHexString(firstMainBlock));
			for (int i = 0; i < nodes.size(); i++) {
				// pw.println("node_" + Long.toHexString(nodes.get(i).hash));
				pwDotFile.println(i + "[label=\""
						+ Long.toHexString(nodes.get(i).hash) + "\"]");

				ArrayList<Edge> edges = nodes.get(i).edges;
				for (Edge e : edges) {
					String branchType;
					if (e.isDirect) {
						branchType = "d";
					} else {
						branchType = "i";
					}

					pwDotFile.println(i + "->" + e.node.index + "[label=\""
							+ branchType + "_" + e.ordinal + "\"]");
				}
			}

			pwDotFile.print("}");
			pwDotFile.flush();

			pwNodeFile.flush();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		if (pwDotFile != null)
			pwDotFile.close();
		if (pwNodeFile != null)
			pwNodeFile.close();

	}

	public boolean isValidGraph() {
		return this.isValidGraph;
	}

	private void readGraphLookup(ArrayList<String> lookupFiles) {
		hashLookupTable = new HashMap<Long, Node>();
		nodes = new ArrayList<Node>();
		FileInputStream fileIn = null;
		DataInputStream dataIn = null;

		for (int i = 0; i < lookupFiles.size(); i++) {
			String lookupFile = lookupFiles.get(i);
			try {
				fileIn = new FileInputStream(lookupFile);
				dataIn = new DataInputStream(fileIn);
				long tag = 0, hash = 0;
				while (true) {
					// the tag and hash here is already a big-endian value
					long tagOriginal = AnalysisUtil
							.reverseForLittleEndian(dataIn.readLong());
					tag = getTagEffectiveValue(tagOriginal);

					if (tagOriginal != tag) {
						System.out.println("Tag more than 6 bytes");
						System.out.println(Long.toHexString(tagOriginal)
								+ " : " + Long.toHexString(tag));
					}

					hash = AnalysisUtil.reverseForLittleEndian(dataIn
							.readLong());
					// Tags don't duplicate in lookup file
					if (hashLookupTable.containsKey(tag)) {
						if (hashLookupTable.get(tag).hash != hash) {
							isValidGraph = false;
							System.out
									.println(Long.toHexString(tag)
											+ " -> "
											+ Long.toHexString(hashLookupTable
													.get(tag).hash) + ":"
											+ Long.toHexString(hash) + "  "
											+ lookupFile);
						}
					}
					Node node = new Node(tag, hash);
					hashLookupTable.put(tag, node);

					nodes.add(node);
					// Important!! Don't forget to update the index
					node.index = nodes.size() - 1;

					// Add it the the hash2Nodes mapping
					if (hash2Nodes.get(hash) == null) {
						hash2Nodes.put(hash, new ArrayList<Node>());
					}
					if (!hash2Nodes.get(hash).contains(node)) {
						hash2Nodes.get(hash).add(node);
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (EOFException e) {

			} catch (IOException e) {
				e.printStackTrace();
			}
			if (dataIn != null) {
				try {
					dataIn.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void readGraph(ArrayList<String> tagFiles)
			throws NullPointerException {

		for (int i = 0; i < tagFiles.size(); i++) {
			String tagFile = tagFiles.get(i);
			File file = new File(tagFile);
			// V <= E / 2 + 1
			FileInputStream fileIn = null;
			DataInputStream dataIn = null;
			// to track how many tags does not exist in lookup file
			HashSet<Long> hashesNotInLookup = new HashSet<Long>();
			try {
				fileIn = new FileInputStream(file);
				dataIn = new DataInputStream(fileIn);
				while (true) {
					long tag1 = AnalysisUtil.reverseForLittleEndian(dataIn
							.readLong());
					int flag = getEdgeFlag(tag1);
					tag1 = getTagEffectiveValue(tag1);
					long tag2Original = AnalysisUtil
							.reverseForLittleEndian(dataIn.readLong());
					long tag2 = getTagEffectiveValue(tag2Original);
					if (tag2 != tag2Original) {
						System.out.println("Something wrong about the tag");
						// System.out.println(Long.toHexString(tag2Original) +
						// " : "
						// + Long.toHexString(tag2));
					}

					Node node1 = hashLookupTable.get(tag1), node2 = hashLookupTable
							.get(tag2);
					// if (adjacentList.get(node1) != null &&
					// adjacentList.get(node2) != null) {
					// System.out.println(Long.toHexString(node1.tag));
					// System.out.println(Long.toHexString(node2.tag));
					// }

					// double check if tag1 and tag2 exist in the lookup file
					if (node1 == null) {
						// System.out.println(Long.toHexString(tag1) +
						// " is not in lookup file");
						hashesNotInLookup.add(tag1);
					}
					if (node2 == null) {
						// System.out.println(Long.toHexString(tag2) +
						// " is not in lookup file");
						hashesNotInLookup.add(tag2);
					}
					if (node1 == null || node2 == null)
						continue;

					// also put the nodes into the adjacentList if they are not
					// stored yet
					// add node to an array, which is in their seen order in the
					// file
					if (adjacentList.get(node1) == null) {
						adjacentList.put(node1, new HashMap<Node, Integer>());
					}

					HashMap<Node, Integer> edges = adjacentList.get(node1);
					// Also update the ArrayList<Edge> of node
					if (!edges.containsKey(node2)) {
						edges.put(node2, flag);
						node1.edges.add(new Edge(node2, flag));
					} else {
						// System.out.println();
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (EOFException e) {
				// System.out.println("Finish reading the file: " + fileName);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (hashesNotInLookup.size() != 0) {
				isValidGraph = false;
				System.out.println(hashesNotInLookup.size()
						+ " tag doesn't exist in lookup file -> " + tagFile);
				// for (long l : hashesNotInLookup) {
				// System.out.println(Long.toHexString(l));
				// }
			}

			if (dataIn != null) {
				try {
					dataIn.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// since V will never change once the graph is created
		nodes.trimToSize();

	}

	// Return the highest two bytes
	public static int getEdgeFlag(long tag) {
		return new Long(tag >>> 48).intValue();
	}

	public static boolean isDirectBranch(long tag) {
		int flag = new Long(tag >>> 48).intValue();
		if (flag / 256 == 1) {
			return true;
		} else {
			return false;
		}
	}

	// get the lower 6 byte of the tag, which is a long integer
	public static long getTagEffectiveValue(long tag) {
		Long res = tag << 16 >>> 16;
		return res;
	}

	public static ArrayList<ExecutionGraph> buildGraphsFromRunDir(String dir) {
		ArrayList<ExecutionGraph> graphs = new ArrayList<ExecutionGraph>();

		File dirFile = new File(dir);
		String[] fileNames = dirFile.list();
		HashMap<Integer, ArrayList<String>> pid2LookupFiles = new HashMap<Integer, ArrayList<String>>(), pid2TagFiles = new HashMap<Integer, ArrayList<String>>();
		HashMap<Integer, String> pid2PairHashFile = new HashMap<Integer, String>(), pid2BlockHashFile = new HashMap<Integer, String>();

		for (int i = 0; i < fileNames.length; i++) {
			int pid = AnalysisUtil.getPidFromFileName(fileNames[i]);
			if (pid == 0)
				continue;
			if (pid2LookupFiles.get(pid) == null) {
				pid2LookupFiles.put(pid, new ArrayList<String>());
				pid2TagFiles.put(pid, new ArrayList<String>());
			}
			if (fileNames[i].indexOf("bb-graph-hash.") != -1) {
				pid2LookupFiles.get(pid).add(dir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf("bb-graph.") != -1) {
				pid2TagFiles.get(pid).add(dir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf("pair-hash") != -1) {
				pid2PairHashFile.put(pid, dir + "/" + fileNames[i]);
			} else if (fileNames[i].indexOf("block-hash") != -1) {
				pid2BlockHashFile.put(pid, dir + "/" + fileNames[i]);
			}
		}

		// Build the graphs
		for (int pid : pid2LookupFiles.keySet()) {

			ArrayList<String> lookupFiles = pid2LookupFiles.get(pid), tagFiles = pid2TagFiles
					.get(pid);
			if (lookupFiles.size() == 0)
				continue;
			String possibleProgName = AnalysisUtil.getProgName(lookupFiles
					.get(0));
			ExecutionGraph graph = new ExecutionGraph();
			graph.pairHashFile = pid2PairHashFile.get(pid);
			graph.blockHashFile = pid2BlockHashFile.get(pid);
			graph.pairHashes = AnalysisUtil.getSetFromPath(graph.pairHashFile);
			graph.blockHashes = AnalysisUtil
					.getSetFromPath(graph.blockHashFile);
			graph.pairHashInstances = AnalysisUtil
					.getAllHashInstanceFromPath(graph.pairHashFile);
			graph.blockHashInstances = AnalysisUtil
					.getAllHashInstanceFromPath(graph.blockHashFile);

			graph.progName = possibleProgName;
			graph.pid = pid;
			graph.readGraphLookup(lookupFiles);
			graph.readGraph(tagFiles);
			// System.out.println(graph.hashLookupTable.size());
			// System.out.println(graph.blockHashInstances.size());
			// System.out.println(graph.adjacentList.size());
			// System.out.println(graph.nodes.size());

			int sizeEdges = 0;
			for (int i = 0; i < graph.nodes.size(); i++) {
				sizeEdges += graph.nodes.get(i).edges.size();
				HashSet<Long> uniqueNodes = new HashSet<Long>();
				for (Edge e : graph.nodes.get(i).edges) {
					if (uniqueNodes.contains(e.node.tag)) {
						System.out.println("Duplicate edges!");
					} else {
						uniqueNodes.add(e.node.tag);
					}
				}

			}
			// System.out.println(sizeEdges);
			// System.out.println(graph.pairHashInstances.size());

			if (!graph.isValidGraph) {
				System.out.println("Pid " + pid + " is not a valid graph!");
			}

			// graph.dumpGraph("graph-files/" + possibleProgName + "." + pid +
			// ".dot");
			// graph.dumpHashCollision();
			graphs.add(graph);
		}

		return graphs;
	}

	public static ArrayList<ExecutionGraph> getGraphs(String dir) {
		File file = new File(dir);
		ArrayList<ExecutionGraph> graphs = new ArrayList<ExecutionGraph>();

		for (File runDir : file.listFiles()) {
			graphs.addAll(buildGraphsFromRunDir(runDir.getAbsolutePath()));
		}
		return graphs;
	}

	public static void pairComparison(String dir) {
		File file = new File(dir);
		File[] runDirs = file.listFiles();
		ExecutionGraph[] graphs = new ExecutionGraph[runDirs.length];

		int countFailed = 0, countMerged = 0;
//		ExecutionGraph bigGraph = buildGraphsFromRunDir(
//				runDirs[0].getAbsolutePath()).get(0);
		for (int i = 0; i < runDirs.length; i++) {
			for (int j = i + 1; j < runDirs.length; j++) {
				if (runDirs[i].getName().indexOf("run") == -1
						|| runDirs[j].getName().indexOf("run") == -1) {
					continue;
				}

				if (graphs[i] == null) {
					graphs[i] = buildGraphsFromRunDir(
							runDirs[i].getAbsolutePath()).get(0);
					graphs[i].dumpGraph("graph-files/" + graphs[i].progName
							+ graphs[i].pid + ".dot");
//					bigGraph = mergeGraph(bigGraph, graphs[i]);
				}
				if (graphs[j] == null) {
					graphs[j] = buildGraphsFromRunDir(
							runDirs[j].getAbsolutePath()).get(0);
					graphs[j].dumpGraph("graph-files/" + graphs[j].progName
							+ graphs[j].pid + ".dot");
//					bigGraph = mergeGraph(bigGraph, graphs[j]);
				}
				if (graphs[i].progName.equals(graphs[j].progName))
					continue;
				ExecutionGraph mergedGraph;
				if (graphs[i].nodes.size() < graphs[j].nodes.size()) {
					System.out.println("Comparison between "
							+ graphs[j].progName + graphs[j].pid + " & "
							+ graphs[i].progName + graphs[i].pid);
					mergedGraph = mergeGraph(graphs[j], graphs[i]);
				} else {
					System.out.println("Comparison between "
							+ graphs[i].progName + graphs[i].pid + " & "
							+ graphs[j].progName + graphs[j].pid);
					mergedGraph = mergeGraph(graphs[i], graphs[j]);
				}

				if (mergedGraph != null) {
					countMerged++;
				} else {
					countFailed++;
				}
			}
		}
		System.out.println("Successful Merging: " + countMerged);
		System.out.println("Failed Merging: " + countFailed);
	}

	public static void main(String[] argvs) {
		// ArrayList<ExecutionGraph> graphs = buildGraphsFromRunDir(argvs[0]);
		//
		// for (int i = 0; i < graphs.size(); i++) {
		// ExecutionGraph graph = graphs.get(i);
		// if (!graph.isValidGraph()) {
		// System.out.print("This is a wrong graph!");
		// }
		// graph.dumpGraph("graph-files/" + graph.progName + "." + graph.pid
		// + ".dot");
		// }
		// ExecutionGraph bigGraph = graphs.get(0);
		// mergeGraph(bigGraph, graphs.get(1));

		// ArrayList<ExecutionGraph> graphs = getGraphs(argvs[0]);
		// for (int i = 0; i < graphs.size(); i++) {
		// ExecutionGraph graph = graphs.get(i);
		// if (!graph.isValidGraph()) {
		// System.out.print("This is a wrong graph!");
		// }
		// graph.dumpGraph("graph-files/" + graph.progName + "." + graph.pid
		// + ".dot");
		// }
		// ExecutionGraph bigGraph = graphs.get(0);
		// mergeGraph(graphs.get(0), graphs.get(1));

		pairComparison(argvs[0]);
	}
}
