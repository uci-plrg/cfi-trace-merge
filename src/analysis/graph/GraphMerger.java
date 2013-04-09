package analysis.graph;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import analysis.graph.representation.Edge;
import analysis.graph.representation.ExecutionGraph;
import analysis.graph.representation.MatchedNodes;
import analysis.graph.representation.Node;
import analysis.graph.representation.PairNode;

public class GraphMerger {
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

	// Search the nearby context to check the similarity of the
	// node1 and node2
	// Depth is how deep the query should try, by default depth == 5
	// Return value: the score of the similarity, -1 means definitely
	// not the same, 0 means might be
	private final static int searchDepth = 10;

	private boolean hasConflict = false;

	private static int getContextSimilarity(Node node1, Node node2, int depth) {
		if (depth <= 0)
			return 0;

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

		for (int i = 0; i < edges1.size(); i++) {
			for (int j = 0; j < edges2.size(); j++) {
				Edge e1 = edges1.get(i), e2 = edges2.get(j);
				if (e1.getOrdinal() == e2.getOrdinal()) {
					if ((e1.getIsDirect() && !e2.getIsDirect())
							|| (!e1.getIsDirect() && e2.getIsDirect()))
						return -1;
					if (e1.getIsDirect() && e2.getIsDirect()) {
						hasDirectBranch = true;
						if (e1.getNode().getHash() != e2.getNode().getHash()) {
							return -1;
						} else {
							res = getContextSimilarity(e1.getNode(),
									e2.getNode(), depth - 1);
							if (res == -1) {
								return -1;
							} else {
								score += res + 1;
							}
						}
					} else {
						// Trace down
						if (e1.getNode().getHash() == e2.getNode().getHash()) {
							res = getContextSimilarity(e1.getNode(),
									e2.getNode(), depth - 1);
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

	private static Node getCorrespondingNode(ExecutionGraph graph1,
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
			if ((score = getContextSimilarity(nodes1.get(i), node2, searchDepth)) != -1) {
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

	private Node getCorrespondingChildNode(Node parentNode1,
			Edge curNodeEdge, MatchedNodes matchedNodes) {
		Node node1 = null, curNode = curNodeEdge.getNode();
		ArrayList<Node> candidates = new ArrayList<Node>();
		for (int i = 0; i < parentNode1.getEdges().size(); i++) {
			Edge e = parentNode1.getEdges().get(i);
			if (e.getOrdinal() == curNodeEdge.getOrdinal()) {
				if (e.getIsDirect() != curNodeEdge.getIsDirect()) {
					System.out.println("Different branch type happened!");
					hasConflict = true;
					break;
				} else if (e.getIsDirect()) {
					if (e.getNode().getHash() != curNode.getHash()) {
						System.out
								.println("Direct branch has different targets!");
						hasConflict = true;
						break;
					} else {
						return e.getNode();
					}
				} else {
					if (e.getNode().getHash() == curNode.getHash()) {
						int score = -1;
						if ((score = getContextSimilarity(e.getNode(), curNode,
								GraphMerger.searchDepth)) > 0) {
							if (!matchedNodes
									.containsKeyByFirstIndex(e.getNode().getIndex())) {
								e.getNode().setScore(score);
								candidates.add(e.getNode());
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
			Node n = mergedGraph.addNode(g1.getNodes().get(i).getHash());

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
				n.addEdge(new Edge(mergedGraph.getNodes().get(e1.getNode().getIndex()),
						e1.getIsDirect(), e1.getOrdinal()));
			}
		}

		// Copy nodes from G2
		HashMap<Integer, Integer> nodesFromG2 = new HashMap<Integer, Integer>();
		for (int i = 0; i < g2.getNodes().size(); i++) {
			Node n2 = g2.getNodes().get(i);
			if (!matchedNodes.containsKeyBySecondIndex(n2.getIndex())) {
				Node n = mergedGraph.addNode(n2.getHash());
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

	private static boolean addEdgeFromG2(ExecutionGraph mergedGraph,
			ExecutionGraph g2, MatchedNodes matchedNodes,
			HashMap<Integer, Integer> nodesFromG2) {

		// Merge edges from G2
		for (int i = 0; i < g2.getNodes().size(); i++) {
			Node n2_1 = g2.getNodes().get(i);
			for (int j = 0; j < n2_1.getEdges().size(); j++) {
				Edge e = n2_1.getEdges().get(j);
				Node n2_2 = e.getNode();
				if (matchedNodes.containsKeyBySecondIndex(n2_1.getIndex())
						&& matchedNodes.containsKeyBySecondIndex(n2_2.getIndex())) {
					// Both are shared nodes, need to check if there are
					// conflicts again!
					Node n_1 = mergedGraph.getNodes().get(matchedNodes
							.getBySecondIndex(n2_1.getIndex())), n_2 = mergedGraph.getNodes()
							.get(matchedNodes.getBySecondIndex(n2_2.getIndex()));
					Edge sharedEdge = null;
					for (int k = 0; k < n_1.getEdges().size(); k++) {
						if (n_1.getEdges().get(k).getNode().getIndex() == n_2.getIndex()) {
							sharedEdge = n_1.getEdges().get(k);
						}
					}
					if (sharedEdge == null) {
						n_1.getEdges().add(new Edge(n_2, e.getIsDirect(), e.getOrdinal()));
					} else {
						if (sharedEdge.getIsDirect() != e.getIsDirect()
								|| sharedEdge.getOrdinal() != e.getOrdinal()) {
							System.out
									.println("There are still some conflicts!");
							return false;
						}
					}
				} else if (matchedNodes.containsKeyBySecondIndex(n2_1.getIndex())
						&& !matchedNodes.containsKeyBySecondIndex(n2_2.getIndex())) {
					// First node is a shared node

					Node n_1 = mergedGraph.getNodes().get(matchedNodes
							.getBySecondIndex(n2_1.getIndex())), n_2 = mergedGraph.getNodes()
							.get(nodesFromG2.get(n2_2.getIndex()));
					n_1.getEdges().add(new Edge(n_2, e.getIsDirect(), e.getOrdinal()));
				} else if (!matchedNodes.containsKeyBySecondIndex(n2_1.getIndex())
						&& matchedNodes.containsKeyBySecondIndex(n2_2.getIndex())) {
					// Second node is a shared node
					Node n_1 = mergedGraph.getNodes().get(nodesFromG2
							.get(n2_1.getIndex())), n_2 = mergedGraph.getNodes()
							.get(matchedNodes.getBySecondIndex(n2_2.getIndex()));
					n_1.getEdges().add(new Edge(n_2, e.getIsDirect(), e.getOrdinal()));

				} else {
					// Both are new nodes from G2
					Node n_1 = mergedGraph.getNodes().get(nodesFromG2
							.get(n2_1.getIndex())), n_2 = mergedGraph.getNodes()
							.get(nodesFromG2.get(n2_2.getIndex()));
					n_1.getEdges().add(new Edge(n_2, e.getIsDirect(), e.getOrdinal()));
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
	public ExecutionGraph mergeGraph(ExecutionGraph graph1,
			ExecutionGraph graph2) {
		// Merge based on the similarity of the first node ---- sanity check!
		if (graph1.getNodes().get(0).getHash() != graph2.getNodes().get(0).getHash()) {
			System.out
					.println("First node not the same, so wired and I can't merge...");
			return null;
		}
		// Checkout if the first main block equals to each other
		ArrayList<Node> mainBlocks1 = graph1.getHash2Nodes()
				.get(GraphMerger.specialHash), mainBlocks2 = graph2.getHash2Nodes()
				.get(GraphMerger.specialHash);

		// if (mainBlocks1.size() == 1 && mainBlocks2.size() == 1) {
		// if (mainBlocks1.get(0).edges.get(0).node.hash !=
		// mainBlocks2.get(0).edges
		// .get(0).node.hash) {
		// // System.out.println("First block not the same, not mergeable!");
		// // return null;
		// }
		// } else {
		// System.out
		// .println("Important message: more than one block to hash has the same hash!!!");
		// }

		// Reset isVisited field
		for (int i = 0; i < graph2.getNodes().size(); i++) {
			graph2.getNodes().get(i).resetVisited();
		}

		// Record matched nodes
		MatchedNodes matchedNodes = new MatchedNodes();

		hasConflict = false;

		for (int i = 0; i < graph2.getNodes().size() && !hasConflict; i++) {
			Node n_2 = graph2.getNodes().get(i);
			if (n_2.isVisited())
				continue;

			// BFS on G2
			Queue<PairNode> matchedQueue = new ArrayDeque<PairNode>(), unmatchedQueue = new LinkedList<PairNode>();

			// For dangling point, if matched put it in matchedQueue, else just
			// marked as visited
			Node n_1 = getCorrespondingNode(graph1, graph2, n_2, matchedNodes);
			if (n_1 == null) {
				n_2.setVisited();
				continue;
			}
			PairNode pairNode = new PairNode(n_1, n_2);

			matchedQueue.add(pairNode);
			matchedNodes.addPair(n_1.getIndex(), n_2.getIndex());

			while (matchedQueue.size() > 0 || unmatchedQueue.size() > 0) {
				if (matchedQueue.size() > 0) {
					pairNode = matchedQueue.remove();
					Node n1 = pairNode.getNode1(), n2 = pairNode.getNode2();
					if (n2.isVisited())
						continue;

					for (int k = 0; k < n2.getEdges().size(); k++) {
						Edge e = n2.getEdges().get(k);
						if (e.getNode().isVisited())
							continue;

						Node childNode1 = getCorrespondingChildNode(n1, e,
								matchedNodes);
						if (childNode1 != null) {
							// Re-match
							// if
							// (matchedNodes.containsKeyByFirstIndex(childNode1.index))
							// {
							// int oldIndex2 =
							// matchedNodes.getByFirstIndex(childNode1.index);
							// matchedNodes.removeByFirstIndex(childNode1.index);
							// graph2.nodes.get(oldIndex2).isVisited = false;
							// unmatchedQueue.add(new PairNode(null,
							// graph2.nodes
							// .get(oldIndex2)));
							// }
							matchedQueue.add(new PairNode(childNode1, e.getNode()));
							// Update matched relationship
							if (!matchedNodes.hasPair(childNode1.getIndex(),
									e.getNode().getIndex())) {
								if (!matchedNodes.addPair(childNode1.getIndex(),
										e.getNode().getIndex())) {
									System.out.println("Node "
											+ childNode1.getIndex()
											+ " of G1 is already matched!");
									return null;
								}
							}
						} else {
							unmatchedQueue.add(new PairNode(null, e.getNode()));
						}
					}
					n2.setVisited();
				} else {
					// try to match unmatched nodes
					Node curNode = unmatchedQueue.remove().getNode2();
					if (curNode.isVisited())
						continue;

					Node node1 = getCorrespondingNode(graph1, graph2, curNode,
							matchedNodes);
					if (node1 != null) {
						matchedQueue.add(new PairNode(node1, curNode));
					} else {
						// Simply push unvisited neighbors to unmatchedQueue
						for (int k = 0; k < curNode.getEdges().size(); k++) {
							Edge e = curNode.getEdges().get(k);
							if (e.getNode().isVisited())
								continue;
							unmatchedQueue.add(new PairNode(null, e.getNode()));
						}
					}
					curNode.setVisited();
				}
			}
		}

		if (hasConflict) {
			System.out.println("Can't merge the two graphs!!");
			return null;
		} else {
			System.out.println("The two graphs merge!!");
			ExecutionGraph mergedGraph = buildMergedGraph(graph1, graph2,
					matchedNodes);
			return mergedGraph;
		}
	}
}
