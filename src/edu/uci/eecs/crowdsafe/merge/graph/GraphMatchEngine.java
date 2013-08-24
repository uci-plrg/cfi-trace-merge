package edu.uci.eecs.crowdsafe.merge.graph;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.MutableInteger;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.exception.WrongEdgeTypeException;
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

	private final GraphMergeSession session;

	GraphMatchEngine(GraphMergeSession session) {
		this.session = session;
	}

	public int getContextSimilarity(Node leftNode, Node rightNode, int depth) {
		MutableInteger potentialMaxScore = new MutableInteger(0);

		session.comparedNodes.clear();
		int score = getContextSimilarity(leftNode, rightNode, depth,
				potentialMaxScore);
		if ((float) score / potentialMaxScore.getVal() > VALID_SCORE_LIMIT) {
			return score;
		} else {
			return 0;
		}
	}

	int getContextSimilarity(Node leftNode, Node node2, int depth,
			MutableInteger potentialMaxScore) {
		if (depth <= 0)
			return 0;
		// In order to avoid cyclic graph
		if (session.comparedNodes.contains(node2)) {
			return 1;
		} else {
			session.comparedNodes.add(node2);
		}

		int score = 0;
		List<? extends Edge<? extends Node>> leftEdges = leftNode
				.getOutgoingEdges();
		List<? extends Edge<? extends Node>> rightEdges = node2
				.getOutgoingEdges();
		// At least one node has no outgoing edges!!
		if (leftEdges.size() == 0 || rightEdges.size() == 0) {
			// Just think that they might be similar...
			if (leftEdges.size() == 0 && rightEdges.size() == 0) {
				potentialMaxScore.setVal(potentialMaxScore.getVal() + 1);
				return 1;
			} else {
				return 0;
			}
		}

		// The way to compute the potentialMaxScore is to count the seen
		// possible divergence in the context.
		int maxEdgeSize = leftEdges.size() > rightEdges.size() ? leftEdges
				.size() : rightEdges.size();
		int maxOrdinal = 0;
		for (int i = 0; i < leftEdges.size(); i++) {
			if (leftEdges.get(i).getOrdinal() > maxOrdinal) {
				maxOrdinal = leftEdges.get(i).getOrdinal();
			}
		}
		for (int i = 0; i < rightEdges.size(); i++) {
			if (rightEdges.get(i).getOrdinal() > maxOrdinal) {
				maxOrdinal = rightEdges.get(i).getOrdinal();
			}
		}
		potentialMaxScore.setVal(potentialMaxScore.getVal() + maxEdgeSize
				+ maxOrdinal);

		int res = -1;
		// First consider the CallContinuation edge
		Edge<? extends Node> leftEdge;
		Edge<? extends Node> rightEdge;
		if ((rightEdge = node2.getContinuationEdge()) != null
				&& (leftEdge = leftNode.getContinuationEdge()) != null) {
			if (leftEdge.getToNode().getHash() != rightEdge.getToNode()
					.getHash()) {
				return -1;
			} else {
				// Check if leftEdge.toNode was already matched to another node; if
				// so, it should return -1 to indicate a conflict
				if (session.matchedNodes.containsLeftKey(leftEdge.getToNode()
						.getKey())
						&& !session.matchedNodes.hasPair(leftEdge.getToNode()
								.getKey(), rightEdge.getToNode().getKey())) {
					return -1;
				}
				score = getContextSimilarity(leftEdge.getToNode(),
						rightEdge.getToNode(), depth - 1, potentialMaxScore);
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
							// Check if leftEdge.toNode was already matched to another
							// node; if so, it should return -1 to indicate a
							// conflict
							if (session.matchedNodes.containsLeftKey(leftEdge
									.getToNode().getKey())
									&& !session.matchedNodes.hasPair(leftEdge
											.getToNode().getKey(), rightEdge
											.getToNode().getKey())) {
								return -1;
							}

							res = getContextSimilarity(leftEdge.getToNode(),
									rightEdge.getToNode(), depth - 1,
									potentialMaxScore);
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
							res = getContextSimilarity(leftEdge.getToNode(),
									rightEdge.getToNode(), depth - 1,
									potentialMaxScore);
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

	// In PureHeuristicsNonExistingMismatch, almost all scores are 1, currently
	// it deems score of 1 as a lack of information and does not match it

	// Why existing unfound mismatch happens? This case is wired, but it
	// happens.
	// The reason is the converging node has an immediate divergence.

	// In the new approach, we only match the pure speculation when the score
	// exceeds the PureHeuristicsSpeculationThreshold
	Node getCorrespondingNode(Node rightNode) {
		session.graphMergingStats.tryPureHeuristicMatch();
		if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
			DebugUtils.debug_pureHeuristicCnt++;
		}

		// First check if this is a node already merged
		Node.Key leftNodeKey = session.matchedNodes
				.getMatchByRightKey(rightNode.getKey());
		if (leftNodeKey != null) {
			return session.left.cluster.getGraphData().nodesByKey
					.get(leftNodeKey);
		}

		// This node is not in the left graph and is not yet merged
		NodeList leftNodes = session.left.cluster.getGraphData().nodesByHash
				.get(rightNode.getHash());
		if (leftNodes == null || leftNodes.size() == 0) {
			if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
				DebugUtils.debug_pureHeuristicNotPresentCnt++;
			}
			return null;
		}

		List<Node> leftCandidates = new ArrayList<Node>();
		for (int i = 0; i < leftNodes.size(); i++) {
			Node leftNode = leftNodes.get(i);
			int score = 0;

			if (DebugUtils.debug) {
				DebugUtils.searchDepth = PURE_SEARCH_DEPTH;
			}

			// If the score is below the threshold, we just don't think it is
			// a potential candidate
			if ((score = getContextSimilarity(leftNode, rightNode,
					PURE_SEARCH_DEPTH)) > PURE_HEURISTIC_SPECULATION_THRESHOLD) {
				// If the node is already merged, skip it
				if (!session.matchedNodes.containsLeftKey(leftNode.getKey())) {
					session.setScore(leftNode, score);
					leftCandidates.add(leftNode);
				}
			}
		}

		// Collect the matching score record for pure heuristics
		// Only in OUTPUT_SCORE debug mode
		if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
			session.graphMergingStats.collectScoreRecord(leftCandidates,
					rightNode, false);
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
				DebugUtils.getScorePW().print(
						String.format("PureHeuristic_%s:\t", r));
				for (int i = 0; i < leftCandidates.size(); i++) {
					int candidateScore = session
							.getScore(leftCandidates.get(i));
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
			session.graphMergingStats.pureHeuristicMatch();
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
	Node getCorrespondingDirectChildNode(Node leftParent,
			Edge<? extends Node> rightEdge) throws WrongEdgeTypeException {
		Node rightToNode = rightEdge.getToNode();

		// Direct edges will also have multiple possible match because of the
		// existence of code re-writing
		ArrayList<Node> candidates = new ArrayList<Node>();
		int score;

		for (int i = 0; i < leftParent.getOutgoingEdges().size(); i++) {
			Edge<? extends Node> leftEdge = leftParent.getOutgoingEdges()
					.get(i);
			if (leftEdge.getOrdinal() == rightEdge.getOrdinal()) {
				if (leftEdge.getEdgeType() == rightEdge.getEdgeType()) {
					if (leftEdge.getToNode().getHash() == rightToNode.getHash()) {
						switch (leftEdge.getEdgeType()) {
							case DIRECT:
								session.graphMergingStats.directMatch();
								break;
							case CALL_CONTINUATION:
								session.graphMergingStats
										.callContinuationMatch();
								break;
							default:
								throw new IllegalArgumentException(
										"Method expects only direct and call continuation edges!");
						}

						if ((score = getContextSimilarity(leftEdge.getToNode(),
								rightToNode, DIRECT_SEARCH_DEPTH)) > DIRECT_SPECULATION_THRESHOLD) {
							if (!session.matchedNodes.containsLeftKey(leftEdge
									.getToNode().getKey())) {
								session.setScore(leftEdge.getToNode(), score);
								candidates.add(leftEdge.getToNode());
							}
						}
					} else { // hashes differ on a matching direct edge!
						session.graphMergingStats.possibleRewrite();
					}
				} else {
					return null; // nothing good here, the edge structure differs
				}
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
			score = -1;
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
				session.graphMergingStats.collectScoreRecord(candidates,
						rightToNode, true);
			}

			// Ambiguous high score, cannot make any decision
			if (highestScoreCnt > 1) {
				return null;
			}

			session.graphMergingStats.directMatch();
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
	Node getCorrespondingIndirectChildNode(Node leftParentNode,
			Edge<? extends Node> rightEdge) throws WrongEdgeTypeException {
		if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
			DebugUtils.debug_indirectHeuristicCnt++;
		}

		session.graphMergingStats.tryIndirectMatch();

		Node rightToNode = rightEdge.getToNode();

		// First check if the current node is already matched
		if (session.matchedNodes.containsRightKey(rightToNode.getKey())) {
			Node alreadyMatched = session.left.cluster.getGraphData().nodesByKey
					.get(session.matchedNodes.getMatchByRightKey(rightToNode
							.getKey()));
			if (alreadyMatched == null)
				System.out.println("break");
			return alreadyMatched;
		}

		ArrayList<Node> leftCandidates = new ArrayList<Node>();

		for (int i = 0; i < leftParentNode.getOutgoingEdges().size(); i++) {
			Edge<? extends Node> leftParentEdge = leftParentNode
					.getOutgoingEdges().get(i);
			if (leftParentEdge.getOrdinal() == rightEdge.getOrdinal()) {
				if (leftParentEdge.getEdgeType() == rightEdge.getEdgeType()) {
					if (leftParentEdge.getToNode().getHash() == rightToNode
							.getHash()) {
						int score = -1;

						if (DebugUtils.debug) {
							DebugUtils.searchDepth = INDIRECT_SEARCH_DEPTH;
						}
						// hash -8462398108006783394 [0x8a8f85c1a851665e]
						// if (leftParentNode.getHash() == 0x2ae4fb2c244e43e2L)
						// System.out.println("check it");
						if ((score = getContextSimilarity(
								leftParentEdge.getToNode(), rightToNode,
								INDIRECT_SEARCH_DEPTH)) > INDIRECT_SPECULATION_THRESHOLD) {
							if (!session.matchedNodes
									.containsLeftKey(leftParentEdge.getToNode()
											.getKey())) {
								session.setScore(leftParentEdge.getToNode(),
										score);
								leftCandidates.add(leftParentEdge.getToNode());
							}
						} else {
							System.out.println("Really?");
						}
					}
				} else {
					return null;
					/*
					 * ExecutionNode lp = (ExecutionNode) leftParentNode; Edge<ExecutionNode> lpe =
					 * (Edge<ExecutionNode>) leftParentEdge; String msg = String.format("%s -> %s (%s -> %s)", lp,
					 * lpe.getToNode(), leftParentEdge.getEdgeType(), rightEdge.getEdgeType()); throw new
					 * WrongEdgeTypeException(msg);
					 */
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
				DebugUtils.getScorePW().print(
						String.format("Indirect_%s:\t", r));
				for (int i = 0; i < leftCandidates.size(); i++) {
					int candidateScore = session
							.getScore(leftCandidates.get(i));

					if (candidateScore > 0) {
						DebugUtils.getScorePW().print(candidateScore + "\t");
					}
				}
				DebugUtils.getScorePW().println();
			}

			int pos = 0, score = -1, highestScoreCnt = 0;
			for (int i = 0; i < leftCandidates.size(); i++) {
				int candidateScore = session.getScore(leftCandidates.get(i));
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
				session.graphMergingStats.collectScoreRecord(leftCandidates,
						rightToNode, true);
			}

			// Ambiguous high score, cannot make any decision
			if (highestScoreCnt > 1) {
				return null;
			}

			session.graphMergingStats.indirectMatch();
			return leftCandidates.get(pos);
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
}
