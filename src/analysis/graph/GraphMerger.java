package analysis.graph;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import utils.AnalysisUtil;
import analysis.exception.graph.WrongEdgeTypeException;
import analysis.graph.debug.DebugUtils;
import analysis.graph.debug.MatchingInstance;
import analysis.graph.debug.MatchingType;
import analysis.graph.representation.Edge;
import analysis.graph.representation.EdgeType;
import analysis.graph.representation.ExecutionGraph;
import analysis.graph.representation.MatchedNodes;
import analysis.graph.representation.MutableInteger;
import analysis.graph.representation.Node;
import analysis.graph.representation.NodeList;
import analysis.graph.representation.PairNode;
import analysis.graph.representation.PairNodeEdge;
import analysis.graph.representation.SpeculativeScoreList;
import analysis.graph.representation.SpeculativeScoreRecord;
import analysis.graph.representation.SpeculativeScoreRecord.MatchResult;
import analysis.graph.representation.SpeculativeScoreRecord.SpeculativeScoreType;

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
	 * 
	 * Date: 4:21pm (PST), 06/20/2013 We are trying new approach, which will set
	 * up a threshold for the matching up of speculation. Intuitively, the
	 * threshold for indirect speculation can be less than pure speculation
	 * because it indeed has more information and confidence.
	 * 
	 * Besides, in any speculation when there is many candidates with the same,
	 * high score, the current merging just does not match any of them yet.
	 */
	public static void main(String[] argvs) {

		if (DebugUtils.debug) {
			// Completely ad-hoc debugging code
			ArrayList<String> runDirs = AnalysisUtil
					.getAllRunDirs(DebugUtils.TMP_HASHLOG_DIR);
			String graphDir1 = runDirs.get(runDirs
					.indexOf(DebugUtils.TMP_HASHLOG_DIR + "/run13")), graphDir2 = runDirs
					.get(runDirs.indexOf(DebugUtils.TMP_HASHLOG_DIR + "/run14"));
			ArrayList<ExecutionGraph> graphs1 = ExecutionGraph
					.buildGraphsFromRunDir(graphDir1), graphs2 = ExecutionGraph
					.buildGraphsFromRunDir(graphDir2);
			ExecutionGraph graph1 = graphs1.get(0), graph2 = graphs2.get(0);
			GraphMerger graphMerger = new GraphMerger(graph1, graph2);

			try {
				graphMerger.mergeGraph();
				if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
					// SpeculativeScoreList.showGlobalStats();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public GraphMerger() {

	}

	public GraphMerger(ExecutionGraph graph1, ExecutionGraph graph2) {
		if (graph1.getNodes().size() > graph2.getNodes().size()) {
			this.graph1 = graph1;
			this.graph2 = graph2;
		} else {
			this.graph1 = graph2;
			this.graph2 = graph1;
		}

		if (DebugUtils.debug_decision(DebugUtils.DUMP_GRAPH)) {
			GraphMergingInfo.dumpGraph(graph1,
					"graph-files/" + graph1.getProgName() + graph1.getPid()
							+ ".dot");
			GraphMergingInfo.dumpGraph(graph2,
					"graph-files/" + graph2.getProgName() + graph2.getPid()
							+ ".dot");
		}
	}

	private ExecutionGraph graph1, graph2;
	private ExecutionGraph mergedGraph;

	public static GraphMergingInfo graphMergingInfo;

	// Record matched nodes
	private MatchedNodes matchedNodes = new MatchedNodes();

	// The static threshold for indirect speculation and pure heuristics
	// These two values are completely hypothetic and need further verification
	private static final int IndirectSpeculationThreshold = 10;
	private static final int PureHeuristicsSpeculationThreshold = 15;

	// The speculativeScoreList, which records the detail of the scoring of
	// all the possible cases
	private SpeculativeScoreList speculativeScoreList;

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

	public void startMerging(ExecutionGraph graph1, ExecutionGraph graph2) {
		this.graph1 = graph1;
		this.graph2 = graph2;
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
	public final static int indirectSearchDepth = 10;
	public final static int pureSearchDepth = 15;

	private boolean hasConflict = false;

	/**
	 * This is used only for debugging to analyze the aliasing problem.
	 * 
	 * @param node1
	 * @param node2
	 * @param depth
	 * @return
	 */
	public int debug_getContextSimilarity(Node node1, Node node2, int depth) {
		if (depth <= 0)
			return 0;

		Node trueNode1 = AnalysisUtil.getTrueMatch(graph1, graph2, node2);
		if (node1.equals(trueNode1)) {
			System.out.println(node1.getIndex() + "<=>" + node2.getIndex());
			return 1;
		}

		int score = 0;
		ArrayList<Edge> edges1 = node1.getOutgoingEdges(), edges2 = node2
				.getOutgoingEdges();
		// At least one node has no outgoing edges!!
		if (edges1.size() == 0 || edges2.size() == 0) {
			// Just think that they might be similar...
			if (edges1.size() == 0 && edges2.size() == 0)
				return 1;
			else
				return 0;
		}

		int res = -1;
		// First consider the CallContinuation edge
		Edge e1, e2;
		if ((e2 = node2.getContinuationEdge()) != null
				&& (e1 = node1.getContinuationEdge()) != null) {
			if (e1.getToNode().getHash() != e2.getToNode().getHash()) {
				return -1;
			} else {
				// Check if e1.toNode was already matched to another node; if
				// so, it should return -1 to indicate a conflict
				if (matchedNodes.containsKeyByFirstIndex(e1.getToNode()
						.getIndex())
						&& !matchedNodes.hasPair(e1.getToNode().getIndex(), e2
								.getToNode().getIndex())) {
					return -1;
				}
				score = debug_getContextSimilarity(e1.getToNode(),
						e2.getToNode(), depth - 1);
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
						// because the ordinal of CallContinuation and
						// DirectEdge usually have the same ordinal 0
						continue;
					}
					// This case was considered previously
					if (e1.getEdgeType() == EdgeType.Call_Continuation) {
						continue;
					}
					if (e1.getEdgeType() == EdgeType.Direct) {
						if (e1.getToNode().getHash() != e2.getToNode()
								.getHash()) {
							return -1;
						} else {
							// Check if e1.toNode was already matched to another
							// node; if so, it should return -1 to indicate a
							// conflict
							if (matchedNodes.containsKeyByFirstIndex(e1
									.getToNode().getIndex())
									&& !matchedNodes.hasPair(e1.getToNode()
											.getIndex(), e2.getToNode()
											.getIndex())) {
								return -1;
							}

							res = debug_getContextSimilarity(e1.getToNode(),
									e2.getToNode(), depth - 1);
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
						if (e1.getToNode().getHash() == e2.getToNode()
								.getHash()) {
							res = debug_getContextSimilarity(e1.getToNode(),
									e2.getToNode(), depth - 1);
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

	private int getContextSimilarity(Node node1, Node node2, int depth) {
		if (node2.getIndex() == 66187) {
			System.out.println();
		}
		MutableInteger potentialMaxScore = new MutableInteger(0);
		int score = getContextSimilarity(node1, node2, depth, potentialMaxScore);
		if ((float) score / potentialMaxScore.getVal() > 0.8f) {
			return score;
		} else {
			return 0;
		}
	}

	// FIXME: The way to compute the potentialMaxScore is wrong in case of
	// divergence
	private int getContextSimilarity(Node node1, Node node2, int depth,
			MutableInteger potentialMaxScore) {
		if (depth <= 0)
			return 0;

		int score = 0;
		ArrayList<Edge> edges1 = node1.getOutgoingEdges(), edges2 = node2
				.getOutgoingEdges();
		// At least one node has no outgoing edges!!
		if (edges1.size() == 0 || edges2.size() == 0) {
			// Just think that they might be similar...
			if (edges1.size() == 0 && edges2.size() == 0) {
				potentialMaxScore.setVal(potentialMaxScore.getVal() + 1);
				return 1;
			} else {
				return 0;
			}
		}

		int maxEdgeSize = edges1.size() > edges2.size() ? edges1.size()
				: edges2.size();
		potentialMaxScore.setVal(potentialMaxScore.getVal() + maxEdgeSize);

		int res = -1;
		// First consider the CallContinuation edge
		Edge e1, e2;
		if ((e2 = node2.getContinuationEdge()) != null
				&& (e1 = node1.getContinuationEdge()) != null) {
			if (e1.getToNode().getHash() != e2.getToNode().getHash()) {
				return -1;
			} else {
				// Check if e1.toNode was already matched to another node; if
				// so, it should return -1 to indicate a conflict
				if (matchedNodes.containsKeyByFirstIndex(e1.getToNode()
						.getIndex())
						&& !matchedNodes.hasPair(e1.getToNode().getIndex(), e2
								.getToNode().getIndex())) {
					return -1;
				}
				score = getContextSimilarity(e1.getToNode(), e2.getToNode(),
						depth - 1, potentialMaxScore);
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
						// because the ordinal of CallContinuation and
						// DirectEdge usually have the same ordinal 0
						continue;
					}
					// This case was considered previously
					if (e1.getEdgeType() == EdgeType.Call_Continuation) {
						continue;
					}
					if (e1.getEdgeType() == EdgeType.Direct) {
						if (e1.getToNode().getHash() != e2.getToNode()
								.getHash()) {
							return -1;
						} else {
							// Check if e1.toNode was already matched to another
							// node; if so, it should return -1 to indicate a
							// conflict
							if (matchedNodes.containsKeyByFirstIndex(e1
									.getToNode().getIndex())
									&& !matchedNodes.hasPair(e1.getToNode()
											.getIndex(), e2.getToNode()
											.getIndex())) {
								return -1;
							}

							res = getContextSimilarity(e1.getToNode(),
									e2.getToNode(), depth - 1,
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
						if (e1.getToNode().getHash() == e2.getToNode()
								.getHash()) {
							res = getContextSimilarity(e1.getToNode(),
									e2.getToNode(), depth - 1,
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
	private Node getCorrespondingNode(Node node2) {
		if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
			DebugUtils.debug_pureHeuristicCnt++;
		}

		if (node2.getIndex() == 72831) {
			System.out.println();
		}

		// First check if this is a node already merged
		if (matchedNodes.getBySecondIndex(node2.getIndex()) != null) {
			return graph1.getNodes().get(
					matchedNodes.getBySecondIndex(node2.getIndex()));
		}
		// This node does not belongs to G1 and
		// is not yet added to G1
		NodeList nodes1 = graph1.getNodesByHash(node2.getHash());
		if (nodes1 == null || nodes1.size() == 0) {

			if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
				DebugUtils.debug_pureHeuristicNotPresentCnt++;
			}

			return null;
		}

		ArrayList<Node> candidates = new ArrayList<Node>();
		for (int i = 0; i < nodes1.size(); i++) {
			int score = 0;

			if (DebugUtils.debug) {
				DebugUtils.searchDepth = GraphMerger.pureSearchDepth;
			}

			// If the score is below the threshold, we just don't think it is
			// a potential candidate
			if ((score = getContextSimilarity(nodes1.get(i), node2,
					pureSearchDepth)) > PureHeuristicsSpeculationThreshold) {
				// If the node is already merged, skip it
				if (!matchedNodes.containsKeyByFirstIndex(nodes1.get(i)
						.getIndex())) {
					nodes1.get(i).setScore(score);
					candidates.add(nodes1.get(i));
				}
			}
		}

		// Collect the matching score record for pure heuristics
		// Only in OUTPUT_SCORE debug mode
		if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
			collectScoreRecord(candidates, node2, false);
		}

		if (candidates.size() > 1) {
			// Returns the candidate with highest score
			int pos = 0, score = 0, highestScoreCnt = 0;
			for (int i = 0; i < candidates.size(); i++) {
				if (candidates.get(i).getScore() > score) {
					pos = i;
					score = candidates.get(i).getScore();
					highestScoreCnt = 1;
				} else if (candidates.get(i).getScore() == score) {
					highestScoreCnt++;
				}
			}

			// In the OUTPUT_SCORE debug mode, output the completely speculative
			// matching score to a file
			if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
				String moduleName = AnalysisUtil.getModuleName(graph2,
						node2.getTag());
				long relativeTag = AnalysisUtil.getRelativeTag(graph2,
						node2.getTag());
				DebugUtils.getScorePW().print(
						"PureHeuristic_" + moduleName + "_0x"
								+ Long.toHexString(relativeTag) + "_0x"
								+ Long.toHexString(node2.getTag()) + ":\t");
				for (int i = 0; i < candidates.size(); i++) {
					Node n = candidates.get(i);
					if (n.getScore() > 0) {
						DebugUtils.getScorePW().print(n.getScore() + "\t");
					}
				}
				DebugUtils.getScorePW().println();
			}

			// Ambiguous high score, cannot make any decision
			if (highestScoreCnt > 1) {
				return null;
			}
			Node mostSimilarNode = candidates.get(pos);
			return mostSimilarNode;
		} else if (candidates.size() == 1) {
			Node mostSimilarNode = candidates.get(0);
			return mostSimilarNode;
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
	 * @return The node that matched; Null if no matched node found
	 * @throws WrongEdgeTypeException
	 */
	private Node getCorrespondingDirectChildNode(Node parentNode1,
			Edge curNodeEdge) throws WrongEdgeTypeException {
		Node curNode = curNodeEdge.getToNode();

		for (int i = 0; i < parentNode1.getOutgoingEdges().size(); i++) {
			Edge e = parentNode1.getOutgoingEdges().get(i);
			if (e.getOrdinal() == curNodeEdge.getOrdinal()) {
				if (e.getEdgeType() != curNodeEdge.getEdgeType()) {
					// Call continuation and conditional jump usually have the
					// same ordinal 0
					continue;
				} else {
					if (e.getToNode().getHash() != curNode.getHash()) {
						if (e.getEdgeType() == EdgeType.Direct) {
							System.out
									.println("Direct branch has different targets!");
							if (parentNode1.getContinuationEdge() != null) {
								System.out
										.println("This is likely to be a function call!");
							} else {
								System.out
										.println("This is likely to be a conditional jump!");
							}
						} else {
							System.out
									.println("Call continuation has different targets!");
						}

						// This part of code is purely for the purpose of
						// debugging
						// Because of the failure to filter out immediate
						// address in some instruction,
						// some of the block hashcode is different, which they
						// are supposed to be the same
						// if (DebugUtils
						// .debug_decision(DebugUtils.IGNORE_CONFLICT)) {
						// if (DebugUtils.chageHashLimit > 0) {
						// // if (DebugUtils.commonBitsCnt(e.getToNode()
						// // .getHash(), curNode.getHash()) >=
						// // DebugUtils.commonBitNum) {
						// e.getToNode().setHash(curNode.getHash());
						// DebugUtils.chageHashLimit--;
						// DebugUtils.chageHashCnt++;
						// return e.getToNode();
						// }
						// }

						if (DebugUtils.debug_decision(DebugUtils.MERGE_ERROR)) {
							System.out.println("Direct edge conflict: "
									+ e.getToNode().getIndex() + "<->"
									+ curNode.getIndex() + "(By "
									+ parentNode1.getIndex() + "<->"
									+ curNodeEdge.getFromNode().getIndex()
									+ ")");
						}

						hasConflict = true;
						break;
					} else {
						return e.getToNode();
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
	 * @return The node that matched; Null if no matched node found
	 * @throws WrongEdgeTypeException
	 */

	// In the new approach, we only match the pure speculation when the score
	// exceeds the IndirectSpeculationThreshold
	private Node getCorrespondingIndirectChildNode(Node parentNode1,
			Edge curNodeEdge) throws WrongEdgeTypeException {
		if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
			DebugUtils.debug_indirectHeuristicCnt++;
		}

		Node curNode = curNodeEdge.getToNode();
		if (curNode.getIndex() == 72831) {
			System.out.println();
		}

		// First check if the current node is already matched
		if (matchedNodes.containsKeyBySecondIndex(curNode.getIndex())) {
			return graph1.getNodes().get(
					matchedNodes.getBySecondIndex(curNode.getIndex()));
		}

		ArrayList<Node> candidates = new ArrayList<Node>();

		for (int i = 0; i < parentNode1.getOutgoingEdges().size(); i++) {
			Edge e = parentNode1.getOutgoingEdges().get(i);
			if (e.getOrdinal() == curNodeEdge.getOrdinal()) {
				if (e.getEdgeType() != curNodeEdge.getEdgeType()) {
					if (DebugUtils.ThrowWrongEdgeType) {
						String msg = parentNode1.getHashHex() + "->"
								+ e.getToNode().getHashHex() + "("
								+ e.getEdgeType() + ", "
								+ curNodeEdge.getEdgeType() + ")";
						throw new WrongEdgeTypeException(msg);
					}
					continue;
				} else if (e.getToNode().getHash() == curNode.getHash()) {
					int score = -1;

					if (DebugUtils.debug) {
						DebugUtils.searchDepth = GraphMerger.indirectSearchDepth;
					}
					if ((score = getContextSimilarity(e.getToNode(), curNode,
							GraphMerger.indirectSearchDepth)) > IndirectSpeculationThreshold) {
						if (!matchedNodes.containsKeyByFirstIndex(e.getToNode()
								.getIndex())) {
							e.getToNode().setScore(score);
							candidates.add(e.getToNode());
						}
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
				String moduleName = AnalysisUtil.getModuleName(graph2,
						curNode.getTag());
				long relativeTag = AnalysisUtil.getRelativeTag(graph2,
						curNode.getTag());
				DebugUtils.getScorePW().print(
						"Indirect_" + moduleName + "_0x"
								+ Long.toHexString(relativeTag) + "_0x"
								+ Long.toHexString(curNode.getTag()) + ":\t");
				for (int i = 0; i < candidates.size(); i++) {
					Node n = candidates.get(i);

					if (n.getScore() > 0) {
						DebugUtils.getScorePW().print(n.getScore() + "\t");
					}

				}
				DebugUtils.getScorePW().println();
			}

			int pos = 0, score = -1, highestScoreCnt = 0;
			for (int i = 0; i < candidates.size(); i++) {
				if (candidates.get(i).getScore() > score) {
					score = candidates.get(i).getScore();
					pos = i;
					highestScoreCnt = 1;
				} else if (candidates.get(i).getScore() == score) {
					highestScoreCnt++;
				}
			}

			// Collect the matching score record for indirect speculation
			// Only in OUTPUT_SCORE debug mode
			if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
				collectScoreRecord(candidates, curNode, true);
			}

			// Ambiguous high score, cannot make any decision
			if (highestScoreCnt > 1) {
				return null;
			}
			return candidates.get(pos);
		}
	}

	private void collectScoreRecord(ArrayList<Node> candidates, Node curNode2,
			boolean isIndirect) {
		int maxScore = -1, maxScoreCnt = 0;
		Node maxNode = null;
		for (int i = 0; i < candidates.size(); i++) {
			if (candidates.get(i).getScore() > maxScore
					&& candidates.get(i).getScore() != 0) {
				maxNode = candidates.get(i);
				maxScore = maxNode.getScore();
				maxScoreCnt = 1;
			} else if (candidates.get(i).getScore() == maxScore) {
				maxScoreCnt++;
			}
		}
		if (maxScoreCnt > 1) {
			maxNode = null;
		}

		MatchResult matchResult = AnalysisUtil.getMatchResult(graph1, graph2,
				maxNode, curNode2, isIndirect);
		Node trueNode1 = AnalysisUtil.getTrueMatch(graph1, graph2, curNode2);
		if (maxNode == null) {
			if (matchResult == MatchResult.IndirectExistingUnfoundMismatch
					|| matchResult == MatchResult.PureHeuristicsExistingUnfoundMismatch) {

				speculativeScoreList.add(new SpeculativeScoreRecord(
						SpeculativeScoreType.NoMatch, isIndirect, -1,
						trueNode1, curNode2, null, matchResult));
			} else {
				speculativeScoreList.add(new SpeculativeScoreRecord(
						SpeculativeScoreType.NoMatch, isIndirect, -1, null,
						curNode2, null, matchResult));
			}
			return;
		}

		// Count the different cases for different
		// speculative matching cases
		boolean allLowScore = true;
		if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
			for (int i = 0; i < candidates.size(); i++) {
				int candidateScore = candidates.get(i).getScore();
				if (candidateScore >= SpeculativeScoreRecord.LowScore) {
					allLowScore = false;
				}
			}

			if (allLowScore) {
				// Need to figure out what leads the low score
				// Easier to find if it is the tail case
				if (graph2.isTailNode(curNode2)) {
					speculativeScoreList
							.add(new SpeculativeScoreRecord(
									SpeculativeScoreType.LowScoreTail,
									isIndirect, maxScore, trueNode1, curNode2,
									maxNode, matchResult));
				} else {
					speculativeScoreList.add(new SpeculativeScoreRecord(
							SpeculativeScoreType.LowScoreDivergence,
							isIndirect, maxScore, trueNode1, curNode2, maxNode,
							matchResult));
				}
			} else {
				if (candidates.size() == 1) {
					if (AnalysisUtil.getRelativeTag(graph2, curNode2.getTag()) == AnalysisUtil
							.getRelativeTag(graph1, maxNode.getTag())) {
						speculativeScoreList.add(new SpeculativeScoreRecord(
								SpeculativeScoreType.OneMatchTrue, isIndirect,
								maxScore, trueNode1, curNode2, maxNode,
								matchResult));
					} else {
						speculativeScoreList.add(new SpeculativeScoreRecord(
								SpeculativeScoreType.OneMatchFalse, isIndirect,
								maxScore, trueNode1, curNode2, maxNode,
								matchResult));
					}
				} else {
					if (maxScoreCnt <= 1) {
						speculativeScoreList.add(new SpeculativeScoreRecord(
								SpeculativeScoreType.ManyMatchesCorrect,
								isIndirect, maxScore, trueNode1, curNode2,
								maxNode, matchResult));
					} else {
						speculativeScoreList.add(new SpeculativeScoreRecord(
								SpeculativeScoreType.ManyMatchesAmbiguity,
								isIndirect, maxScore, trueNode1, curNode2,
								maxNode, matchResult));
					}
				}
			}
		}
	}

	private ExecutionGraph buildMergedGraph() {
		ExecutionGraph mergedGraph = new ExecutionGraph();
		mergedGraph.setProgName(graph1.getProgName());
		// Copy nodes from G1
		for (int i = 0; i < graph1.getNodes().size(); i++) {
			Node n1 = graph1.getNodes().get(i);
			Node n = mergedGraph.addNode(n1.getHash(), n1.getMetaNodeType());

			if (matchedNodes.containsKeyByFirstIndex(n.getIndex())) {
				n.setFromWhichGraph(0);
			} else {
				n.setFromWhichGraph(1);
			}
		}

		// Copy edges from G1
		// Traverse edges by outgoing edges
		for (int i = 0; i < graph1.getNodes().size(); i++) {
			Node n1 = graph1.getNodes().get(i);
			for (int j = 0; j < n1.getOutgoingEdges().size(); j++) {
				Edge e1 = n1.getOutgoingEdges().get(j);
				Node newFromNode = mergedGraph.getNodes().get(
						e1.getFromNode().getIndex()), newToNode = mergedGraph
						.getNodes().get(e1.getToNode().getIndex());
				Edge newEdge = new Edge(newFromNode, newToNode,
						e1.getEdgeType(), e1.getOrdinal());
				newFromNode.addOutgoingEdge(newEdge);
				newToNode.addIncomingEdge(newEdge);
			}
		}

		// Copy nodes from G2
		HashMap<Integer, Integer> nodesFromG2 = new HashMap<Integer, Integer>();
		for (int i = 0; i < graph2.getNodes().size(); i++) {
			Node n2 = graph2.getNodes().get(i);
			if (!matchedNodes.containsKeyBySecondIndex(n2.getIndex())) {
				Node n = mergedGraph
						.addNode(n2.getHash(), n2.getMetaNodeType());
				nodesFromG2.put(n2.getIndex(), n.getIndex());
			}
		}

		// Add edges from G2
		if (!addEdgeFromG2(mergedGraph, graph2, nodesFromG2)) {
			System.out.println("There are conflicts when merging edges!");
			return null;
		}

		// Update block hashes and pair hashes
		mergedGraph.addBlockHash(graph1);
		mergedGraph.addBlockHash(graph2);
		mergedGraph.addPairHash(graph1);
		mergedGraph.addPairHash(graph2);

		return mergedGraph;
	}

	private boolean addEdgeFromG2(ExecutionGraph mergedGraph,
			ExecutionGraph graph2, HashMap<Integer, Integer> nodesFromG2) {

		// Merge edges from G2
		// Traverse edges in G2 by outgoing edges
		for (int i = 0; i < graph2.getNodes().size(); i++) {
			Node fromNodeInG2 = graph2.getNodes().get(i), toNodeInG2;
			// New fromNode and toNode in the merged graph
			Node fromNodeInGraph, toNodeInGraph;
			Edge newEdge = null;

			for (int j = 0; j < fromNodeInG2.getOutgoingEdges().size(); j++) {
				Edge e = fromNodeInG2.getOutgoingEdges().get(j);
				toNodeInG2 = e.getToNode();
				if (matchedNodes
						.containsKeyBySecondIndex(toNodeInG2.getIndex())
						&& matchedNodes.containsKeyBySecondIndex(fromNodeInG2
								.getIndex())) {
					// Both are shared nodes, need to check if there are
					// conflicts again!
					fromNodeInGraph = mergedGraph.getNodes().get(
							matchedNodes.getBySecondIndex(fromNodeInG2
									.getIndex()));
					toNodeInGraph = mergedGraph.getNodes()
							.get(matchedNodes.getBySecondIndex(toNodeInG2
									.getIndex()));
					Edge sharedEdge = null;
					for (int k = 0; k < fromNodeInGraph.getOutgoingEdges()
							.size(); k++) {
						if (fromNodeInGraph.getOutgoingEdges().get(k)
								.getToNode().getIndex() == toNodeInGraph
								.getIndex()) {
							sharedEdge = fromNodeInGraph.getOutgoingEdges()
									.get(k);
						}
					}
					if (sharedEdge == null) {
						newEdge = new Edge(fromNodeInGraph, toNodeInGraph,
								e.getEdgeType(), e.getOrdinal());
						fromNodeInGraph.addOutgoingEdge(newEdge);
						toNodeInGraph.addIncomingEdge(newEdge);
					} else {
						if (sharedEdge.getEdgeType() != e.getEdgeType()
								|| sharedEdge.getOrdinal() != e.getOrdinal()) {
							System.out
									.println("There are still some conflicts!");
							return false;
						}
					}
				} else if (matchedNodes.containsKeyBySecondIndex(fromNodeInG2
						.getIndex())
						&& !matchedNodes.containsKeyBySecondIndex(toNodeInG2
								.getIndex())) {
					// First node is a shared node
					fromNodeInGraph = mergedGraph.getNodes().get(
							matchedNodes.getBySecondIndex(fromNodeInG2
									.getIndex()));
					toNodeInGraph = mergedGraph.getNodes().get(
							nodesFromG2.get(toNodeInG2.getIndex()));
					newEdge = new Edge(fromNodeInGraph, toNodeInGraph,
							e.getEdgeType(), e.getOrdinal());
					fromNodeInGraph.addOutgoingEdge(newEdge);
					toNodeInGraph.addIncomingEdge(newEdge);

				} else if (!matchedNodes.containsKeyBySecondIndex(fromNodeInG2
						.getIndex())
						&& matchedNodes.containsKeyBySecondIndex(toNodeInG2
								.getIndex())) {
					// Second node is a shared node
					fromNodeInGraph = mergedGraph.getNodes().get(
							nodesFromG2.get(fromNodeInG2.getIndex()));
					toNodeInGraph = mergedGraph.getNodes()
							.get(matchedNodes.getBySecondIndex(toNodeInG2
									.getIndex()));
					newEdge = new Edge(fromNodeInGraph, toNodeInGraph,
							e.getEdgeType(), e.getOrdinal());
					fromNodeInGraph.addOutgoingEdge(newEdge);
					toNodeInGraph.addIncomingEdge(newEdge);
				} else {
					// Both are new nodes from G2
					fromNodeInGraph = mergedGraph.getNodes().get(
							nodesFromG2.get(fromNodeInG2.getIndex()));
					toNodeInGraph = mergedGraph.getNodes().get(
							nodesFromG2.get(toNodeInG2.getIndex()));
					newEdge = new Edge(fromNodeInGraph, toNodeInGraph,
							e.getEdgeType(), e.getOrdinal());
					fromNodeInGraph.addOutgoingEdge(newEdge);
					toNodeInGraph.addIncomingEdge(newEdge);
				}
			}
		}
		return true;
	}

	private Node getMainBlock(ExecutionGraph graph) {
		// Checkout if the first main block equals to each other
		NodeList preMainBlocks = graph.getNodesByHash(GraphMerger.specialHash);
		if (preMainBlocks == null) {
			return null;
		}
		if (preMainBlocks.size() == 1) {
			Node preMainNode = preMainBlocks.get(0);
			for (int i = 0; i < preMainNode.getOutgoingEdges().size(); i++) {
				if (preMainNode.getOutgoingEdges().get(i).getEdgeType() == EdgeType.Indirect) {
					return preMainNode.getOutgoingEdges().get(i).getToNode();
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

	/**
	 * 
	 * @param graph1
	 * @param graph2
	 * @return
	 * @throws WrongEdgeTypeException
	 */
	private ExecutionGraph mergeGraph() throws WrongEdgeTypeException {
		// Initialize debugging info before merging the graph
		if (DebugUtils.debug) {
			DebugUtils.debug_init();
		}

		// Pre-examination of the graph
		if (graph1 == null || graph2 == null) {
			return null;
		}
		if (graph1.getNodes().size() == 0) {
			return new ExecutionGraph(graph2);
		} else if (graph2.getNodes().size() == 0) {
			return new ExecutionGraph(graph1);
		}

		// In the OUTPUT_SCORE debug mode, initialize the PrintWriter for this
		// merging process
		if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
			if (DebugUtils.getScorePW() != null) {
				DebugUtils.getScorePW().flush();
				DebugUtils.getScorePW().close();
			}
			String fileName = graph1.getProgName() + ".score-"
					+ graph1.getPid() + "-" + graph2.getPid() + ".txt";
			DebugUtils.setScorePW(fileName);
		}

		// Merge based on the similarity of the first node ---- sanity check!
		if (graph1.getNodes().get(0).getHash() != graph2.getNodes().get(0)
				.getHash()) {
			System.out.println("In execution " + graph1.getProgName()
					+ graph1.getPid() + " & " + graph2.getProgName()
					+ graph2.getPid());
			System.out
					.println("First node not the same, so wired and I can't merge...");
			return null;
		}

		// Initialize the speculativeScoreList, which records the detail of
		// the scoring of all the possible cases
		speculativeScoreList = new SpeculativeScoreList();

		// Reset isVisited field
		for (int i = 0; i < graph2.getNodes().size(); i++) {
			graph2.getNodes().get(i).resetVisited();
		}

		// Record matched nodes
		matchedNodes = new MatchedNodes();

		hasConflict = false;
		Node n_1 = graph1.getNodes().get(0), n_2 = graph2.getNodes().get(0);

		// BFS on G2
		Queue<PairNode> matchedQueue = new LinkedList<PairNode>(), unmatchedQueue = new LinkedList<PairNode>();
		PairNode pairNode = new PairNode(n_1, n_2, 0);

		matchedQueue.add(pairNode);
		matchedNodes.addPair(n_1.getIndex(), n_2.getIndex(), 0);

		Node mainNode1 = null, mainNode2 = null;
		mainNode1 = getMainBlock(graph1);
		mainNode2 = getMainBlock(graph2);

		if (DebugUtils.debug_decision(DebugUtils.MAIN_KNOWN_ADD_MAIN)) {
			if (mainNode1 != null && mainNode2 != null) {
				matchedNodes.addPair(mainNode1.getIndex(),
						mainNode2.getIndex(), 0);
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

				if (DebugUtils.debug_decision(DebugUtils.MAIN_KNOWN)) {
					// Treat the first main block specially
					if (n2.getHash() == specialHash) {
						if (mainNode1 != null && mainNode2 != null) {
							if (mainNode1.getHash() != mainNode2.getHash()) {
								System.out.println("Conflict at first block!");
								hasConflict = true;
								break;
							}
						}
					}
				}

				for (int k = 0; k < n2.getOutgoingEdges().size(); k++) {
					Edge e = n2.getOutgoingEdges().get(k);
					if (e.getToNode().isVisited())
						continue;

					// Find out the next matched node
					// Prioritize direct edge and call continuation edge
					Node childNode1;
					if ((e.getEdgeType() == EdgeType.Direct || e.getEdgeType() == EdgeType.Call_Continuation)) {
						childNode1 = getCorrespondingDirectChildNode(n1, e);

						if (childNode1 != null) {
							matchedQueue.add(new PairNode(childNode1, e
									.getToNode(), pairNode.level + 1));

							// Update matched relationship
							if (!matchedNodes.hasPair(childNode1.getIndex(), e
									.getToNode().getIndex())) {
								if (!matchedNodes.addPair(
										childNode1.getIndex(), e.getToNode()
												.getIndex(), childNode1
												.getScore())) {
									System.out.println("In execution "
											+ graph1.getPid() + " & "
											+ graph2.getPid());
									System.out.println(graph1.getRunDir()
											+ " & " + graph2.getRunDir());
									System.out.println("Node "
											+ childNode1.getIndex()
											+ " of G1 is already matched!");
									System.out
											.println("Node pair need to be matched: "
													+ childNode1.getIndex()
													+ "<->"
													+ e.getToNode().getIndex());
									System.out.println("Prematched nodes: "
											+ childNode1.getIndex()
											+ "<->"
											+ matchedNodes
													.getByFirstIndex(childNode1
															.getIndex()));
									System.out.println(matchedNodes
											.getBySecondIndex(e.getToNode()
													.getIndex()));
									hasConflict = true;
									break;
								}

								if (DebugUtils.debug) {
									MatchingType matchType = e.getEdgeType() == EdgeType.Direct ? MatchingType.DirectBranch
											: MatchingType.CallingContinuation;
									DebugUtils.debug_matchingTrace
											.addInstance(new MatchingInstance(
													pairNode.level, childNode1
															.getIndex(), e
															.getToNode()
															.getIndex(),
													matchType, n2.getIndex()));
								}

								if (DebugUtils
										.debug_decision(DebugUtils.PRINT_MATCHING_HISTORY)) {
									// Print out indirect nodes that can be
									// matched
									// by direct edges. However, they might also
									// indirectly
									// decided by the heuristic
									MatchingType matchType = e.getEdgeType() == EdgeType.Direct ? MatchingType.DirectBranch
											: MatchingType.CallingContinuation;
									System.out.println(matchType + ": "
											+ childNode1.getIndex() + "<->"
											+ e.getToNode().getIndex() + "(by "
											+ n1.getIndex() + "<->"
											+ n2.getIndex() + ")");
								}

							}
						} else {
							if (DebugUtils
									.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
								DebugUtils.debug_directUnmatchedCnt++;
							}

							// Should mark that this node should never be
							// matched when
							// it is popped out of the unmatchedQueue
							unmatchedQueue.add(new PairNode(null,
									e.getToNode(), pairNode.level + 1, true));
						}
					} else {
						// Add the indirect node to the queue
						// to delay its matching
						if (!matchedNodes.containsKeyBySecondIndex(e
								.getToNode().getIndex())) {
							indirectChildren.add(new PairNodeEdge(n1, e, n2));
						}
					}
				}
			} else if (indirectChildren.size() != 0) {
				PairNodeEdge nodeEdgePair = indirectChildren.remove();
				Node parentNode1 = nodeEdgePair.getParentNode1(), parentNode2 = nodeEdgePair
						.getParentNode2();
				Edge e = nodeEdgePair.getCurNodeEdge();

				Node childNode1 = getCorrespondingIndirectChildNode(
						parentNode1, e);
				if (childNode1 != null) {
					matchedQueue.add(new PairNode(childNode1, e.getToNode(),
							pairNode.level + 1));

					// Update matched relationship
					if (!matchedNodes.hasPair(childNode1.getIndex(), e
							.getToNode().getIndex())) {
						if (!matchedNodes.addPair(childNode1.getIndex(), e
								.getToNode().getIndex(), childNode1.getScore())) {
							System.out.println("Node " + childNode1.getIndex()
									+ " of G1 is already matched!");
							return null;
						}

						if (DebugUtils.debug) {
							DebugUtils.debug_matchingTrace
									.addInstance(new MatchingInstance(
											pairNode.level, childNode1
													.getIndex(), e.getToNode()
													.getIndex(),
											MatchingType.IndirectBranch,
											parentNode2.getIndex()));
						}

						if (DebugUtils
								.debug_decision(DebugUtils.PRINT_MATCHING_HISTORY)) {
							// Print out indirect nodes that must be decided by
							// heuristic
							System.out.println("Indirect: "
									+ childNode1.getIndex() + "<->"
									+ e.getToNode().getIndex() + "(by "
									+ parentNode1.getIndex() + "<->"
									+ parentNode2.getIndex() + ")");
						}
					}
				} else {
					if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
						DebugUtils.debug_indirectHeuristicUnmatchedCnt++;
					}

					unmatchedQueue.add(new PairNode(null, e.getToNode(),
							pairNode.level + 1));
				}

			} else {
				// try to match unmatched nodes
				pairNode = unmatchedQueue.remove();
				Node curNode = pairNode.getNode2();
				if (curNode.isVisited())
					continue;

				Node node1 = null;
				// For nodes that are already known not to match,
				// simply don't match them
				if (!pairNode.neverMatched) {
					node1 = getCorrespondingNode(curNode);
				}

				if (node1 != null) {

					if (DebugUtils.debug) {
						DebugUtils.debug_matchingTrace
								.addInstance(new MatchingInstance(
										pairNode.level, node1.getIndex(),
										curNode.getIndex(),
										MatchingType.PureHeuristic, -1));
					}

					if (DebugUtils
							.debug_decision(DebugUtils.PRINT_MATCHING_HISTORY)) {
						// Print out indirect nodes that must be decided by
						// heuristic
						System.out.println("PureHeuristic: " + node1.getIndex()
								+ "<->" + curNode.getIndex()
								+ "(by pure heuristic)");
					}

					matchedQueue.add(new PairNode(node1, curNode,
							pairNode.level, true));
				} else {
					// Simply push unvisited neighbors to unmatchedQueue
					for (int k = 0; k < curNode.getOutgoingEdges().size(); k++) {
						Edge e = curNode.getOutgoingEdges().get(k);
						if (e.getToNode().isVisited())
							continue;

						unmatchedQueue.add(new PairNode(null, e.getToNode(),
								pairNode.level + 1));
					}
					curNode.setVisited();
				}
			}
		}

		if (DebugUtils.debug_decision(DebugUtils.MAIN_KNOWN)) {
			if (mainNode1 != null && mainNode2 != null) {
				if (mainNode1.getHash() != mainNode2.getHash()) {
					System.out.println("Conflict at first block!");
					hasConflict = true;
				}
			}
		}

		if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
			System.out.println("All pure heuristic: "
					+ DebugUtils.debug_pureHeuristicCnt);
			System.out.println("Pure heuristic not present: "
					+ DebugUtils.debug_pureHeuristicNotPresentCnt);
			System.out.println("All direct unsmatched: "
					+ DebugUtils.debug_directUnmatchedCnt);
			System.out.println("All indirect heuristic: "
					+ DebugUtils.debug_indirectHeuristicCnt);
			System.out.println("Indirect heuristic unmatched: "
					+ DebugUtils.debug_indirectHeuristicUnmatchedCnt);
		}

		// In the OUTPUT_SCORE debug mode, close the PrintWriter when merging
		// finishes, also print out the score statistics
		if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
			DebugUtils.getScorePW().flush();
			DebugUtils.getScorePW().close();

			// Count and print out the statistical results of each speculative
			// matching case
			speculativeScoreList.setHasConflict(hasConflict);
			speculativeScoreList.count();
			speculativeScoreList.showResult();
		}

		graphMergingInfo = new GraphMergingInfo(graph1, graph2, matchedNodes);
		if (hasConflict) {
			System.out.println("Can't merge the two graphs!!");
			graphMergingInfo.outputMergedGraphInfo();
			return null;
		} else {
			System.out.println("The two graphs merge!!");
			graphMergingInfo.outputMergedGraphInfo();
			mergedGraph = buildMergedGraph();
			return mergedGraph;
		}
	}

	public void run() {
		try {
			// Before merging, cheat to filter out all the immediate addresses
			if (DebugUtils.debug_decision(DebugUtils.FILTER_OUT_IMME_ADDR)) {
				AnalysisUtil.filteroutImmeAddr(graph1, graph2);
			}

			mergedGraph = mergeGraph();
			if (hasConflict) {
				if (graph1.getProgName().equals(graph2.getProgName())) {
					System.out.println("Wrong match!");
				}
			} else {
				if (!graph1.getProgName().equals(graph2.getProgName())) {
					System.out.println("Unable to tell difference!");
				}
			}
			// System.out.println("Changed hashcode for " +
			// DebugUtils.chageHashCnt + " times");
		} catch (WrongEdgeTypeException e) {
			e.printStackTrace();
		}

	}
}
