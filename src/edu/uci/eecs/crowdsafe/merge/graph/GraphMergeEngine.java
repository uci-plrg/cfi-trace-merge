package edu.uci.eecs.crowdsafe.merge.graph;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.exception.WrongEdgeTypeException;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.exception.MergedFailedException;
import edu.uci.eecs.crowdsafe.merge.graph.data.MergedNode;
import edu.uci.eecs.crowdsafe.merge.graph.debug.DebugUtils;

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

	private final ClusterMergeSession session;
	final GraphMatchEngine matcher;

	public static final long specialHash = new BigInteger("4f1f7a5c30ae8622", 16).longValue();
	private static final long beginHash = 0x5eee92;

	public GraphMergeEngine(ClusterMergeSession session) {
		this.session = session;
		matcher = new GraphMatchEngine(session);
	}

	protected void addUnmatchedNode2Queue(Node node2, int level) {
		if (node2 == null) {
			throw new NullPointerException("There is a bug here!");
		}
		session.matchState.enqueueUnmatch(new PairNode(null, node2, level));
	}

	public void mergeGraph() throws WrongEdgeTypeException {
		session.initializeMerge();

		try {
			findCommonSubgraphs();
		} catch (MergedFailedException e) {
			Log.log(e);
			session.hasConflict = true;
		}

		if (session.hasConflict) {
			Log.log("Can't merge the two graphs!!");
		} else {
			Log.log("The two graphs merge!!");
			buildMergedGraph();
		}

		// Count and print out the statistical results of each speculative
		// matching case
		// session.speculativeScoreList.setHasConflict(session.hasConflict);
		// session.speculativeScoreList.count();
		// session.speculativeScoreList.showResult();
	}

	private void findCommonSubgraphs() throws WrongEdgeTypeException {
		while ((session.matchState.hasMatches() || session.matchState.hasIndirectEdges() || session.matchState
				.hasUnmatches()) && !session.hasConflict) {
			if (session.matchState.hasMatches()) {
				extendMatchedPairs();
			} else if (session.matchState.hasIndirectEdges()) {
				speculateIndirectBranches();
			} else {
				exploreHeuristicMatch();
			}
		}
	}

	private void extendMatchedPairs() {
		PairNode pairNode = session.matchState.dequeueMatch();

		Node<? extends Node> leftNode = pairNode.getLeftNode();
		Node<? extends Node> rightNode = pairNode.getRightNode();
		if (session.right.visitedNodes.contains(rightNode))
			return;
		session.right.visitedNodes.add(rightNode);

		for (Edge<? extends Node> rightEdge : rightNode.getOutgoingEdges()) {
			if (session.right.visitedNodes.contains(rightEdge.getToNode()))
				continue;

			// Find out the next matched node
			// Prioritize direct edge and call continuation edge
			Node leftChild;
			switch (rightEdge.getEdgeType()) {
				case DIRECT:
				case CALL_CONTINUATION:
				case MODULE_ENTRY:
					session.statistics.tryDirectMatch();

					leftChild = matcher.getCorrespondingDirectChildNode(leftNode, rightEdge);

					if (leftChild != null) {
						if (session.matchedNodes.containsLeftKey(leftChild.getKey()))
							continue;

						session.matchState.enqueueMatch(new PairNode(leftChild, rightEdge.getToNode(),
								pairNode.level + 1));

						// Update matched relationship
						if (!session.matchedNodes.hasPair(leftChild.getKey(), rightEdge.getToNode().getKey())) {
							session.matchedNodes.addPair(leftChild, rightEdge.getToNode(), session.getScore(leftChild));

							session.debugLog.directMatch(pairNode, rightEdge, leftChild);
						}
					} else {
						if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
							DebugUtils.debug_directUnmatchedCnt++;
						}

						addUnmatchedNode2Queue(rightEdge.getToNode(), pairNode.level + 1);
					}
					break;
				default:
					// Add the indirect node to the queue
					// to delay its matching
					if (!session.matchedNodes.containsRightKey(rightEdge.getToNode().getKey())) {
						session.matchState.enqueueIndirectEdge(new PairNodeEdge(leftNode, rightEdge, rightNode,
								pairNode.level));
					}
			}
		}
	}

	private void speculateIndirectBranches() {
		PairNodeEdge nodeEdgePair = session.matchState.dequeueIndirectEdge();
		Node leftParentNode = nodeEdgePair.getLeftParentNode();
		Edge<? extends Node> rightEdge = nodeEdgePair.getRightEdge();

		Node leftChild = matcher.getCorrespondingIndirectChildNode(leftParentNode, rightEdge);
		if (leftChild != null) {
			session.matchState.enqueueMatch(new PairNode(leftChild, rightEdge.getToNode(), nodeEdgePair.level + 1));

			// Update matched relationship
			if (!session.matchedNodes.hasPair(leftChild.getKey(), rightEdge.getToNode().getKey())) {
				if (!session.matchedNodes.addPair(leftChild, rightEdge.getToNode(), session.getScore(leftChild))) {
					Log.log("Node " + leftChild.getKey() + " of the left graph is already matched!");
					return;
				}

				session.debugLog.indirectMatch(nodeEdgePair, rightEdge, leftChild);
			}
		} else {
			if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
				DebugUtils.debug_indirectHeuristicUnmatchedCnt++;
			}

			addUnmatchedNode2Queue(rightEdge.getToNode(), nodeEdgePair.level + 1);
		}
	}

	private void exploreHeuristicMatch() {
		PairNode pairNode = session.matchState.dequeueUnmatch();
		Node<? extends Node> rightNode = pairNode.getRightNode();
		if (session.right.visitedNodes.contains(rightNode))
			return;

		Node leftChild = null;
		// For nodes that are already known not to match,
		// simply don't match them
		if (!pairNode.neverMatched) {
			leftChild = matcher.matchByHashThenContext(rightNode);
		}

		if (leftChild != null) {
			session.debugLog.heuristicMatch(pairNode, leftChild);

			session.matchState.enqueueMatch(new PairNode(leftChild, rightNode, pairNode.level, true));
		} else {
			// Simply push unvisited neighbors to unmatchedQueue
			for (int k = 0; k < rightNode.getOutgoingEdges().size(); k++) {
				Edge<? extends Node> rightEdge = rightNode.getOutgoingEdges().get(k);
				if (session.right.visitedNodes.contains(rightEdge.getToNode()))
					continue;

				addUnmatchedNode2Queue(rightEdge.getToNode(), pairNode.level + 1);
			}
			session.right.visitedNodes.add(rightNode);
		}
	}

	protected void buildMergedGraph() {
		Map<Node, MergedNode> leftNode2MergedNode = new HashMap<Node, MergedNode>();

		// Copy nodes from left
		for (Node leftNode : session.left.cluster.getGraphData().nodesByKey.values()) {
			MergedNode mergedNode = session.mergedGraph.addNode(leftNode.getHash(), leftNode.getType());
			leftNode2MergedNode.put(leftNode, mergedNode);
			session.debugLog.nodeMergedFromLeft(leftNode);
		}

		// Copy edges from left
		// Traverse edges by outgoing edges
		for (Node<? extends Node> leftNode : session.left.cluster.getGraphData().nodesByKey.values()) {
			for (Edge<? extends Node> leftEdge : leftNode.getOutgoingEdges()) {
				MergedNode mergedFromNode = session.mergedGraph.getNode(leftNode2MergedNode.get(leftNode).getKey());
				MergedNode mergedToNode = session.mergedGraph.getNode(leftNode2MergedNode.get(leftEdge.getToNode())
						.getKey());
				Edge<MergedNode> mergedEdge = new Edge<MergedNode>(mergedFromNode, mergedToNode,
						leftEdge.getEdgeType(), leftEdge.getOrdinal());
				mergedFromNode.addOutgoingEdge(mergedEdge);
				mergedToNode.addIncomingEdge(mergedEdge);
				session.debugLog.edgeMergedFromLeft(leftEdge);
			}
		}

		// Copy nodes from right
		Map<Node, MergedNode> rightNode2MergedNode = new HashMap<Node, MergedNode>();
		for (Node rightNode : session.right.cluster.getGraphData().nodesByKey.values()) {
			if (!session.matchedNodes.containsRightKey(rightNode.getKey())) {
				MergedNode mergedNode = session.mergedGraph.addNode(rightNode.getHash(), rightNode.getType());
				rightNode2MergedNode.put(rightNode, mergedNode);
				session.debugLog.nodeMergedFromRight(rightNode);
			}
		}

		// Add edges from right
		if (!addEdgesFromRight(session.right.cluster, leftNode2MergedNode, rightNode2MergedNode)) {
			Log.log("There are conflicts when merging edges!");
			return;
		}
	}

	private boolean addEdgesFromRight(ModuleGraphCluster right, Map<Node, MergedNode> leftNode2MergedNode,
			Map<Node, MergedNode> rightNode2MergedNode) {

		// Merge edges from right
		// Traverse edges in right by outgoing edges
		for (Node<? extends Node> rightFromNode : right.getGraphData().nodesByKey.values()) {
			// New fromNode and toNode in the merged graph
			Edge<MergedNode> mergedEdge;
			for (Edge<? extends Node> rightEdge : rightFromNode.getOutgoingEdges()) {

				mergedEdge = null;
				Node rightToNode = rightEdge.getToNode();
				MergedNode mergedFromNode = leftNode2MergedNode.get(session.left.cluster.getGraphData().nodesByKey
						.get(session.matchedNodes.getMatchByRightKey(rightFromNode.getKey())));
				MergedNode mergedToNode = leftNode2MergedNode.get(session.left.cluster.getGraphData().nodesByKey
						.get(session.matchedNodes.getMatchByRightKey(rightToNode.getKey())));
				// rightNode2MergedNode.get(rightToNode .getKey());
				if ((mergedFromNode != null) && (mergedToNode != null)) {
					// Both are shared nodes, need to check if there are
					// conflicts again!
					Edge<MergedNode> alreadyMergedEdge = null;
					for (Edge<MergedNode> mergedFromEdge : mergedFromNode.getOutgoingEdges()) {
						if (mergedFromEdge.getToNode().getKey().equals(mergedToNode.getKey())) {
							alreadyMergedEdge = mergedFromEdge;
							break;
						}
					}
					if (alreadyMergedEdge == null) {
						mergedEdge = new Edge<MergedNode>(mergedFromNode, mergedToNode, rightEdge.getEdgeType(),
								rightEdge.getOrdinal());
					} else {
						if (alreadyMergedEdge.getEdgeType() != rightEdge.getEdgeType()
								|| alreadyMergedEdge.getOrdinal() != rightEdge.getOrdinal()) {
							throw new MergedFailedException(
									"Edge from %s to %s was merged with type %s and ordinal %d, but has type %s and ordinal %d in the right graph",
									rightEdge.getFromNode(), rightEdge.getToNode(), alreadyMergedEdge.getEdgeType(),
									alreadyMergedEdge.getOrdinal(), rightEdge.getEdgeType(), rightEdge.getOrdinal());
						}
						continue;
					}
				} else if (mergedFromNode != null) {
					// First node is a shared node
					mergedToNode = rightNode2MergedNode.get(rightToNode);
					mergedEdge = new Edge<MergedNode>(mergedFromNode, mergedToNode, rightEdge.getEdgeType(),
							rightEdge.getOrdinal());
				} else if (mergedToNode != null) {
					// Second node is a shared node
					mergedFromNode = rightNode2MergedNode.get(rightFromNode);
					mergedEdge = new Edge<MergedNode>(mergedFromNode, mergedToNode, rightEdge.getEdgeType(),
							rightEdge.getOrdinal());
				} else {
					// Both are new nodes from G2
					mergedFromNode = rightNode2MergedNode.get(rightFromNode);
					mergedToNode = rightNode2MergedNode.get(rightToNode);
					mergedEdge = new Edge<MergedNode>(mergedFromNode, mergedToNode, rightEdge.getEdgeType(),
							rightEdge.getOrdinal());
				}

				if ((mergedEdge.getEdgeType() == EdgeType.CALL_CONTINUATION)
						&& (mergedFromNode.getCallContinuation() != null))
					continue;

				mergedFromNode.addOutgoingEdge(mergedEdge);

				if (mergedToNode == null) {
					Log.log(String.format("Error: merged node %s cannot be found", rightToNode));
					continue;
				}

				mergedToNode.addIncomingEdge(mergedEdge);
			}
		}
		return true;
	}

	/**
	 * By cheating (knowing the normalized tags), we can evaluate how good the matching is. It considers mismatching
	 * (nodes should not be matched have been matched) and unmatching (nodes should be matched have not been matched).
	 */
	protected void evaluateMatching() {

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
}
