package analysis.graph;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import utils.AnalysisUtil;

import analysis.graph.debug.ContextSimilarityTrace;
import analysis.graph.debug.MatchingInstance;
import analysis.graph.debug.MatchingTrace;
import analysis.graph.debug.MatchingType;
import analysis.graph.debug.DebugUtils;
import analysis.graph.representation.Edge;
import analysis.graph.representation.EdgeType;
import analysis.graph.representation.ExecutionGraph;
import analysis.graph.representation.MatchedNodes;
import analysis.graph.representation.Node;
import analysis.graph.representation.PairNode;
import analysis.graph.representation.PairNodeEdge;

public class GraphMerger extends Thread {
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
	 */
	public static void main(String[] argvs) {
		ArrayList<String> runDirs = AnalysisUtil.getAllRunDirs(argvs[0]);
		for (int i = 0; i < runDirs.size(); i++) {
			for (int j = i + 1; j < runDirs.size(); j++) {
				ExecutionGraph graph1 = ExecutionGraph.buildGraphsFromRunDir(
						runDirs.get(i)).get(0), graph2 = ExecutionGraph
						.buildGraphsFromRunDir(runDirs.get(j)).get(0);
				if (DebugUtils.debug) {
					GraphMerger graphMerger = new GraphMerger(graph1, graph2);
					graphMerger.mergeGraph();
				} else {
					GraphMerger graphMerger = new GraphMerger(graph1, graph2);
					graphMerger.run();
				}

			}
		}

	}

	public GraphMerger() {

	}

	public GraphMerger(ExecutionGraph g1, ExecutionGraph g2) {
		this.graph1 = g1;
		this.graph2 = g2;
	}

	private ExecutionGraph graph1, graph2;
	private ExecutionGraph mergedGraph;

	public void setGraph1(ExecutionGraph graph1) {
		this.graph1 = graph1;
	}

	public void setGraph2(ExecutionGraph graph2) {
		this.graph2 = graph2;
	}

	public ExecutionGraph getMergedGraph() {
		return mergedGraph;
	}

	public void startMerging() {
		this.run();
	}

	public void startMerging(ExecutionGraph g1, ExecutionGraph g2) {
		this.graph1 = g1;
		this.graph2 = g2;
		this.run();
	}

	public static final long specialHash = new BigInteger("4f1f7a5c30ae8622",
			16).longValue();
	private static final long beginHash = 0x5eee92;

	// Search the nearby context to check the similarity of the
	// node1 and node2
	// Depth is how deep the query should try, by default depth == 5
	// Return value: the score of the similarity, -1 means definitely
	// not the same, 0 means might be
	public final static int searchDepth = 10;

	private boolean hasConflict = false;

	private int getContextSimilarity(Node node1, Node node2, int depth,
			MatchedNodes matchedNodes) {
		if (depth <= 0)
			return 0;

		// Should return immediately if the two nodes are already matched
		if (matchedNodes.hasPair(node1.getIndex(), node2.getIndex())) {
			// The idea is to take advantage of previous computation on the
			// score of that node, but this is not a well-tested idea...
			return node1.getIndex() == -1 ? 1 : node1.getIndex();
		}

		if (DebugUtils.debug) {
			if (depth == searchDepth) {
				MatchingInstance inst;
				inst = new MatchingInstance(0, node1.getIndex(),
						node2.getIndex(), MatchingType.Heuristic, -1);
				DebugUtils.debug_contextSimilarityTrace
						.addTraceAtDepth(0, inst);
			}
		}

		int score = 0;
		ArrayList<Edge> edges1 = node1.getEdges(), edges2 = node2.getEdges();
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

		// First treat call node
		Edge e1, e2;
		if ((e2 = node2.getContinuationEdge()) != null
				&& (e1 = node1.getContinuationEdge()) != null) {
			if (e1.getNode().getHash() != e2.getNode().getHash()) {
				return -1;
			} else {

				if (DebugUtils.debug) {
					MatchingInstance inst;
					int parentIdx = searchDepth;

					inst = new MatchingInstance(searchDepth - depth + 1, e1
							.getNode().getIndex(), e2.getNode().getIndex(),
							MatchingType.CallingContinuation, node2.getIndex());
					DebugUtils.debug_contextSimilarityTrace.addTraceAtDepth(
							searchDepth - depth + 1, inst);
				}

				score = getContextSimilarity(e1.getNode(), e2.getNode(),
						depth - 1, matchedNodes);
				if (score == -1)
					return -1;
			}
		}

		for (int i = 0; i < edges1.size(); i++) {
			for (int j = 0; j < edges2.size(); j++) {
				e1 = edges1.get(i);
				e2 = edges2.get(j);
				if (e1.getOrdinal() == e2.getOrdinal()) {
					if (e1.getEdgeType() != e2.getEdgeType()) {
						// Need to treat the edge type specially here
						continue;
					}
					if (e1.getEdgeType() == EdgeType.Direct
							|| e1.getEdgeType() == EdgeType.Call_Continuation) {
						hasDirectBranch = true;
						if (e1.getNode().getHash() != e2.getNode().getHash()) {
							return -1;
						} else {

							if (DebugUtils.debug) {
								MatchingInstance inst;
								int parentIdx = searchDepth;

								inst = new MatchingInstance(searchDepth - depth
										+ 1, e1.getNode().getIndex(), e2
										.getNode().getIndex(),
										MatchingType.DirectBranch,
										node2.getIndex());
								DebugUtils.debug_contextSimilarityTrace
										.addTraceAtDepth(searchDepth - depth
												+ 1, inst);
							}

							res = getContextSimilarity(e1.getNode(),
									e2.getNode(), depth - 1, matchedNodes);
							if (res == -1) {
								return -1;
							} else {
								score += res + 1;
							}
						}
					} else {
						// Trace down
						if (e1.getNode().getHash() == e2.getNode().getHash()) {

							if (DebugUtils.debug) {
								if (e1.getEdgeType() == EdgeType.Indirect) {
									MatchingInstance inst;
									int parentIdx = searchDepth;

									inst = new MatchingInstance(searchDepth
											- depth + 1, e1.getNode()
											.getIndex(), e2.getNode()
											.getIndex(),
											MatchingType.IndirectBranch,
											node2.getIndex());
									DebugUtils.debug_contextSimilarityTrace
											.addTraceAtDepth(searchDepth
													- depth + 1, inst);
								} else {
									MatchingInstance inst;
									int parentIdx = searchDepth;

									inst = new MatchingInstance(searchDepth
											- depth + 1, e1.getNode()
											.getIndex(), e2.getNode()
											.getIndex(),
											MatchingType.UnexpectedReturn,
											node2.getIndex());
									DebugUtils.debug_contextSimilarityTrace
											.addTraceAtDepth(searchDepth
													- depth + 1, inst);
								}
							}

							res = getContextSimilarity(e1.getNode(),
									e2.getNode(), depth - 1, matchedNodes);
							if (res != -1) {
								score += res + 1;
							}
						}
					}
				}
			}
		}

		if (!hasDirectBranch && score == 0)
			return -1;

		return score;
	}

	private Node getCorrespondingNode(ExecutionGraph graph1,
			ExecutionGraph graph2, Node node2, MatchedNodes matchedNodes) {
		// First check if this is a node already merged
		if (matchedNodes.getBySecondIndex(node2.getIndex()) != null) {
			return graph1.getNodes().get(
					matchedNodes.getBySecondIndex(node2.getIndex()));
		}

		// This node does not belongs to G1 and
		// is not yet added to G1
		ArrayList<Node> nodes1 = graph1.getHash2Nodes().get(node2.getHash());
		if (nodes1 == null || nodes1.size() == 0)
			return null;

		ArrayList<Node> candidates = new ArrayList<Node>();
		for (int i = 0; i < nodes1.size(); i++) {
			int score = 0;
			if ((score = getContextSimilarity(nodes1.get(i), node2,
					searchDepth, matchedNodes)) != -1) {
				// If the node is already merged, skip it
				if (!matchedNodes.containsKeyByFirstIndex(nodes1.get(i)
						.getIndex())) {
					nodes1.get(i).setScore(score);
					candidates.add(nodes1.get(i));
				}
			}
		}
		if (candidates.size() > 1) {
			// Returns the candidate with highest score
			int pos = 0, score = 0;
			for (int i = 0; i < candidates.size(); i++) {
				if (candidates.get(i).getScore() > score) {
					pos = i;
					score = candidates.get(i).getScore();
				}
			}
			// If the highest score is 0, we can't believe
			// that they are the same node
			Node mostSimilarNode = candidates.get(pos);
			if (mostSimilarNode.getScore() > 0) {
				return mostSimilarNode;
			} else {
				return null;
			}
		} else if (candidates.size() == 1) {
			// If the highest score is 0, we can't believe
			// that they are the same node
			Node mostSimilarNode = candidates.get(0);
			if (mostSimilarNode.getScore() > 0) {
				return mostSimilarNode;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	/**
	 * Search for corresponding direct child node, including direct edge and
	 * call continuation edges
	 * 
	 * @param parentNode1
	 * @param curNodeEdge
	 * @param matchedNodes
	 * @return The node that matched; Null if no matched node found
	 */
	private Node getCorrespondingDirectChildNode(Node parentNode1,
			Edge curNodeEdge, MatchedNodes matchedNodes) {
		Node curNode = curNodeEdge.getNode();

		for (int i = 0; i < parentNode1.getEdges().size(); i++) {
			Edge e = parentNode1.getEdges().get(i);
			if (e.getOrdinal() == curNodeEdge.getOrdinal()) {
				if (e.getEdgeType() != curNodeEdge.getEdgeType()) {
					continue;
				} else {
					if (e.getNode().getHash() != curNode.getHash()) {
						if (e.getEdgeType() == EdgeType.Direct) {
							System.out
									.println("Direct branch has different targets!");
						} else {
							System.out
									.println("Call continuation has different targets!");
						}

						if (DebugUtils.debugDecision(DebugUtils.MERGE_ERROR)) {
							System.out.println("Direct edge conflict: "
									+ e.getNode().getIndex() + "<->"
									+ curNode.getIndex() + "(By "
									+ parentNode1.getIndex() + "<->Unknown)");
						}

						hasConflict = true;
						break;
					} else {
						return e.getNode();
					}
				}
			}
		}
		return null;
	}

	/**
	 * Search for corresponding indirect child node, including indirect edge and
	 * unexpected return edges
	 * 
	 * @param parentNode1
	 * @param curNodeEdge
	 * @param matchedNodes
	 * @return The node that matched; Null if no matched node found
	 */
	private Node getCorrespondingIndirectChildNode(ExecutionGraph graph1,
			Node parentNode1, Edge curNodeEdge, MatchedNodes matchedNodes) {
		Node curNode = curNodeEdge.getNode();

		// First check if the current node is already matched
		if (matchedNodes.containsKeyBySecondIndex(curNode.getIndex())) {
			return graph1.getNodes().get(
					matchedNodes.getBySecondIndex(curNode.getIndex()));
		}

		ArrayList<Node> candidates = new ArrayList<Node>();
		for (int i = 0; i < parentNode1.getEdges().size(); i++) {
			Edge e = parentNode1.getEdges().get(i);
			if (e.getOrdinal() == curNodeEdge.getOrdinal()) {
				if (e.getEdgeType() != curNodeEdge.getEdgeType()) {
					continue;
				} else if (e.getNode().getHash() == curNode.getHash()) {
					int score = -1;
					if ((score = getContextSimilarity(e.getNode(), curNode,
							GraphMerger.searchDepth, matchedNodes)) > 0) {
						if (!matchedNodes.containsKeyByFirstIndex(e.getNode()
								.getIndex())) {
							e.getNode().setScore(score);
							candidates.add(e.getNode());
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
				if (candidates.get(i).getScore() > score) {
					score = candidates.get(i).getScore();
					pos = i;
				}
			}
			return candidates.get(pos);
		}
	}

	private ExecutionGraph buildMergedGraph(ExecutionGraph g1,
			ExecutionGraph g2, MatchedNodes matchedNodes) {
		ExecutionGraph mergedGraph = new ExecutionGraph();
		mergedGraph.setProgName(g1.getProgName());
		// Copy nodes from G1
		for (int i = 0; i < g1.getNodes().size(); i++) {
			Node n1 = g1.getNodes().get(i);
			Node n = mergedGraph.addNode(n1.getHash(), n1.getMetaNodeType());

			if (matchedNodes.containsKeyByFirstIndex(n.getIndex())) {
				n.setFromWhichGraph(0);
			} else {
				n.setFromWhichGraph(1);
			}
		}

		// Copy edges from G1
		for (int i = 0; i < g1.getNodes().size(); i++) {
			Node n1 = g1.getNodes().get(i), n = mergedGraph.getNodes().get(i);
			for (int j = 0; j < n1.getEdges().size(); j++) {
				Edge e1 = n1.getEdges().get(j);
				n.addEdge(new Edge(mergedGraph.getNodes().get(
						e1.getNode().getIndex()), e1.getEdgeType(), e1
						.getOrdinal()));
			}
		}

		// Copy nodes from G2
		HashMap<Integer, Integer> nodesFromG2 = new HashMap<Integer, Integer>();
		for (int i = 0; i < g2.getNodes().size(); i++) {
			Node n2 = g2.getNodes().get(i);
			if (!matchedNodes.containsKeyBySecondIndex(n2.getIndex())) {
				Node n = mergedGraph
						.addNode(n2.getHash(), n2.getMetaNodeType());
				nodesFromG2.put(n2.getIndex(), n.getIndex());
			}
		}

		// Update block hashes and pair hashes
		mergedGraph.addBlockHash(g1);
		mergedGraph.addBlockHash(g2);
		mergedGraph.addPairHash(g1);
		mergedGraph.addPairHash(g2);

		if (!addEdgeFromG2(mergedGraph, g2, matchedNodes, nodesFromG2)) {
			System.out.println("There are conflicts when merging edges!");
			return null;
		}
		return mergedGraph;
	}

	private boolean addEdgeFromG2(ExecutionGraph mergedGraph,
			ExecutionGraph g2, MatchedNodes matchedNodes,
			HashMap<Integer, Integer> nodesFromG2) {

		// Merge edges from G2
		for (int i = 0; i < g2.getNodes().size(); i++) {
			Node n2_1 = g2.getNodes().get(i);
			for (int j = 0; j < n2_1.getEdges().size(); j++) {
				Edge e = n2_1.getEdges().get(j);
				Node n2_2 = e.getNode();
				if (matchedNodes.containsKeyBySecondIndex(n2_1.getIndex())
						&& matchedNodes.containsKeyBySecondIndex(n2_2
								.getIndex())) {
					// Both are shared nodes, need to check if there are
					// conflicts again!
					Node n_1 = mergedGraph.getNodes().get(
							matchedNodes.getBySecondIndex(n2_1.getIndex())), n_2 = mergedGraph
							.getNodes().get(
									matchedNodes.getBySecondIndex(n2_2
											.getIndex()));
					Edge sharedEdge = null;
					for (int k = 0; k < n_1.getEdges().size(); k++) {
						if (n_1.getEdges().get(k).getNode().getIndex() == n_2
								.getIndex()) {
							sharedEdge = n_1.getEdges().get(k);
						}
					}
					if (sharedEdge == null) {
						n_1.getEdges().add(
								new Edge(n_2, e.getEdgeType(), e.getOrdinal()));
					} else {
						if (sharedEdge.getEdgeType() != e.getEdgeType()
								|| sharedEdge.getOrdinal() != e.getOrdinal()) {
							System.out
									.println("There are still some conflicts!");
							return false;
						}
					}
				} else if (matchedNodes.containsKeyBySecondIndex(n2_1
						.getIndex())
						&& !matchedNodes.containsKeyBySecondIndex(n2_2
								.getIndex())) {
					// First node is a shared node

					Node n_1 = mergedGraph.getNodes().get(
							matchedNodes.getBySecondIndex(n2_1.getIndex())), n_2 = mergedGraph
							.getNodes().get(nodesFromG2.get(n2_2.getIndex()));
					n_1.getEdges().add(
							new Edge(n_2, e.getEdgeType(), e.getOrdinal()));
				} else if (!matchedNodes.containsKeyBySecondIndex(n2_1
						.getIndex())
						&& matchedNodes.containsKeyBySecondIndex(n2_2
								.getIndex())) {
					// Second node is a shared node
					Node n_1 = mergedGraph.getNodes().get(
							nodesFromG2.get(n2_1.getIndex())), n_2 = mergedGraph
							.getNodes().get(
									matchedNodes.getBySecondIndex(n2_2
											.getIndex()));
					n_1.getEdges().add(
							new Edge(n_2, e.getEdgeType(), e.getOrdinal()));

				} else {
					// Both are new nodes from G2
					Node n_1 = mergedGraph.getNodes().get(
							nodesFromG2.get(n2_1.getIndex())), n_2 = mergedGraph
							.getNodes().get(nodesFromG2.get(n2_2.getIndex()));
					n_1.getEdges().add(
							new Edge(n_2, e.getEdgeType(), e.getOrdinal()));
				}
			}
		}
		return true;
	}

	private Node getMainBlock(ExecutionGraph graph) {
		// Checkout if the first main block equals to each other
		ArrayList<Node> preMainBlocks = graph.getHash2Nodes().get(
				GraphMerger.specialHash);

		if (preMainBlocks.size() == 1) {
			Node preMainNode = preMainBlocks.get(0);
			for (int i = 0; i < preMainNode.getEdges().size(); i++) {
				if (preMainNode.getEdges().get(i).getEdgeType() == EdgeType.Indirect) {
					return preMainNode.getEdges().get(i).getNode();
				}
			}
		} else if (preMainBlocks.size() == 0) {
			System.out
					.println("Important message: can't find the first main block!!!");
		} else {
			System.out
					.println("Important message: more than one block to hash has the same hash!!!");
		}

		return null;
	}

	public ExecutionGraph mergeGraph() {
		GraphMergingInfo.dumpGraph(graph1,
				"graph-files/" + graph1.getProgName() + graph1.getPid()
						+ ".dot");
		GraphMergingInfo.dumpGraph(graph2,
				"graph-files/" + graph2.getProgName() + graph2.getPid()
						+ ".dot");
		return mergeGraph(graph1, graph2);
	}

	/**
	 * 
	 * @param graph1
	 * @param graph2
	 * @return
	 */
	public ExecutionGraph mergeGraph(ExecutionGraph graph1,
			ExecutionGraph graph2) {

		// Merge based on the similarity of the first node ---- sanity check!
		if (graph1.getNodes().get(0).getHash() != graph2.getNodes().get(0)
				.getHash()) {
			System.out
					.println("First node not the same, so wired and I can't merge...");
			return null;
		}

		// Reset isVisited field
		for (int i = 0; i < graph2.getNodes().size(); i++) {
			graph2.getNodes().get(i).resetVisited();
		}

		// Record matched nodes
		MatchedNodes matchedNodes = new MatchedNodes();

		hasConflict = false;
		Node n_1 = graph1.getNodes().get(0), n_2 = graph2.getNodes().get(0);

		// BFS on G2
		Queue<PairNode> matchedQueue = new LinkedList<PairNode>(), unmatchedQueue = new LinkedList<PairNode>();
		PairNode pairNode = new PairNode(n_1, n_2, 0);

		matchedQueue.add(pairNode);
		matchedNodes.addPair(n_1.getIndex(), n_2.getIndex());

		if (DebugUtils.debugDecision(DebugUtils.MAIN_KNOWN_ADD_MAIN)) {
			Node mainNode1 = getMainBlock(graph1), mainNode2 = getMainBlock(graph2);
			if (mainNode1 != null && mainNode2 != null) {
				matchedNodes
						.addPair(mainNode1.getIndex(), mainNode2.getIndex());
				matchedQueue.add(new PairNode(mainNode1, mainNode2, 0));

				DebugUtils.debug_matchingTrace
						.addInstance(new MatchingInstance(0, mainNode1
								.getIndex(), mainNode2.getIndex(),
								MatchingType.Heuristic, -1));

			}
		}

		// This is a queue to record all the unvisited indirect node...
		Queue<PairNodeEdge> indirectChildren = new LinkedList<PairNodeEdge>();

		if (DebugUtils.debug) {
			DebugUtils.debug_matchingTrace
					.addInstance(new MatchingInstance(0, n_1.getIndex(), n_2
							.getIndex(), MatchingType.Heuristic, -1));
		}

		while ((matchedQueue.size() > 0 || indirectChildren.size() > 0 || unmatchedQueue
				.size() > 0) && !hasConflict) {
			if (matchedQueue.size() > 0) {
				pairNode = matchedQueue.remove();

				// Nodes in the matchedQueue is already matched
				Node n1 = pairNode.getNode1(), n2 = pairNode.getNode2();
				if (n2.isVisited())
					continue;
				n2.setVisited();

				if (DebugUtils.debug) {
					if (n2.getIndex() == 853) {
						DebugUtils.stopHere();
					}
				}

				for (int k = 0; k < n2.getEdges().size(); k++) {
					Edge e = n2.getEdges().get(k);
					if (e.getNode().isVisited())
						continue;

					// Find out the next matched node
					// Prioritize direct edge and call continuation edge
					Node childNode1;
					if (e.getEdgeType() == EdgeType.Direct
							|| e.getEdgeType() == EdgeType.Call_Continuation) {
						childNode1 = getCorrespondingDirectChildNode(n1, e,
								matchedNodes);

						if (childNode1 != null) {
							matchedQueue.add(new PairNode(childNode1, e
									.getNode(), pairNode.level + 1));

							// Update matched relationship
							if (!matchedNodes.hasPair(childNode1.getIndex(), e
									.getNode().getIndex())) {
								if (!matchedNodes.addPair(
										childNode1.getIndex(), e.getNode()
												.getIndex())) {
									System.out.println("In execution "
											+ graph1.getPid() + " & "
											+ graph2.getPid());
									System.out.println("Node "
											+ childNode1.getIndex()
											+ " of G1 is already matched!");
									System.out
											.println("Node pair need to be matched: "
													+ childNode1.getIndex()
													+ "<->"
													+ e.getNode().getIndex());
									System.out.println("Prematched nodes: "
											+ childNode1.getIndex()
											+ "<->"
											+ matchedNodes
													.getByFirstIndex(childNode1
															.getIndex()));
									return null;
								}

								if (DebugUtils.debug) {
									DebugUtils.debug_matchingTrace
											.addInstance(new MatchingInstance(
													pairNode.level, childNode1
															.getIndex(), e
															.getNode()
															.getIndex(),
													MatchingType.DirectBranch,
													n2.getIndex()));
								}

								if (DebugUtils
										.debugDecision(DebugUtils.PRINT_MATCHING_HISTORY)) {
									// Print out indirect nodes that can be
									// matched
									// by direct edges. However, they might also
									// indirectly
									// decided by the heuristic
									System.out.println("Direct: "
											+ childNode1.getIndex() + "<->"
											+ e.getNode().getIndex() + "(by "
											+ n1.getIndex() + "<->"
											+ n2.getIndex() + ")");
								}

							}
						} else {
							unmatchedQueue.add(new PairNode(null, e.getNode(),
									pairNode.level + 1));
						}
					} else {
						// Add the indirect node to the queue
						// to delay its matching
						if (!matchedNodes.containsKeyBySecondIndex(e.getNode()
								.getIndex())) {
							indirectChildren.add(new PairNodeEdge(n1, e, n2));
						}
					}
				}
			} else if (indirectChildren.size() != 0) {
				PairNodeEdge nodeEdgePair = indirectChildren.remove();
				Node parentNode1 = nodeEdgePair.getParentNode1(), parentNode2 = nodeEdgePair
						.getParentNode2();
				Edge e = nodeEdgePair.getCurNodeEdge();

				Node childNode1 = getCorrespondingIndirectChildNode(graph1,
						parentNode1, e, matchedNodes);

				if (childNode1 != null) {
					matchedQueue.add(new PairNode(childNode1, e.getNode(),
							pairNode.level + 1));

					// Update matched relationship
					if (!matchedNodes.hasPair(childNode1.getIndex(), e
							.getNode().getIndex())) {
						if (!matchedNodes.addPair(childNode1.getIndex(), e
								.getNode().getIndex())) {
							System.out.println("Node " + childNode1.getIndex()
									+ " of G1 is already matched!");
							return null;
						}

						if (DebugUtils.debug) {
							DebugUtils.debug_matchingTrace
									.addInstance(new MatchingInstance(
											pairNode.level, childNode1
													.getIndex(), e.getNode()
													.getIndex(),
											MatchingType.IndirectBranch,
											parentNode2.getIndex()));
						}

						if (DebugUtils
								.debugDecision(DebugUtils.PRINT_MATCHING_HISTORY)) {
							// Print out indirect nodes that must be decided by
							// heuristic
							System.out.println("Indirect: "
									+ childNode1.getIndex() + "<->"
									+ e.getNode().getIndex() + "(by "
									+ parentNode1.getIndex() + "<->"
									+ parentNode2.getIndex() + ")");
						}

					}
				} else {
					unmatchedQueue.add(new PairNode(null, e.getNode(),
							pairNode.level + 1));
				}

			} else {
				// try to match unmatched nodes
				pairNode = unmatchedQueue.remove();
				Node curNode = pairNode.getNode2();
				if (curNode.isVisited())
					continue;

				Node node1 = getCorrespondingNode(graph1, graph2, curNode,
						matchedNodes);
				if (node1 != null) {

					if (DebugUtils.debug) {
						DebugUtils.debug_matchingTrace
								.addInstance(new MatchingInstance(
										pairNode.level, node1.getIndex(),
										curNode.getIndex(),
										MatchingType.PureHeuristic, -1));
					}

					if (DebugUtils
							.debugDecision(DebugUtils.PRINT_MATCHING_HISTORY)) {
						// Print out indirect nodes that must be decided by
						// heuristic
						System.out.println("PureHeuristic: " + node1.getIndex()
								+ "<->" + curNode.getIndex()
								+ "(by pure heuristic");
					}

					matchedQueue.add(new PairNode(node1, curNode,
							pairNode.level));
				} else {
					// Simply push unvisited neighbors to unmatchedQueue
					for (int k = 0; k < curNode.getEdges().size(); k++) {
						Edge e = curNode.getEdges().get(k);
						if (e.getNode().isVisited())
							continue;
						unmatchedQueue.add(new PairNode(null, e.getNode(),
								pairNode.level + 1));
					}
				}
				curNode.setVisited();
			}
		}
		// }

		if (hasConflict) {
			System.out.println("Can't merge the two graphs!!");
			GraphMergingInfo mergingInfo = new GraphMergingInfo(graph1, graph2,
					matchedNodes);
			mergingInfo.outputMergedGraphInfo();
			return null;
		} else {
			// System.out.println("The two graphs merge!!");
			ExecutionGraph mergedGraph = buildMergedGraph(graph1, graph2,
					matchedNodes);

			GraphMergingInfo mergingInfo = new GraphMergingInfo(graph1, graph2,
					matchedNodes);
			mergingInfo.outputMergedGraphInfo();
			return mergedGraph;
		}
	}

	public void run() {
		if (graph1 == null || graph2 == null)
			return;
		if (DebugUtils.debug) {
			GraphMergingInfo.dumpGraph(graph1,
					"graph-files/" + graph1.getProgName() + graph1.getPid()
							+ ".dot");
			GraphMergingInfo.dumpGraph(graph2,
					"graph-files/" + graph2.getProgName() + graph2.getPid()
							+ ".dot");
		}
		if (graph1.getNodes().size() < graph2.getNodes().size()) {
			mergedGraph = mergeGraph(graph1, graph2);
		} else {
			mergedGraph = mergeGraph(graph2, graph1);
		}

	}
}
