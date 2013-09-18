package edu.uci.eecs.crowdsafe.merge.graph;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.exception.WrongEdgeTypeException;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.graph.ContextMatchRecord.EdgeMatchType;
import edu.uci.eecs.crowdsafe.merge.graph.debug.DebugUtils;

public class GraphMatchEngine {

	// The static threshold for indirect speculation and pure heuristics
	// These two values are completely hypothetic and need further verification
	private static final int INDIRECT_SPECULATION_THRESHOLD = 0;
	private static final int DIRECT_SPECULATION_THRESHOLD = 0;
	private static final int PURE_HEURISTIC_SPECULATION_THRESHOLD = 0;

	// Search the nearby context to check the similarity of the
	// node1 and node2
	// Depth is how deep the query should try, by default depth == 5
	// Return value: the score of the similarity, -1 means definitely
	// not the same, 0 means might be
	public final static int DIRECT_SEARCH_DEPTH = 10;
	public final static int INDIRECT_SEARCH_DEPTH = 10;
	public final static int PURE_SEARCH_DEPTH = 15;

	private static final float VALID_SCORE_LIMIT = 0.5f;

	private final ClusterMergeSession session;

	GraphMatchEngine(ClusterMergeSession session) {
		this.session = session;
	}

	public void getContextSimilarity(Node<? extends Node> leftNode, Node<? extends Node> rightNode, int depth) {
		// Check if either node was already matched to another node; if so, fail
		if ((session.matchedNodes.containsLeftKey(leftNode.getKey()) || session.matchedNodes.containsRightKey(rightNode
				.getKey())) && !session.matchedNodes.hasPair(leftNode.getKey(), rightNode.getKey())) {
			session.contextRecord.fail();
			return;
		}

		if (depth <= 0)
			return;

		// In order to avoid cyclic graph
		if (session.contextRecord.isAlreadyCompared(rightNode))
			return;

		session.contextRecord.addComparedNode(rightNode);

		if (!leftNode.hasCompatibleEdges(rightNode)) {
			session.contextRecord.fail();
			return;
		}

		// At least one node has no outgoing edges!!
		if (!leftNode.hasOutgoingEdges() || !rightNode.hasOutgoingEdges()) {
			// Just think that they might be similar...
			for (Edge<? extends Node> leftEdge : leftNode.getOutgoingEdges()) {
				session.contextRecord.addEdge(depth, EdgeMatchType.ONE_SIDE_ONLY);
			}
			for (Edge<? extends Node> rightEdge : rightNode.getOutgoingEdges()) {
				session.contextRecord.addEdge(depth, EdgeMatchType.ONE_SIDE_ONLY);
			}
		}

		{
			// First consider the CallContinuation edge
			Edge<? extends Node> leftEdge;
			Edge<? extends Node> rightEdge;
			if ((rightEdge = rightNode.getCallContinuation()) != null
					&& (leftEdge = leftNode.getCallContinuation()) != null) {
				if (leftEdge.getToNode().getHash() != rightEdge.getToNode().getHash()) {
					session.contextRecord.fail();
					return;
				}
				getContextSimilarity(leftEdge.getToNode(), rightEdge.getToNode(), depth - 1);
				if (session.contextRecord.isFailed())
					return;
			}
		}

		int minOrdinal = Math.min(leftNode.getOutgoingOrdinalCount(), rightNode.getOutgoingOrdinalCount());
		for (int ordinal = 0; ordinal < minOrdinal; ordinal++) {
			List<? extends Edge<? extends Node>> leftEdges = leftNode.getOutgoingEdges(ordinal);
			List<? extends Edge<? extends Node>> rightEdges = rightNode.getOutgoingEdges(ordinal);
			if (leftEdges.isEmpty() || rightEdges.isEmpty()) {
				if (!(leftEdges.isEmpty() && rightEdges.isEmpty()))
					session.contextRecord.addEdge(depth, EdgeMatchType.ONE_SIDE_ONLY);
				continue;
			}
			EdgeType type = leftEdges.get(0).getEdgeType();
			switch (type) {
				case DIRECT:
					int matchCount = 0;
					for (Edge<? extends Node> leftEdge : leftEdges) {
						for (Edge<? extends Node> rightEdge : rightEdges) {
							if (leftEdge.getToNode().getHash() != rightEdge.getToNode().getHash()) {
								continue;
							}
							// Check if leftEdge.toNode was already matched to another node; if so, fail
							if (session.matchedNodes.containsLeftKey(leftEdge.getToNode().getKey())
									&& !session.matchedNodes.hasPair(leftEdge.getToNode().getKey(), rightEdge
											.getToNode().getKey())) {
								matchCount++;
								continue;
							}

							getContextSimilarity(leftEdge.getToNode(), rightEdge.getToNode(), depth - 1);
							if (!session.contextRecord.isFailed())
								matchCount++;
						}
					}
					if (matchCount == Math.min(leftEdges.size(), rightEdges.size())) {
						session.contextRecord.addEdge(depth, EdgeMatchType.DIRECT_MATCH);
					} else if ((leftEdges.size() > 1) || (rightEdges.size() > 1)) {
						session.contextRecord.addEdge(depth, EdgeMatchType.DIRECT_MATCH);
					} else {
						session.contextRecord.fail();
						return;
					}
					break;
				case INDIRECT:
					for (Edge<? extends Node> leftEdge : leftEdges) {
						for (Edge<? extends Node> rightEdge : rightEdges) {
							// TODO: what about existing PairNodes?

							// Either indirect or unexpected edges, keep tracing
							// down. If the pair of node does not match, that does
							// not mean the context is different.
							if (leftEdge.getToNode().getHash() == rightEdge.getToNode().getHash()) {
								session.contextRecord.saveState();
								getContextSimilarity(leftEdge.getToNode(), rightEdge.getToNode(), depth - 1);
								// In the case of res == -1, just leave it alone
								// because of lack of information
								if (session.contextRecord.isFailed()) {
									session.contextRecord.rewindState();
								} else {
									session.contextRecord.commitState();
									session.contextRecord.addEdge(depth, EdgeMatchType.INDIRECT_UNIQUE_MATCH);
									// TODO: ambiguous indirect matches
								}
							}
						}
					}
					break;
			}
		}
	}

	// In PureHeuristicsNonExistingMismatch, almost all scores are 1, currently
	// it deems score of 1 as a lack of information and does not match it

	// Why existing unfound mismatch happens? This case is wired, but it
	// happens.
	// The reason is the converging node has an immediate divergence.

	// In the new approach, we only match the pure speculation when the score
	// exceeds the PureHeuristicsSpeculationThreshold
	Node matchByHashThenContext(Node rightNode) {
		session.statistics.tryPureHeuristicMatch();
		if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
			DebugUtils.debug_pureHeuristicCnt++;
		}

		// First check if this is a node already merged
		Node.Key leftNodeKey = session.matchedNodes.getMatchByRightKey(rightNode.getKey());
		if (leftNodeKey != null) {
			return session.left.cluster.getGraphData().nodesByKey.get(leftNodeKey);
		}

		// This node is not in the left graph and is not yet merged
		NodeList leftNodes = session.left.cluster.getGraphData().nodesByHash.get(rightNode.getHash());
		if (leftNodes == null || leftNodes.size() == 0) {
			if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
				DebugUtils.debug_pureHeuristicNotPresentCnt++;
			}
			return null;
		}

		List<Node> leftCandidates = new ArrayList<Node>();
		for (int i = 0; i < leftNodes.size(); i++) {
			Node leftNode = leftNodes.get(i);

			if (leftNode.getType() == MetaNodeType.CLUSTER_EXIT)
				continue;

			if (leftNode.isModuleRelativeEquivalent(rightNode)) {
				if (session.matchedNodes.containsLeftKey(leftNode.getKey()))
					continue; // doh!
				return leftNode;
			}

			if (!leftNode.getModule().isEquivalent(rightNode.getModule()))
				continue;

			// If the node is already merged, skip it
			if (session.matchedNodes.containsLeftKey(leftNode.getKey()))
				continue;

			// If the score is below the threshold, we just don't think it is
			// a potential candidate
			session.contextRecord.reset(leftNode, rightNode);
			getContextSimilarity(leftNode, rightNode, PURE_SEARCH_DEPTH);
			if (session.acceptContext(leftNode)) {
				if (leftNode.isModuleRelativeMismatch(rightNode))
					Log.log("Mismatch candidate %s accepted for %s with score %d", leftNode, rightNode,
							session.getScore(leftNode));
				leftCandidates.add(leftNode);
			}
		}

		// Collect the matching score record for pure heuristics
		// Only in OUTPUT_SCORE debug mode
		if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
			session.statistics.collectScoreRecord(leftCandidates, rightNode, false);
		}

		if (leftCandidates.size() > 1) {
			// Returns the candidate with highest score
			int pos = 0, score = 0, highestScoreCnt = 0;
			for (int i = 0; i < leftCandidates.size(); i++) {
				int candidateScore = session.getScore(leftCandidates.get(i));
				if (candidateScore > score) {
					pos = i;
					score = candidateScore;
					highestScoreCnt = 1;
				} else if (candidateScore == score) {
					highestScoreCnt++;
				}
			}

			// In the OUTPUT_SCORE debug mode, output the completely speculative
			// matching score to a file
			if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
				ExecutionNode r = (ExecutionNode) rightNode;
				DebugUtils.getScorePW().print(String.format("PureHeuristic_%s:\t", r));
				for (int i = 0; i < leftCandidates.size(); i++) {
					int candidateScore = session.getScore(leftCandidates.get(i));
					if (candidateScore > 0) {
						DebugUtils.getScorePW().print(candidateScore + "\t");
					}
				}
				DebugUtils.getScorePW().println();
			}

			// Ambiguous high score, cannot make any decision
			if (highestScoreCnt > 1) {
				return null;
			}
			Node mostSimilarNode = leftCandidates.get(pos);
			return mostSimilarNode;
		} else if (leftCandidates.size() == 1) {
			Node mostSimilarNode = leftCandidates.get(0);
			session.statistics.pureHeuristicMatch();
			return mostSimilarNode;
		} else {
			return null;
		}
	}

	/**
	 * <p>
	 * Search for corresponding direct child node, including direct edge and call continuation edges
	 * </p>
	 * <p>
	 * For direct edges with the same ordinal, we just treat them as indirect edges because they might've already been
	 * re-written.
	 * </p>
	 * 
	 * @param leftParent
	 * @param rightEdge
	 * @return The node that matched; Null if no matched node found
	 * @throws WrongEdgeTypeException
	 */
	Node getCorrespondingDirectChildNode(Node<? extends Node> leftParent, Edge<? extends Node> rightEdge)
			throws WrongEdgeTypeException {
		Node rightToNode = rightEdge.getToNode();

		session.debugLog.debugCheck(rightToNode);

		if (rightEdge.getEdgeType() == EdgeType.CALL_CONTINUATION) {
			Edge<? extends Node> callContinuation = leftParent.getCallContinuation();
			if (callContinuation != null) {
				if (callContinuation.getToNode().getHash() == rightEdge.getToNode().getHash()) {
					return callContinuation.getToNode();
				}
			}
		}

		List<? extends Edge<? extends Node>> leftEdges = leftParent.getOutgoingEdges(rightEdge.getOrdinal());

		if (leftEdges.size() == 1) {
			Node leftChild = leftEdges.get(0).getToNode();
			if ((leftChild.getHash() == rightToNode.getHash()) && leftChild.hasCompatibleEdges(rightEdge.getToNode())) {
				return leftChild;
			} else {
				return null;
			}
		}

		// Direct edges will also have multiple possible match because of the
		// existence of code re-writing
		ArrayList<Node> candidates = new ArrayList<Node>();

		for (Edge<? extends Node> leftEdge : leftEdges) {
			if (leftEdge.getEdgeType() == rightEdge.getEdgeType()) {
				if (leftEdge.getToNode().getHash() == rightToNode.getHash()) {

					if (session.matchedNodes.containsLeftKey(leftEdge.getToNode().getKey()))
						continue;
					// && leftEdge.getEdgeType() != EdgeType.MODULE_ENTRY)
					// return leftEdge.getToNode();

					switch (leftEdge.getEdgeType()) {
						case DIRECT:
						case CLUSTER_ENTRY:
							session.statistics.directMatch();
							break;
						case CALL_CONTINUATION:
							session.statistics.callContinuationMatch();
							break;
						default:
							throw new IllegalArgumentException(
									"Method only expects edges of type direct, module entry and call continuation!");
					}

					session.contextRecord.reset(leftEdge.getToNode(), rightEdge.getToNode());
					getContextSimilarity(leftEdge.getToNode(), rightEdge.getToNode(), DIRECT_SEARCH_DEPTH);
					if (session.acceptContext(leftEdge.getToNode())) {
						candidates.add(leftEdge.getToNode());
					}
				} else if (rightEdge.getEdgeType() != EdgeType.CLUSTER_ENTRY) {
					// hashes differ on a matching direct edge!
					session.statistics.possibleRewrite();
				}
			} else {
				return null; // nothing good here, the edge structure differs
			}
		}

		if (candidates.size() == 0) {
			return null;
		} else {
			// In the OUTPUT_SCORE debug mode, output the speculative
			// matching score of indirect edges to a file
			if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
				ExecutionNode r = (ExecutionNode) rightToNode;
				DebugUtils.getScorePW().print(String.format("Direct_%s:\t", r));
				for (int i = 0; i < candidates.size(); i++) {
					int candidateScore = session.getScore(candidates.get(i));
					if (candidateScore > 0) {
						DebugUtils.getScorePW().print(candidateScore + "\t");
					}

				}
				DebugUtils.getScorePW().println();
			}

			int pos = 0, highestScoreCnt = 0;
			int score = -1;
			for (int i = 0; i < candidates.size(); i++) {
				int candidateScore = session.getScore(candidates.get(i));
				if (candidateScore > score) {
					score = candidateScore;
					pos = i;
					highestScoreCnt = 1;
				} else if (candidateScore == score) {
					highestScoreCnt++;
				}
			}

			// Collect the matching score record for indirect speculation
			// Only in OUTPUT_SCORE debug mode
			if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
				session.statistics.collectScoreRecord(candidates, rightToNode, true);
			}

			// Ambiguous high score, cannot make any decision
			if (highestScoreCnt > 1) {
				return null;
			}

			session.statistics.directMatch();
			return candidates.get(pos);
		}
	}

	/**
	 * Search for corresponding indirect child node, including indirect edge and unexpected return edges
	 * 
	 * @param leftParentNode
	 * @param rightEdge
	 * @return The node that matched; Null if no matched node found
	 * @throws WrongEdgeTypeException
	 */

	// In the new approach, we only match the pure speculation when the score
	// exceeds the IndirectSpeculationThreshold
	Node getCorrespondingIndirectChildNode(Node<? extends Node<?>> leftParentNode, Edge<? extends Node> rightEdge)
			throws WrongEdgeTypeException {
		if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
			DebugUtils.debug_indirectHeuristicCnt++;
		}

		session.statistics.tryIndirectMatch();

		Node rightToNode = rightEdge.getToNode();

		// First check if the current node is already matched
		if (session.matchedNodes.containsRightKey(rightToNode.getKey())) {
			Node alreadyMatched = session.left.cluster.getGraphData().nodesByKey.get(session.matchedNodes
					.getMatchByRightKey(rightToNode.getKey()));
			return alreadyMatched;
		}

		List<Node> leftCandidates = new ArrayList<Node>();
		for (Edge<? extends Node> leftParentEdge : leftParentNode.getOutgoingEdges(rightEdge.getOrdinal())) {
			Node leftChild = leftParentEdge.getToNode();
			if (leftChild.isModuleRelativeEquivalent(rightToNode)) {
				if (session.matchedNodes.containsLeftKey(leftChild.getKey()))
					continue; // doh!
				return leftChild;
			}
			if ((leftChild.getHash() == rightToNode.getHash())
					&& leftChild.getModule().isEquivalent(rightToNode.getModule())) {
				if (session.matchedNodes.containsLeftKey(leftChild.getKey()))
					continue;

				session.contextRecord.reset(leftChild, rightToNode);
				getContextSimilarity(leftChild, rightToNode, INDIRECT_SEARCH_DEPTH);
				if (session.acceptContext(leftChild)) {
					if (leftChild.isModuleRelativeMismatch(rightToNode))
						Log.log("Mismatch candidate %s accepted for %s with score %d", leftChild, rightToNode,
								session.getScore(leftChild));
					leftCandidates.add(leftChild);
				}
			}
		}

		if (leftCandidates.size() == 0) {
			return null;
		} else {
			// In the OUTPUT_SCORE debug mode, output the speculative
			// matching score of indirect edges to a file
			if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
				ExecutionNode r = (ExecutionNode) rightToNode;
				DebugUtils.getScorePW().print(String.format("Indirect_%s:\t", r));
				for (int i = 0; i < leftCandidates.size(); i++) {
					int candidateScore = session.getScore(leftCandidates.get(i));

					if (candidateScore > 0) {
						DebugUtils.getScorePW().print(candidateScore + "\t");
					}
				}
				DebugUtils.getScorePW().println();
			}

			List<Node> topCandidates = new ArrayList<Node>();
			int pos = 0, score = -1;
			for (Node candidate : leftCandidates) {
				int candidateScore = session.getScore(candidate);
				if (candidateScore > score) {
					score = candidateScore;
					topCandidates.clear();
					topCandidates.add(candidate);
				} else if (candidateScore == score) {
					topCandidates.add(candidate);
				}
			}

			// Collect the matching score record for indirect speculation
			// Only in OUTPUT_SCORE debug mode
			if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
				session.statistics.collectScoreRecord(leftCandidates, rightToNode, true);
			}

			// Ambiguous match, multiple ndoes have the same high score
			if (topCandidates.size() > 1) {
				// Bail if any incoming edge has an unmatched source node
				for (Node<? extends Node> candidate : topCandidates) {
					for (Edge<? extends Node> incoming : candidate.getIncomingEdges()) {
						if (!session.matchedNodes.containsLeftKey(incoming.getFromNode().getKey()))
							return null;
					}
				}
				// All incoming edges are from matches, so randomly pick a match
				while (topCandidates.size() > 1)
					topCandidates.remove(topCandidates.size() - 1);
			}

			session.statistics.indirectMatch();
			return topCandidates.get(0);
		}
	}
	/**
	 * This is used only for debugging to analyze the aliasing problem. Don't ever use it to merge the graph!!!
	 * 
	 * @param leftNode
	 * @param rightNode
	 * @param depth
	 * @return
	 */
	/**
	 * <pre> Why not just use a graph iterator?
	public int debug_getContextSimilarity(ExecutionNode leftNode,
			ExecutionNode rightNode, int depth) {
		if (depth <= 0)
			return 0;

		if (session.comparedNodes.contains(rightNode)) {
			return 1;
		} else {
			session.comparedNodes.add(rightNode);
		}
		Node trueLeftNode = session.left.cluster.getGraphData()
				.HACK_relativeTagLookup(rightNode);
		if (leftNode.equals(trueLeftNode)) {
			return 1;
		}

		int score = 0;
		List<Edge<ExecutionNode>> leftEdges = leftNode.getOutgoingEdges();
		List<Edge<ExecutionNode>> rightEdges = rightNode.getOutgoingEdges();
		// At least one node has no outgoing edges!!
		if (leftEdges.size() == 0 || rightEdges.size() == 0) {
			// Just think that they might be similar...
			if (leftEdges.size() == 0 && rightEdges.size() == 0) {
				return 1;
			} else {
				return 0;
			}
		}

		int res = -1;
		// First consider the CallContinuation edge
		Edge<ExecutionNode> leftEdge, rightEdge;
		if ((rightEdge = rightNode.getContinuationEdge()) != null
				&& (leftEdge = leftNode.getContinuationEdge()) != null) {
			if (leftEdge.getToNode().getHash() != rightEdge.getToNode()
					.getHash()) {
				return -1;
			} else {
				// Check if e1.toNode was already matched to another node; if
				// so, it should return -1 to indicate a conflict
				if (session.matchedNodes.containsLeftKey(leftEdge.getToNode()
						.getKey())
						&& !session.matchedNodes.hasPair(leftEdge.getToNode()
								.getKey(), rightEdge.getToNode().getKey())) {
					return -1;
				}
				score = debug_getContextSimilarity(leftEdge.getToNode(),
						rightEdge.getToNode(), depth - 1);
				if (score == -1)
					return -1;
			}
		}

		for (int i = 0; i < leftEdges.size(); i++) {
			for (int j = 0; j < rightEdges.size(); j++) {
				leftEdge = leftEdges.get(i);
				rightEdge = rightEdges.get(j);
				if (leftEdge.getOrdinal() == rightEdge.getOrdinal()) {
					if (leftEdge.getEdgeType() != rightEdge.getEdgeType()) {
						// Need to treat the edge type specially here
						// because the ordinal of CallContinuation and
						// DirectEdge usually have the same ordinal 0
						continue;
					}
					// This case was considered previously
					if (leftEdge.getEdgeType() == EdgeType.CALL_CONTINUATION) {
						continue;
					}
					if (leftEdge.getEdgeType() == EdgeType.DIRECT) {
						if (leftEdge.getToNode().getHash() != rightEdge
								.getToNode().getHash()) {
							return -1;
						} else {
							// Check if e1.toNode was already matched to another
							// node; if so, it should return -1 to indicate a
							// conflict
							if (session.matchedNodes.containsLeftKey(leftEdge
									.getToNode().getKey())
									&& !session.matchedNodes.hasPair(leftEdge
											.getToNode().getKey(), rightEdge
											.getToNode().getKey())) {
								return -1;
							}

							res = debug_getContextSimilarity(
									leftEdge.getToNode(),
									rightEdge.getToNode(), depth - 1);
							if (res == -1) {
								return -1;
							} else {
								score += res + 1;
							}
						}
					} else {
						// Either indirect or unexpected edges, keep tracing
						// down. If the pair of node does not match, that does
						// not mean the context is different.
						if (leftEdge.getToNode().getHash() == rightEdge
								.getToNode().getHash()) {
							res = debug_getContextSimilarity(
									leftEdge.getToNode(),
									rightEdge.getToNode(), depth - 1);
							// In the case of res == -1, just leave it alone
							// because of lack of information
							if (res != -1) {
								score += res + 1;
							}
						}
					}
				}
			}
		}
		return score;
	}
	 */
}
