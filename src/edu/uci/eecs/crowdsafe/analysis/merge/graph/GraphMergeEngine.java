package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.analysis.data.graph.Edge;
import edu.uci.eecs.crowdsafe.analysis.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.Node;
import edu.uci.eecs.crowdsafe.analysis.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.analysis.data.graph.merged.MergedClusterGraph;
import edu.uci.eecs.crowdsafe.analysis.data.graph.merged.MergedNode;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.WrongEdgeTypeException;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.SpeculativeScoreRecord.MatchResult;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.SpeculativeScoreRecord.SpeculativeScoreType;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.DebugUtils;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.MatchingInstance;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.MatchingType;
import edu.uci.eecs.crowdsafe.analysis.util.AnalysisUtil;
import edu.uci.eecs.crowdsafe.util.MutableInteger;
import edu.uci.eecs.crowdsafe.util.log.Log;

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
public class GraphMergeEngine {

	// The static threshold for indirect speculation and pure heuristics
	// These two values are completely hypothetic and need further verification
	private static final int INDIRECT_SPECULATION_THRESHOLD = 10;
	private static final int DIRECT_SPECULATION_THRESHOLD = 0;
	private static final int PURE_HEURISTIC_SPECULATION_THRESHOLD = 15;

	private static final float VALID_SCORE_LIMIT = 0.5f;

	private final GraphMergeSession session;

	public GraphMergeEngine(GraphMergeSession session) {
		this.session = session;
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

	public static final long specialHash = new BigInteger("4f1f7a5c30ae8622",
			16).longValue();
	private static final long beginHash = 0x5eee92;

	// Search the nearby context to check the similarity of the
	// node1 and node2
	// Depth is how deep the query should try, by default depth == 5
	// Return value: the score of the similarity, -1 means definitely
	// not the same, 0 means might be
	public final static int directSearchDepth = 10;
	public final static int indirectSearchDepth = 10;
	public final static int pureSearchDepth = 15;

	protected boolean hasConflict = false;

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

	private int getContextSimilarity(Node leftNode, Node node2, int depth,
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
	private Node getCorrespondingNode(Node rightNode) {
		session.graphMergingInfo.tryPureHeuristicMatch();
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
				DebugUtils.searchDepth = GraphMergeEngine.pureSearchDepth;
			}

			// If the score is below the threshold, we just don't think it is
			// a potential candidate
			if ((score = getContextSimilarity(leftNode, rightNode,
					pureSearchDepth)) > PURE_HEURISTIC_SPECULATION_THRESHOLD) {
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
			collectScoreRecord(leftCandidates, rightNode, false);
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
						"PureHeuristic_" + r.getModule().unit.name + "_0x"
								+ Long.toHexString(r.getRelativeTag()) + "_0x"
								+ Long.toHexString(r.getTag()) + ":\t");
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
			session.graphMergingInfo.pureHeuristicMatch();
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
	private Node getCorrespondingDirectChildNode(Node leftParent,
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
				if (leftEdge.getEdgeType() != rightEdge.getEdgeType()) {
					// Call continuation and conditional jump usually have the
					// same ordinal 0
					continue;
				} else {
					if (leftEdge.getToNode().getHash() != rightToNode.getHash()) {
						if (leftEdge.getEdgeType() == EdgeType.DIRECT) {
							Log.log("Direct branch has different targets, "
									+ "but it may be caused by code re-writing!");
							if (leftParent.getContinuationEdge() != null) {
								Log.log("This is likely to be a function call!");
							} else {
								Log.log("This is likely to be a conditional jump!");
							}
						} else {
							Log.log("Call continuation has different targets, "
									+ "but it may be caused by code re-writing!");
						}
						// No enough information anymore, just keep going
						// But we will pretend nothing happens now
						// continue;
						Log.log("Code re-written!");
						hasConflict = true;
					} else {
						if (leftEdge.getEdgeType() == EdgeType.DIRECT) {
							session.graphMergingInfo.directMatch();
						} else {
							session.graphMergingInfo.callContinuationMatch();
						}

						if ((score = getContextSimilarity(leftEdge.getToNode(),
								rightToNode, GraphMergeEngine.directSearchDepth)) > DIRECT_SPECULATION_THRESHOLD) {
							if (!session.matchedNodes.containsLeftKey(leftEdge
									.getToNode().getKey())) {
								session.setScore(leftEdge.getToNode(), score);
								candidates.add(leftEdge.getToNode());
							}
						}
						// Just return the node now
						return leftEdge.getToNode();
					}
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
				String moduleName = r.getModule().unit.name;
				long relativeTag = r.getRelativeTag();
				DebugUtils.getScorePW().print(
						"Direct_" + moduleName + "_0x"
								+ Long.toHexString(relativeTag) + "_0x"
								+ Long.toHexString(r.getTag()) + "v"
								+ r.getTagVersion() + ":\t");
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
				collectScoreRecord(candidates, rightToNode, true);
			}

			// Ambiguous high score, cannot make any decision
			if (highestScoreCnt > 1) {
				return null;
			}

			session.graphMergingInfo.directMatch();
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
	private Node getCorrespondingIndirectChildNode(Node leftParentNode,
			Edge<? extends Node> rightEdge) throws WrongEdgeTypeException {
		if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
			DebugUtils.debug_indirectHeuristicCnt++;
		}

		session.graphMergingInfo.tryIndirectMatch();

		Node rightToNode = rightEdge.getToNode();

		// First check if the current node is already matched
		if (session.matchedNodes.containsRightKey(rightToNode.getKey())) {
			return session.left.cluster.getGraphData().nodesByKey
					.get(session.matchedNodes.getMatchByRightKey(rightToNode
							.getKey()));
		}

		ArrayList<Node> leftCandidates = new ArrayList<Node>();

		for (int i = 0; i < leftParentNode.getOutgoingEdges().size(); i++) {
			Edge<? extends Node> leftParentEdge = leftParentNode
					.getOutgoingEdges().get(i);
			if (leftParentEdge.getOrdinal() == rightEdge.getOrdinal()) {
				if (leftParentEdge.getEdgeType() != rightEdge.getEdgeType()) {
					if (DebugUtils.ThrowWrongEdgeType) {
						ExecutionNode lp = (ExecutionNode) leftParentNode;
						Edge<ExecutionNode> lpe = (Edge<ExecutionNode>) leftParentEdge;
						String msg = Long.toHexString(lp.getTag()) + "->"
								+ Long.toHexString(lpe.getToNode().getTag())
								+ "(" + leftParentEdge.getEdgeType() + ", "
								+ rightEdge.getEdgeType() + ")";
						throw new WrongEdgeTypeException(msg);
					}
					continue;
				} else if (leftParentEdge.getToNode().getHash() == rightToNode
						.getHash()) {
					int score = -1;

					if (DebugUtils.debug) {
						DebugUtils.searchDepth = GraphMergeEngine.indirectSearchDepth;
					}
					if ((score = getContextSimilarity(
							leftParentEdge.getToNode(), rightToNode,
							GraphMergeEngine.indirectSearchDepth)) > INDIRECT_SPECULATION_THRESHOLD) {
						if (!session.matchedNodes
								.containsLeftKey(leftParentEdge.getToNode()
										.getKey())) {
							session.setScore(leftParentEdge.getToNode(), score);
							leftCandidates.add(leftParentEdge.getToNode());
						}
					}
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
				String moduleName = r.getModule().unit.name;
				long relativeTag = r.getRelativeTag();
				DebugUtils.getScorePW().print(
						"Indirect_" + moduleName + "_0x"
								+ Long.toHexString(relativeTag) + "_0x"
								+ Long.toHexString(r.getTag()) + ":\t");
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
				collectScoreRecord(leftCandidates, rightToNode, true);
			}

			// Ambiguous high score, cannot make any decision
			if (highestScoreCnt > 1) {
				return null;
			}

			session.graphMergingInfo.indirectMatch();
			return leftCandidates.get(pos);
		}
	}

	private void collectScoreRecord(List<Node> leftCandidates, Node rightNode,
			boolean isIndirect) {
		int maxScore = -1, maxScoreCnt = 0;
		Node maxNode = null;
		for (int i = 0; i < leftCandidates.size(); i++) {
			int candidateScore = session.getScore(leftCandidates.get(i));
			if (candidateScore > maxScore && candidateScore != 0) {
				maxNode = leftCandidates.get(i);
				maxScore = candidateScore;
				maxScoreCnt = 1;
			} else if (candidateScore == maxScore) {
				maxScoreCnt++;
			}
		}
		if (maxScoreCnt > 1) {
			maxNode = null;
		}

		MatchResult matchResult = AnalysisUtil.getMatchResult(
				session.left.cluster, session.right.cluster, maxNode,
				(ExecutionNode) rightNode, isIndirect);
		Node trueLeftNode = session.left.cluster.getGraphData()
				.HACK_relativeTagLookup((ExecutionNode) rightNode);
		if (maxNode == null) {
			if (matchResult == MatchResult.IndirectExistingUnfoundMismatch
					|| matchResult == MatchResult.PureHeuristicsExistingUnfoundMismatch) {
				session.speculativeScoreList.add(new SpeculativeScoreRecord(
						SpeculativeScoreType.NoMatch, isIndirect, -1,
						trueLeftNode, rightNode, null, matchResult));
			} else {
				session.speculativeScoreList.add(new SpeculativeScoreRecord(
						SpeculativeScoreType.NoMatch, isIndirect, -1, null,
						rightNode, null, matchResult));
			}
			return;
		}

		// Count the different cases for different
		// speculative matching cases
		boolean allLowScore = true;
		if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
			for (int i = 0; i < leftCandidates.size(); i++) {
				int candidateScore = session.getScore(leftCandidates.get(i));
				if (candidateScore >= SpeculativeScoreRecord.LowScore) {
					allLowScore = false;
				}
			}

			if (allLowScore) {
				// Need to figure out what leads the low score
				// Easier to find if it is the tail case
				if (AnalysisUtil.isTailNode(rightNode)) {
					session.speculativeScoreList
							.add(new SpeculativeScoreRecord(
									SpeculativeScoreType.LowScoreTail,
									isIndirect, maxScore, trueLeftNode,
									rightNode, maxNode, matchResult));
				} else {
					session.speculativeScoreList
							.add(new SpeculativeScoreRecord(
									SpeculativeScoreType.LowScoreDivergence,
									isIndirect, maxScore, trueLeftNode,
									rightNode, maxNode, matchResult));
				}
			} else {
				if (leftCandidates.size() == 1) {
					if (((ExecutionNode) rightNode).getRelativeTag() == ((ExecutionNode) maxNode)
							.getRelativeTag()) {
						session.speculativeScoreList
								.add(new SpeculativeScoreRecord(
										SpeculativeScoreType.OneMatchTrue,
										isIndirect, maxScore, trueLeftNode,
										rightNode, maxNode, matchResult));
					} else {
						session.speculativeScoreList
								.add(new SpeculativeScoreRecord(
										SpeculativeScoreType.OneMatchFalse,
										isIndirect, maxScore, trueLeftNode,
										rightNode, maxNode, matchResult));
					}
				} else {
					if (maxScoreCnt <= 1) {
						session.speculativeScoreList
								.add(new SpeculativeScoreRecord(
										SpeculativeScoreType.ManyMatchesCorrect,
										isIndirect, maxScore, trueLeftNode,
										rightNode, maxNode, matchResult));
					} else {
						session.speculativeScoreList
								.add(new SpeculativeScoreRecord(
										SpeculativeScoreType.ManyMatchesAmbiguity,
										isIndirect, maxScore, trueLeftNode,
										rightNode, maxNode, matchResult));
					}
				}
			}
		}
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
			Edge<MergedNode> mergedEdge = null;

			for (int j = 0; j < rightFromNode.getOutgoingEdges().size(); j++) {
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
					Edge<MergedNode> sharedEdge = null;
					for (Edge<MergedNode> mergedFromEdge : mergedFromNode
							.getOutgoingEdges()) {
						if (mergedFromEdge.getToNode().getKey()
								.equals(mergedToNode.getKey())) {
							sharedEdge = mergedFromEdge;
							break;
						}
					}
					if (sharedEdge == null) {
						mergedEdge = new Edge<MergedNode>(mergedFromNode,
								mergedToNode, rightEdge.getEdgeType(),
								rightEdge.getOrdinal());
					} else {
						if (sharedEdge.getEdgeType() != rightEdge.getEdgeType()
								|| sharedEdge.getOrdinal() != rightEdge
										.getOrdinal()) {
							System.err
									.println(String
											.format("Edge from 0x%x to 0x%x was merged with type %s and ordinal %d, but has type %s and ordinal %d in the right graph",
													((ExecutionNode) rightEdge
															.getFromNode())
															.getTag(),
													((ExecutionNode) rightEdge
															.getToNode())
															.getTag(),
													sharedEdge.getEdgeType(),
													sharedEdge.getOrdinal(),
													rightEdge.getEdgeType(),
													rightEdge.getOrdinal()));
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
					System.err
							.println(String
									.format("Error: merged node with tag 0x%x-v%d cannot be found",
											((ExecutionNode) rightToNode)
													.getTag(),
											((ExecutionNode) rightToNode)
													.getTagVersion()));
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
		if (!session.initializeMerge()) {
			return null;
		}

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
					if ((rightEdge.getEdgeType() == EdgeType.DIRECT || rightEdge
							.getEdgeType() == EdgeType.CALL_CONTINUATION)) {
						session.graphMergingInfo.tryDirectMatch();

						leftChild = getCorrespondingDirectChildNode(leftNode,
								rightEdge);

						if (leftChild != null) {
							session.matchedQueue.add(new PairNode(leftChild,
									rightEdge.getToNode(), pairNode.level + 1));

							// Update matched relationship
							if (!session.matchedNodes.hasPair(leftChild
									.getKey(), rightEdge.getToNode().getKey())) {
								if (!session.matchedNodes.addPair(leftChild
										.getKey(), rightEdge.getToNode()
										.getKey(), session.getScore(leftChild))) {
									Log.log("In execution "
											+ session.left.getProcessId()
											+ " & "
											+ session.right.getProcessId());
									Log.log("Node "
											+ leftChild.getKey()
											+ " of the left graph is already matched!");
									Log.log("Node pair need to be matched: "
											+ leftChild.getKey() + "<->"
											+ rightEdge.getToNode().getKey());
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
													pairNode.level, leftChild
															.getKey(),
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
									Log.log(matchType + ": "
											+ leftChild.getKey() + "<->"
											+ rightEdge.getToNode().getKey()
											+ "(by " + leftNode.getKey()
											+ "<->" + rightNode.getKey() + ")");
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
					} else {
						// Add the indirect node to the queue
						// to delay its matching
						if (!session.matchedNodes.containsRightKey(rightEdge
								.getToNode().getKey())) {
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

				Node leftChild = getCorrespondingIndirectChildNode(
						leftParentNode, rightEdge);
				if (leftChild != null) {
					session.matchedQueue.add(new PairNode(leftChild, rightEdge
							.getToNode(), pairNode.level + 1));

					// Update matched relationship
					if (!session.matchedNodes.hasPair(leftChild.getKey(),
							rightEdge.getToNode().getKey())) {
						if (!session.matchedNodes.addPair(leftChild.getKey(),
								rightEdge.getToNode().getKey(),
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
					leftNode = getCorrespondingNode(rightNode);
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
			session.graphMergingInfo.outputMergedGraphInfo();
			return null;
		} else {
			Log.log("The two graphs merge!!");
			session.graphMergingInfo.outputMergedGraphInfo();
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
