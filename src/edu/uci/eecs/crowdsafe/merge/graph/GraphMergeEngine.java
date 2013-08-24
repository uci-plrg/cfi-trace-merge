package edu.uci.eecs.crowdsafe.merge.graph;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.CrowdSafeTraceUtil;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.exception.WrongEdgeTypeException;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.exception.MergedFailedException;
import edu.uci.eecs.crowdsafe.merge.graph.SpeculativeScoreRecord.MatchResult;
import edu.uci.eecs.crowdsafe.merge.graph.SpeculativeScoreRecord.SpeculativeScoreType;
import edu.uci.eecs.crowdsafe.merge.graph.data.MergedClusterGraph;
import edu.uci.eecs.crowdsafe.merge.graph.data.MergedNode;
import edu.uci.eecs.crowdsafe.merge.graph.debug.DebugUtils;
import edu.uci.eecs.crowdsafe.merge.graph.debug.MatchingInstance;
import edu.uci.eecs.crowdsafe.merge.graph.debug.MatchingType;
import edu.uci.eecs.crowdsafe.merge.util.AnalysisUtil;

/**
 * <p>
 * This class abstracts an object that can match two ExecutionGraph. To initialize these two graphs, pass two
 * well-constructed ExecutionGraph and call the mergeGraph() method. It will merge the two graphs and construct a new
 * merged graph, which you can get it by calling the getMergedGraph() method.
 * </p>
 * 
 * <p>
 * It has been found that both in Linux and Windows, the real entry block of code in the main module comes from an
 * indirect branch of some certain C library (linux) or system libraries (ntdll.dll in Windows). Our current approach
 * treats the indirect edges as half speculation, which in this case means all programs will match if we don't know the
 * entry block. Therefore, we assume that we will know a list of rarely changed entry block and they can be provided as
 * part of the configuration.
 * </p>
 * 
 * <p>
 * Programs in x86/linux seems to enter their main function after a very similar dynamic-loading process, at the end of
 * which there is a indirect branch which jumps to the real main blocks. In the environment of this machine, the hash
 * value of that 'final block' is 0x1d84443b9bf8a6b3. ####
 * </p>
 * 
 * <p>
 * Date: 4:21pm (PST), 06/20/2013 We are trying new approach, which will set up a threshold for the matching up of
 * speculation. Intuitively, the threshold for indirect speculation can be less than pure speculation because it indeed
 * has more information and confidence.
 * </p>
 * 
 * <p>
 * Besides, in any speculation when there is many candidates with the same, high score, the current merging just does
 * not match any of them yet.
 * </p>
 * 
 * <p>
 * To use the current matching approach for the ModuleGraph, we extends the GraphMerger to ModuleGraphMerger, and
 * overrides its mergeGraph() method. At the same time, this class contains a ModuleGraphMerger subclass which matches
 * the ModuleGraphs.
 * </p>
 * 
 */
class GraphMergeEngine {

	private final GraphMergeSession session;
	final GraphMatchEngine matcher;

	public static final long specialHash = new BigInteger("4f1f7a5c30ae8622",
			16).longValue();
	private static final long beginHash = 0x5eee92;

	protected boolean hasConflict = false;

	public GraphMergeEngine(GraphMergeSession session) {
		this.session = session;
		matcher = new GraphMatchEngine(session);
	}

	/**
	 * <pre>  // deprecating: use a session
	public ModuleGraphMerger(ModuleGraphCluster left, ModuleGraph right) {
		if (left.getGraphData().getNodeCount() > right.getGraphData()
				.getNodeCount()) {
			this.left = left;
			this.right = right;
		} else {
			this.left = right;
			this.right = left;
		}

		if (DebugUtils.debug_decision(DebugUtils.DUMP_GRAPH)) {
			GraphMergeStatistics.dumpGraph(left, DebugUtils.GRAPH_DIR
					+ left.softwareUnit.name + "_"
					+ left.getContainingGraph().dataSource.getProcessId()
					+ ".dot");
			GraphMergeStatistics.dumpGraph(
					right,
					DebugUtils.GRAPH_DIR
							+ right.softwareUnit.name
							+ "_"
							+ right.getContainingGraph().dataSource
									.getProcessId() + ".dot");
		}
	}
	 */

	public ProcessExecutionGraph getMergedGraph() {
		// return mergedGraph;// TODO: merge result graph
		return null;
	}

	protected MergedClusterGraph buildMergedGraph() {
		// Don't have to copy the nodesByHash explicitly because it will be
		// automatically added when calling addNode method, but you should
		// take care of maintaining the signature2Node by yourself, be careful!!

		// Get an empty new graph to copy nodes and edges
		MergedClusterGraph mergedGraph = new MergedClusterGraph();

		// The following Node variables with ordinal "1" mean those nodes from
		// left, "2" from right, without ordinal for nodes in the new graph
		// The naming rule is also true for Edge<Node> variables

		Map<Node, MergedNode> leftNode2MergedNode = new HashMap<Node, MergedNode>();

		// Copy nodes from left
		for (Node leftNode : session.left.cluster.getGraphData().nodesByKey
				.values()) {
			MergedNode mergedNode = mergedGraph.addNode(leftNode.getHash(),
					leftNode.getType());
			leftNode2MergedNode.put(leftNode, mergedNode);
		}

		// Copy edges from left
		// Traverse edges by outgoing edges
		for (Node leftNode : session.left.cluster.getGraphData().nodesByKey
				.values()) {
			for (Edge<? extends Node> leftEdge : leftNode.getOutgoingEdges()) {
				MergedNode mergedFromNode = mergedGraph
						.getNode(leftNode2MergedNode.get(leftNode).getKey());
				MergedNode mergedToNode = mergedGraph
						.getNode(leftNode2MergedNode.get(leftEdge.getToNode())
								.getKey());
				Edge<MergedNode> mergedEdge = new Edge<MergedNode>(
						mergedFromNode, mergedToNode, leftEdge.getEdgeType(),
						leftEdge.getOrdinal());
				mergedFromNode.addOutgoingEdge(mergedEdge);
				mergedToNode.addIncomingEdge(mergedEdge);
			}
		}

		// Copy nodes from right
		Map<Node, MergedNode> rightNode2MergedNode = new HashMap<Node, MergedNode>();
		for (Node rightNode : session.right.cluster.getGraphData().nodesByKey
				.values()) {
			if (!session.matchedNodes.containsRightKey(rightNode.getKey())) {
				MergedNode mergedNode = mergedGraph.addNode(
						rightNode.getHash(), rightNode.getType());
				rightNode2MergedNode.put(rightNode, mergedNode);
			}
		}

		// Add edges from right
		if (!addEdgesFromRight(mergedGraph, session.right.cluster,
				leftNode2MergedNode, rightNode2MergedNode)) {
			Log.log("There are conflicts when merging edges!");
			return null;
		}

		return mergedGraph;
	}

	private boolean addEdgesFromRight(MergedClusterGraph mergedGraph,
			ModuleGraphCluster right,
			Map<Node, MergedNode> leftNode2MergedNode,
			Map<Node, MergedNode> rightNode2MergedNode) {

		// Merge edges from right
		// Traverse edges in right by outgoing edges
		for (Node rightFromNode : right.getGraphData().nodesByKey.values()) {
			// New fromNode and toNode in the merged graph
			Edge<MergedNode> mergedEdge;
			for (int j = 0; j < rightFromNode.getOutgoingEdges().size(); j++) {
				mergedEdge = null;
				Edge<? extends Node> rightEdge = rightFromNode
						.getOutgoingEdges().get(j);
				Node rightToNode = rightEdge.getToNode();
				MergedNode mergedFromNode = leftNode2MergedNode
						.get(session.left.cluster.getGraphData().nodesByKey
								.get(session.matchedNodes
										.getMatchByRightKey(rightFromNode
												.getKey())));
				MergedNode mergedToNode = leftNode2MergedNode
						.get(session.left.cluster.getGraphData().nodesByKey
								.get(session.matchedNodes
										.getMatchByRightKey(rightToNode
												.getKey())));
				// rightNode2MergedNode.get(rightToNode .getKey());
				if ((mergedFromNode != null) && (mergedToNode != null)) {
					// Both are shared nodes, need to check if there are
					// conflicts again!
					Edge<MergedNode> alreadyMergedEdge = null;
					for (Edge<MergedNode> mergedFromEdge : mergedFromNode
							.getOutgoingEdges()) {
						if (mergedFromEdge.getToNode().getKey()
								.equals(mergedToNode.getKey())) {
							alreadyMergedEdge = mergedFromEdge;
							break;
						}
					}
					if (alreadyMergedEdge == null) {
						mergedEdge = new Edge<MergedNode>(mergedFromNode,
								mergedToNode, rightEdge.getEdgeType(),
								rightEdge.getOrdinal());
					} else {
						if (alreadyMergedEdge.getEdgeType() != rightEdge
								.getEdgeType()
								|| alreadyMergedEdge.getOrdinal() != rightEdge
										.getOrdinal()) {
							throw new MergedFailedException(
									"Edge from %s to %s was merged with type %s and ordinal %d, but has type %s and ordinal %d in the right graph",
									rightEdge.getFromNode(), rightEdge
											.getToNode(), alreadyMergedEdge
											.getEdgeType(), alreadyMergedEdge
											.getOrdinal(), rightEdge
											.getEdgeType(), rightEdge
											.getOrdinal());
						}
						continue;
					}
				} else if (mergedFromNode != null) {
					// First node is a shared node
					mergedToNode = rightNode2MergedNode.get(rightToNode);
					mergedEdge = new Edge<MergedNode>(mergedFromNode,
							mergedToNode, rightEdge.getEdgeType(),
							rightEdge.getOrdinal());
				} else if (mergedToNode != null) {
					// Second node is a shared node
					mergedFromNode = rightNode2MergedNode.get(rightFromNode);
					mergedEdge = new Edge<MergedNode>(mergedFromNode,
							mergedToNode, rightEdge.getEdgeType(),
							rightEdge.getOrdinal());
				} else {
					// Both are new nodes from G2
					mergedFromNode = rightNode2MergedNode.get(rightFromNode);
					mergedToNode = rightNode2MergedNode.get(rightToNode);
					mergedEdge = new Edge<MergedNode>(mergedFromNode,
							mergedToNode, rightEdge.getEdgeType(),
							rightEdge.getOrdinal());
				}
				mergedFromNode.addOutgoingEdge(mergedEdge);

				if (mergedToNode == null) {
					System.err.println(String.format(
							"Error: merged node %s cannot be found",
							rightToNode));
					continue;
				}
				mergedToNode.addIncomingEdge(mergedEdge);
			}
		}
		return true;
	}

	/**
	 * <pre>
	private Node getMainBlock(ProcessExecutionGraph graph) {
		// Checkout if the first main block equals to each other
		NodeList preMainBlocks = graph
				.getNodesByHash(ModuleGraphMerger.specialHash);
		if (preMainBlocks == null) {
			return null;
		}
		if (preMainBlocks.size() == 1) {
			Node preMainNode = preMainBlocks.get(0);
			for (int i = 0; i < preMainNode.getOutgoingEdges().size(); i++) {
				if (preMainNode.getOutgoingEdges().get(i).getEdgeType() == EdgeType.INDIRECT) {
					return preMainNode.getOutgoingEdges().get(i).getToNode();
				}
			}
		} else if (preMainBlocks.size() == 0) {
			Log.log("Important message: can't find the first main block!!!");
		} else {
			Log.log("Important message: more than one block to hash has the same hash!!!");
		}

		return null;
	}
	 */

	protected void addUnmatchedNode2Queue(Node node2, int level) {
		if (node2 == null) {
			throw new NullPointerException("There is a bug here!");
		}
		session.unmatchedQueue.add(new PairNode(null, node2, level));
	}

	/**
	 * 
	 * @param left
	 * @param right
	 * @return
	 * @throws WrongEdgeTypeException
	 */
	public MergedClusterGraph mergeGraph() throws WrongEdgeTypeException {
		// Set up the initial status before actually matching
		session.initializeMerge();

		// In the OUTPUT_SCORE debug mode, initialize the PrintWriter for this
		// merging process
		if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
			if (DebugUtils.getScorePW() != null) {
				DebugUtils.getScorePW().flush();
				DebugUtils.getScorePW().close();
			}
			String fileName = session.left.cluster.getGraphData().containingGraph.dataSource
					.getProcessName()
					+ ".score-"
					+ session.left.cluster.getGraphData().containingGraph.dataSource
							.getProcessId()
					+ "-"
					+ session.right.cluster.getGraphData().containingGraph.dataSource
							.getProcessId() + ".txt";
			DebugUtils.setScorePW(fileName);
		}

		PairNode pairNode = null;
		while ((session.matchedQueue.size() > 0
				|| session.indirectChildren.size() > 0 || session.unmatchedQueue
				.size() > 0) && !hasConflict) {
			if (session.matchedQueue.size() > 0) {
				pairNode = session.matchedQueue.remove();

				// Nodes in the matchedQueue is already matched
				Node leftNode = pairNode.getLeftNode();
				Node rightNode = pairNode.getRightNode();
				if (session.right.visitedNodes.contains(rightNode))
					continue;
				session.right.visitedNodes.add(rightNode);

				for (int k = 0; k < rightNode.getOutgoingEdges().size(); k++) {
					Edge<? extends Node> rightEdge = rightNode
							.getOutgoingEdges().get(k);
					if (session.right.visitedNodes.contains(rightEdge
							.getToNode()))
						continue;

					// Find out the next matched node
					// Prioritize direct edge and call continuation edge
					Node leftChild;
					switch (rightEdge.getEdgeType()) {
						case DIRECT:
						case CALL_CONTINUATION:
							session.graphMergingStats.tryDirectMatch();

							leftChild = matcher
									.getCorrespondingDirectChildNode(leftNode,
											rightEdge);

							if (leftChild != null) {
								session.matchedQueue.add(new PairNode(
										leftChild, rightEdge.getToNode(),
										pairNode.level + 1));

								// Update matched relationship
								if (!session.matchedNodes.hasPair(leftChild
										.getKey(), rightEdge.getToNode()
										.getKey())) {
									if (!session.matchedNodes.addPair(
											leftChild, rightEdge.getToNode(),
											session.getScore(leftChild))) {
										Log.log("In execution "
												+ session.left.getProcessId()
												+ " & "
												+ session.right.getProcessId());
										Log.log("Node "
												+ leftChild.getKey()
												+ " of the left graph is already matched!");
										Log.log("Node pair need to be matched: "
												+ leftChild.getKey()
												+ "<->"
												+ rightEdge.getToNode()
														.getKey());
										Log.log("Prematched nodes: "
												+ leftChild.getKey()
												+ "<->"
												+ session.matchedNodes
														.getMatchByLeftKey(leftChild
																.getKey()));
										Log.log(session.matchedNodes
												.getMatchByRightKey(rightEdge
														.getToNode().getKey()));
										hasConflict = true;
										break;
									}

									if (DebugUtils.debug) {
										MatchingType matchType = rightEdge
												.getEdgeType() == EdgeType.DIRECT ? MatchingType.DirectBranch
												: MatchingType.CallingContinuation;
										DebugUtils.debug_matchingTrace
												.addInstance(new MatchingInstance(
														pairNode.level,
														leftChild.getKey(),
														rightEdge.getToNode()
																.getKey(),
														matchType, rightNode
																.getKey()));
									}

									if (DebugUtils
											.debug_decision(DebugUtils.PRINT_MATCHING_HISTORY)) {
										// Print out indirect nodes that can be
										// matched
										// by direct edges. However, they might also
										// indirectly
										// decided by the heuristic
										MatchingType matchType = rightEdge
												.getEdgeType() == EdgeType.DIRECT ? MatchingType.DirectBranch
												: MatchingType.CallingContinuation;
										Log.log(matchType
												+ ": "
												+ leftChild.getKey()
												+ "<->"
												+ rightEdge.getToNode()
														.getKey() + "(by "
												+ leftNode.getKey() + "<->"
												+ rightNode.getKey() + ")");
									}

								}
							} else {
								if (DebugUtils
										.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
									DebugUtils.debug_directUnmatchedCnt++;
								}

								// Should mark that this node should never be
								// matched when
								// it is popped out of the session.unmatchedQueue
								addUnmatchedNode2Queue(rightEdge.getToNode(),
										pairNode.level + 1);
							}
							break;
						default:
							// Add the indirect node to the queue
							// to delay its matching
							if (!session.matchedNodes
									.containsRightKey(rightEdge.getToNode()
											.getKey())) {
								session.indirectChildren.add(new PairNodeEdge(
										leftNode, rightEdge, rightNode));
							}
					}
				}
			} else if (session.indirectChildren.size() != 0) {
				PairNodeEdge nodeEdgePair = session.indirectChildren.remove();
				Node leftParentNode = nodeEdgePair.getLeftParentNode(), parentNode2 = nodeEdgePair
						.getRightParentNode();
				Edge<? extends Node> rightEdge = nodeEdgePair.getRightEdge();

				Node leftChild = matcher.getCorrespondingIndirectChildNode(
						leftParentNode, rightEdge);
				if (leftChild != null) {
					session.matchedQueue.add(new PairNode(leftChild, rightEdge
							.getToNode(), pairNode.level + 1));

					// Update matched relationship
					if (!session.matchedNodes.hasPair(leftChild.getKey(),
							rightEdge.getToNode().getKey())) {
						if (!session.matchedNodes.addPair(leftChild,
								rightEdge.getToNode(),
								session.getScore(leftChild))) {
							Log.log("Node " + leftChild.getKey()
									+ " of the left graph is already matched!");
							return null;
						}

						if (DebugUtils.debug) {
							DebugUtils.debug_matchingTrace
									.addInstance(new MatchingInstance(
											pairNode.level, leftChild.getKey(),
											rightEdge.getToNode().getKey(),
											MatchingType.IndirectBranch,
											parentNode2.getKey()));
						}

						if (DebugUtils
								.debug_decision(DebugUtils.PRINT_MATCHING_HISTORY)) {
							// Print out indirect nodes that must be decided by
							// heuristic
							System.out.print("Indirect: " + leftChild.getKey()
									+ "<->" + rightEdge.getToNode().getKey()
									+ "(by " + leftParentNode.getKey() + "<->"
									+ parentNode2.getKey() + ")");
							Log.log();
						}
					}
				} else {
					if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
						DebugUtils.debug_indirectHeuristicUnmatchedCnt++;
					}

					addUnmatchedNode2Queue(rightEdge.getToNode(),
							pairNode.level + 1);
				}

			} else {
				// try to match unmatched nodes
				pairNode = session.unmatchedQueue.remove();
				Node rightNode = pairNode.getRightNode();
				if (session.right.visitedNodes.contains(rightNode))
					continue;

				Node leftNode = null;
				// For nodes that are already known not to match,
				// simply don't match them
				if (!pairNode.neverMatched) {
					leftNode = matcher.getCorrespondingNode(rightNode);
				}

				if (leftNode != null) {
					if (DebugUtils.debug) {
						DebugUtils.debug_matchingTrace
								.addInstance(new MatchingInstance(
										pairNode.level, leftNode.getKey(),
										rightNode.getKey(),
										MatchingType.PureHeuristic, null));
					}

					if (DebugUtils
							.debug_decision(DebugUtils.PRINT_MATCHING_HISTORY)) {
						// Print out indirect nodes that must be decided by
						// heuristic
						Log.log("PureHeuristic: " + leftNode.getKey() + "<->"
								+ rightNode.getKey() + "(by pure heuristic)");
					}

					session.matchedQueue.add(new PairNode(leftNode, rightNode,
							pairNode.level, true));
				} else {
					// Simply push unvisited neighbors to unmatchedQueue
					for (int k = 0; k < rightNode.getOutgoingEdges().size(); k++) {
						Edge<? extends Node> rightEdge = rightNode
								.getOutgoingEdges().get(k);
						if (session.right.visitedNodes.contains(rightEdge
								.getToNode()))
							continue;

						addUnmatchedNode2Queue(rightEdge.getToNode(),
								pairNode.level + 1);
					}
					session.right.visitedNodes.add(rightNode);
				}
			}
		}

		if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
			Log.log("All pure heuristic: " + DebugUtils.debug_pureHeuristicCnt);
			Log.log("Pure heuristic not present: "
					+ DebugUtils.debug_pureHeuristicNotPresentCnt);
			Log.log("All direct unsmatched: "
					+ DebugUtils.debug_directUnmatchedCnt);
			Log.log("All indirect heuristic: "
					+ DebugUtils.debug_indirectHeuristicCnt);
			Log.log("Indirect heuristic unmatched: "
					+ DebugUtils.debug_indirectHeuristicUnmatchedCnt);
		}

		// In the OUTPUT_SCORE debug mode, close the PrintWriter when merging
		// finishes, also print out the score statistics
		if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
			DebugUtils.getScorePW().flush();
			DebugUtils.getScorePW().close();

			// Count and print out the statistical results of each speculative
			// matching case
			session.speculativeScoreList.setHasConflict(hasConflict);
			session.speculativeScoreList.count();
			session.speculativeScoreList.showResult();
		}

		if (hasConflict) {
			Log.log("Can't merge the two graphs!!");
			session.graphMergingStats.outputMergedGraphInfo();
			return null;
		} else {
			Log.log("The two graphs merge!!");
			session.graphMergingStats.outputMergedGraphInfo();
			return buildMergedGraph();
		}
	}

	/**
	 * By cheating (knowing the normalized tags), we can evaluate how good the matching is. It considers mismatching
	 * (nodes should not be matched have been matched) and unmatching (nodes should be matched have not been matched).
	 */
	protected void evaluateMatching() {

	}
}
