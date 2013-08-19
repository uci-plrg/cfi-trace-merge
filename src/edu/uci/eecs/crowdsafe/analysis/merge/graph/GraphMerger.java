package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import edu.uci.eecs.crowdsafe.analysis.data.graph.Edge;
import edu.uci.eecs.crowdsafe.analysis.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.MatchedNodes;
import edu.uci.eecs.crowdsafe.analysis.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.analysis.data.graph.MutableInteger;
import edu.uci.eecs.crowdsafe.analysis.data.graph.Node;
import edu.uci.eecs.crowdsafe.analysis.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.analysis.data.graph.NormalizedTag;
import edu.uci.eecs.crowdsafe.analysis.data.graph.PairNode;
import edu.uci.eecs.crowdsafe.analysis.data.graph.PairNodeEdge;
import edu.uci.eecs.crowdsafe.analysis.data.graph.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.analysis.data.graph.SpeculativeScoreList;
import edu.uci.eecs.crowdsafe.analysis.data.graph.SpeculativeScoreRecord;
import edu.uci.eecs.crowdsafe.analysis.data.graph.SpeculativeScoreRecord.MatchResult;
import edu.uci.eecs.crowdsafe.analysis.data.graph.SpeculativeScoreRecord.SpeculativeScoreType;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.WrongEdgeTypeException;
import edu.uci.eecs.crowdsafe.analysis.loader.ProcessGraphDataLoader;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.DebugUtils;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.MatchingInstance;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.MatchingType;

import utils.AnalysisUtil;

public class GraphMerger {
	/**
	 * <p>
	 * This class abstracts an object that can match two ExecutionGraph. To initialize these two graphs, pass two
	 * well-constructed ExecutionGraph and call the mergeGraph() method. It will merge the two graphs and construct a
	 * new merged graph, which you can get it by calling the getMergedGraph() method.
	 * </p>
	 * 
	 * <p>
	 * It has been found that both in Linux and Windows, the real entry block of code in the main module comes from an
	 * indirect branch of some certain C library (linux) or system libraries (ntdll.dll in Windows). Our current
	 * approach treats the indirect edges as half speculation, which in this case means all programs will match if we
	 * don't know the entry block. Therefore, we assume that we will know a list of rarely changed entry block and they
	 * can be provided as part of the configuration.
	 * </p>
	 * 
	 * <p>
	 * Programs in x86/linux seems to enter their main function after a very similar dynamic-loading process, at the end
	 * of which there is a indirect branch which jumps to the real main blocks. In the environment of this machine, the
	 * hash value of that 'final block' is 0x1d84443b9bf8a6b3. ####
	 * </p>
	 * 
	 * <p>
	 * Date: 4:21pm (PST), 06/20/2013 We are trying new approach, which will set up a threshold for the matching up of
	 * speculation. Intuitively, the threshold for indirect speculation can be less than pure speculation because it
	 * indeed has more information and confidence.
	 * </p>
	 * 
	 * <p>
	 * Besides, in any speculation when there is many candidates with the same, high score, the current merging just
	 * does not match any of them yet.
	 * </p>
	 * 
	 * <p>
	 * To use the current matching approach for the ModuleGraph, we extends the GraphMerger to ModuleGraphMerger, and
	 * overrides its mergeGraph() method. At the same time, this class contains a ModuleGraphMerger subclass which
	 * matches the ModuleGraphs.
	 * </p>
	 * 
	 */
	public static void main(String[] args) {
		if (args.length != 2) {
			System.out
					.println("Illegal arguments: please specify the two run directories as relative or absolute paths.");
			System.exit(1);
		}

		File run0 = new File(args[0]);
		File run1 = new File(args[1]);

		if (!(run0.exists() && run0.isDirectory())) {
			System.out.println("Illegal argument " + args[0]
					+ "; no such directory.");
		}
		if (!(run1.exists() && run1.isDirectory())) {
			System.out.println("Illegal argument " + args[1]
					+ "; no such directory.");
		}

		System.out.println("### --------------- ###");
		ProcessExecutionGraph graph0 = ProcessGraphDataLoader.loadProcessGraph(run0);
		ProcessExecutionGraph graph1 = ProcessGraphDataLoader.loadProcessGraph(run1);

		if (DebugUtils.debug_decision(DebugUtils.FILTER_OUT_IMME_ADDR)) {
			AnalysisUtil.filteroutImmeAddr(graph0, graph1);
		}
		GraphMerger graphMerger = new GraphMerger(graph0, graph1);

		try {
			graphMerger.mergeGraph();
			graphMerger.mergeModules();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public GraphMerger() {

	}

	public ProcessExecutionGraph getGraph1() {
		return graph1;
	}

	public ProcessExecutionGraph getGraph2() {
		return graph2;
	}

	public GraphMerger(ProcessExecutionGraph graph1, ProcessExecutionGraph graph2) {
		if (graph1.getNodes().size() > graph2.getNodes().size()) {
			this.graph1 = graph1;
			this.graph2 = graph2;
		} else {
			this.graph1 = graph2;
			this.graph2 = graph1;
		}

		System.out.println("\n    ==== " + graph1.getProgName() + " & "
				+ graph2.getProgName() + " ====");

		if (DebugUtils.debug_decision(DebugUtils.DUMP_GRAPH)) {
			GraphMergingInfo.dumpGraph(
					graph1,
					DebugUtils.GRAPH_DIR + graph1.getProgName()
							+ graph1.getPid() + ".dot");
			GraphMergingInfo.dumpGraph(
					graph2,
					DebugUtils.GRAPH_DIR + graph2.getProgName()
							+ graph2.getPid() + ".dot");
		}
	}

	protected ProcessExecutionGraph graph1, graph2;
	protected ProcessExecutionGraph mergedGraph;
	protected HashMap<String, ModuleGraph> mergedModules;

	public GraphMergingInfo graphMergingInfo;

	// Record matched nodes
	protected MatchedNodes matchedNodes = new MatchedNodes();

	// BFS on G2
	Queue<PairNode> matchedQueue, unmatchedQueue;
	// To record all the unvisited indirect node.
	Queue<PairNodeEdge> indirectChildren;

	// The static threshold for indirect speculation and pure heuristics
	// These two values are completely hypothetic and need further verification
	private static final int IndirectSpeculationThreshold = 10;
	private static final int DirectSpeculationThreshold = 0;
	private static final int PureHeuristicsSpeculationThreshold = 15;

	// The speculativeScoreList, which records the detail of the scoring of
	// all the possible cases
	private SpeculativeScoreList speculativeScoreList;

	public void setGraph1(ProcessExecutionGraph graph1) {
		this.graph1 = graph1;
	}

	public void setGraph2(ProcessExecutionGraph graph2) {
		this.graph2 = graph2;
	}

	public ProcessExecutionGraph getMergedGraph() {
		return mergedGraph;
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
	 * @param node1
	 * @param node2
	 * @param depth
	 * @return
	 */
	public int debug_getContextSimilarity(Node node1, Node node2, int depth) {
		if (depth <= 0)
			return 0;

		if (comparedNodes.contains(node2)) {
			return 1;
		} else {
			comparedNodes.add(node2);
		}
		Node trueNode1 = AnalysisUtil.getTrueMatch(graph1, graph2, node2);
		if (node1.equals(trueNode1)) {
			return 1;
		}

		int score = 0;
		ArrayList<Edge> edges1 = node1.getOutgoingEdges(), edges2 = node2
				.getOutgoingEdges();
		// At least one node has no outgoing edges!!
		if (edges1.size() == 0 || edges2.size() == 0) {
			// Just think that they might be similar...
			if (edges1.size() == 0 && edges2.size() == 0) {
				return 1;
			} else {
				return 0;
			}
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
					if (e1.getEdgeType() == EdgeType.CallContinuation) {
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

	public static final float validScoreLimit = 0.5f;

	// In case of recursively compute the similarity of cyclic graph, record
	// the compared nodes every time getContextSimilarity is called
	public HashSet<Node> comparedNodes = new HashSet<Node>();

	public int getContextSimilarity(Node node1, Node node2, int depth) {
		MutableInteger potentialMaxScore = new MutableInteger(0);

		comparedNodes.clear();
		int score = getContextSimilarity(node1, node2, depth, potentialMaxScore);
		if ((float) score / potentialMaxScore.getVal() > validScoreLimit) {
			return score;
		} else {
			return 0;
		}
	}

	private int getContextSimilarity(Node node1, Node node2, int depth,
			MutableInteger potentialMaxScore) {
		if (depth <= 0)
			return 0;
		// In order to avoid cyclic graph
		if (comparedNodes.contains(node2)) {
			return 1;
		} else {
			comparedNodes.add(node2);
		}

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

		// The way to compute the potentialMaxScore is to count the seen
		// possible divergence in the context.
		int maxEdgeSize = edges1.size() > edges2.size() ? edges1.size()
				: edges2.size();
		int maxOrdinal = 0;
		for (int i = 0; i < edges1.size(); i++) {
			if (edges1.get(i).getOrdinal() > maxOrdinal) {
				maxOrdinal = edges1.get(i).getOrdinal();
			}
		}
		for (int i = 0; i < edges2.size(); i++) {
			if (edges2.get(i).getOrdinal() > maxOrdinal) {
				maxOrdinal = edges2.get(i).getOrdinal();
			}
		}
		potentialMaxScore.setVal(potentialMaxScore.getVal() + maxEdgeSize
				+ maxOrdinal);

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
					if (e1.getEdgeType() == EdgeType.CallContinuation) {
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
		graphMergingInfo.tryPureHeuristicMatch();
		if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
			DebugUtils.debug_pureHeuristicCnt++;
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
						node2.getTag().tag);
				long relativeTag = AnalysisUtil.getRelativeTag(graph2,
						node2.getTag().tag);
				DebugUtils.getScorePW().print(
						"PureHeuristic_" + moduleName + "_0x"
								+ Long.toHexString(relativeTag) + "_0x"
								+ Long.toHexString(node2.getTag().tag) + ":\t");
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
			graphMergingInfo.pureHeuristicMatch();
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
	 * @param parentNode1
	 * @param curNodeEdge
	 * @return The node that matched; Null if no matched node found
	 * @throws WrongEdgeTypeException
	 */
	private Node getCorrespondingDirectChildNode(Node parentNode1,
			Edge curNodeEdge) throws WrongEdgeTypeException {
		Node curNode = curNodeEdge.getToNode();

		// Direct edges will also have multiple possible match because of the
		// existence of code re-writing
		ArrayList<Node> candidates = new ArrayList<Node>();
		int score;

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
									.println("Direct branch has different targets, "
											+ "but it may be caused by code re-writing!");
							if (parentNode1.getContinuationEdge() != null) {
								System.out
										.println("This is likely to be a function call!");
							} else {
								System.out
										.println("This is likely to be a conditional jump!");
							}
						} else {
							System.out
									.println("Call continuation has different targets, "
											+ "but it may be caused by code re-writing!");
						}
						// No enough information anymore, just keep going
						// But we will pretend nothing happens now
						// continue;
						System.out.println("Code re-written!");
						hasConflict = true;
					} else {
						if (e.getEdgeType() == EdgeType.Direct) {
							graphMergingInfo.directMatch();
						} else {
							graphMergingInfo.callContinuationMatch();
						}

						if ((score = getContextSimilarity(e.getToNode(),
								curNode, GraphMerger.directSearchDepth)) > DirectSpeculationThreshold) {
							if (!matchedNodes.containsKeyByFirstIndex(e
									.getToNode().getIndex())) {
								e.getToNode().setScore(score);
								candidates.add(e.getToNode());
							}
						}
						// Just return the node now
						return e.getToNode();
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
						curNode.getTag().tag);
				long relativeTag = AnalysisUtil.getRelativeTag(graph2,
						curNode.getTag().tag);
				DebugUtils.getScorePW().print(
						"Direct_" + moduleName + "_0x"
								+ Long.toHexString(relativeTag) + "_0x"
								+ Long.toHexString(curNode.getTag().tag)
								+ ":\t");
				for (int i = 0; i < candidates.size(); i++) {
					Node n = candidates.get(i);

					if (n.getScore() > 0) {
						DebugUtils.getScorePW().print(n.getScore() + "\t");
					}

				}
				DebugUtils.getScorePW().println();
			}

			int pos = 0, highestScoreCnt = 0;
			score = -1;
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

			graphMergingInfo.directMatch();
			return candidates.get(pos);
		}
	}

	/**
	 * Search for corresponding indirect child node, including indirect edge and unexpected return edges
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

		graphMergingInfo.tryIndirectMatch();

		Node curNode = curNodeEdge.getToNode();

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
						curNode.getTag().tag);
				long relativeTag = AnalysisUtil.getRelativeTag(graph2,
						curNode.getTag().tag);
				DebugUtils.getScorePW().print(
						"Indirect_" + moduleName + "_0x"
								+ Long.toHexString(relativeTag) + "_0x"
								+ Long.toHexString(curNode.getTag().tag)
								+ ":\t");
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

			graphMergingInfo.indirectMatch();
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
					if (AnalysisUtil.getRelativeTag(graph2,
							curNode2.getTag().tag) == AnalysisUtil
							.getRelativeTag(graph1, maxNode.getTag().tag)) {
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

	protected ProcessExecutionGraph buildMergedGraph() {
		// Don't have to copy the hash2Nodes explicitly because it will be
		// automatically added when calling addNode method, but you should
		// take care of maintaining the signature2Node by yourself, be careful!!

		// Get an empty new graph to copy nodes and edges
		ProcessExecutionGraph mergedGraph;
		if ((graph1 instanceof ModuleGraph) && (graph2 instanceof ModuleGraph)) {
			mergedGraph = new ModuleGraph(((ModuleGraph) graph1).moduleName);
		} else {
			mergedGraph = new ProcessExecutionGraph();
		}

		// The program name does not mean anything essentially
		// It's only done for convenience
		mergedGraph.setProgName(graph1.getProgName());

		// The following Node variables with ordinal "1" mean those nodes from
		// graph1, "2" from graph2, without ordinal for nodes in the new graph
		// The naming rule is also true for Edge variables

		// Copy nodes from graph1
		for (int i = 0; i < graph1.getNodes().size(); i++) {
			Node n1 = graph1.getNodes().get(i);
			Node n = mergedGraph.addNode(n1.getHash(), n1.getMetaNodeType(),
					new NormalizedTag(n1));

			if (matchedNodes.containsKeyByFirstIndex(n.getIndex())) {
				n.setFromWhichGraph(0);
			} else {
				n.setFromWhichGraph(1);
			}
		}

		// Copy edges from graph1
		// Traverse edges by outgoing edges
		for (int i = 0; i < graph1.getNodes().size(); i++) {
			Node n1 = graph1.getNodes().get(i);
			for (int j = 0; j < n1.getOutgoingEdges().size(); j++) {
				Edge e1 = n1.getOutgoingEdges().get(j);
				Node newFromNode = mergedGraph.getNodes().get(
						e1.getFromNode().getIndex());
				if (e1.getToNode().getIndex() == 75329) {
					System.out.println(e1.getFromNode());
					System.out.println(e1.getToNode());
				}
				Node newToNode = mergedGraph.getNodes().get(
						e1.getToNode().getIndex());
				Edge newEdge = new Edge(newFromNode, newToNode,
						e1.getEdgeType(), e1.getOrdinal());
				newFromNode.addOutgoingEdge(newEdge);
				newToNode.addIncomingEdge(newEdge);
			}
		}

		// Copy nodes from graph2
		HashMap<Integer, Integer> nodesFromG2 = new HashMap<Integer, Integer>();
		for (int i = 0; i < graph2.getNodes().size(); i++) {
			Node n2 = graph2.getNodes().get(i);
			if (!matchedNodes.containsKeyBySecondIndex(n2.getIndex())) {
				Node n = mergedGraph.addNode(n2.getHash(),
						n2.getMetaNodeType(), new NormalizedTag(n2));
				nodesFromG2.put(n2.getIndex(), n.getIndex());
			}
		}

		// Add edges from graph2
		if (!addEdgeFromG2(mergedGraph, graph2, nodesFromG2)) {
			System.out.println("There are conflicts when merging edges!");
			return null;
		}

		// Update block hashes and pair hashes
		// TODO: this appears to have been incorrect, because it merged `blockHashes` instead of `totalBlockHashes`
		// without descending into modules
		// mergedGraph.addBlockHash(graph1);
		// mergedGraph.addBlockHash(graph2);

		return mergedGraph;
	}

	private boolean addEdgeFromG2(ProcessExecutionGraph mergedGraph,
			ProcessExecutionGraph graph2, HashMap<Integer, Integer> nodesFromG2) {

		// Merge edges from graph2
		// Traverse edges in graph2 by outgoing edges
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

	private Node getMainBlock(ProcessExecutionGraph graph) {
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

	protected void addUnmatchedNode2Queue(Node node2, int level) {
		if (node2 == null) {
			throw new NullPointerException("There is a bug here!");
		}
		unmatchedQueue.add(new PairNode(null, node2, level));
	}

	/**
	 * Setup before matching the two graphs.
	 */
	protected boolean preMergeGraph() {

		System.out.println("### Start to merge the main module ###");

		// Reset isVisited field
		for (int i = 0; i < graph2.getNodes().size(); i++) {
			Node n = graph2.getNodes().get(i);
			n.resetVisited();
		}

		// Record matched nodes
		matchedNodes = new MatchedNodes();

		hasConflict = false;

		// Initialize debugging info before merging the graph
		if (DebugUtils.debug) {
			DebugUtils.debug_init();
		}

		// BFS on G2
		matchedQueue = new LinkedList<PairNode>();
		unmatchedQueue = new LinkedList<PairNode>();
		indirectChildren = new LinkedList<PairNodeEdge>();

		graphMergingInfo = new GraphMergingInfo(graph1, graph2, matchedNodes);

		HashMap<Long, Node> graph1Sig2Node = graph1.getSigature2Node(), graph2Sig2Node = graph2
				.getSigature2Node();

		for (long sigHash : graph2Sig2Node.keySet()) {
			if (graph1Sig2Node.containsKey(sigHash)) {
				Node n1 = graph1Sig2Node.get(sigHash);
				Node n2 = graph2Sig2Node.get(sigHash);

				PairNode pairNode = new PairNode(n1, n2, 0);
				matchedQueue.add(pairNode);
				matchedNodes.addPair(n1.getIndex(), n2.getIndex(), 0);

				graphMergingInfo.directMatch();

				if (DebugUtils.debug) {
					// AnalysisUtil.outputIndirectNodesInfo(n1, n2);
				}

				if (DebugUtils.debug) {
					DebugUtils.debug_matchingTrace
							.addInstance(new MatchingInstance(0, n1.getIndex(),
									n2.getIndex(), MatchingType.SignatureNode,
									-1));
					System.out.println("SignatureNode: " + n1.getIndex()
							+ "<->" + n2.getIndex() + "(by ...)");
				}
			} else {
				// Push new signature node to prioritize the speculation to the
				// beginning of the graph
				Node n2 = graph2Sig2Node.get(sigHash);
				if (n2 == null) {
					System.out.println(0);
				}
				addUnmatchedNode2Queue(n2, -1);
			}
		}

		return true;
	}

	/**
	 * 
	 * @param graph1
	 * @param graph2
	 * @return
	 * @throws WrongEdgeTypeException
	 */
	public ProcessExecutionGraph mergeGraph() throws WrongEdgeTypeException {

		// Pre-examination of the graph
		if (graph1 == null || graph2 == null) {
			return null;
		}
		if (graph1.getNodes().size() == 0) {
			return graph2;
		} else if (graph2.getNodes().size() == 0) {
			return graph1;
		}

		// Set up the initial status before actually matching
		if (!preMergeGraph()) {
			return null;
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

		// Initialize the speculativeScoreList, which records the detail of
		// the scoring of all the possible cases
		if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
			speculativeScoreList = new SpeculativeScoreList(this);
		}

		PairNode pairNode = null;
		while ((matchedQueue.size() > 0 || indirectChildren.size() > 0 || unmatchedQueue
				.size() > 0) && !hasConflict) {
			if (matchedQueue.size() > 0) {
				pairNode = matchedQueue.remove();

				// Nodes in the matchedQueue is already matched
				Node n1 = pairNode.getNode1(), n2 = pairNode.getNode2();
				if (n2.isVisited())
					continue;
				n2.setVisited();

				for (int k = 0; k < n2.getOutgoingEdges().size(); k++) {
					Edge e = n2.getOutgoingEdges().get(k);
					if (e.getToNode().isVisited())
						continue;

					// Find out the next matched node
					// Prioritize direct edge and call continuation edge
					Node childNode1;
					if ((e.getEdgeType() == EdgeType.Direct || e.getEdgeType() == EdgeType.CallContinuation)) {
						graphMergingInfo.tryDirectMatch();

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
							addUnmatchedNode2Queue(e.getToNode(),
									pairNode.level + 1);
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
							System.out.print("Indirect: "
									+ childNode1.getIndex() + "<->"
									+ e.getToNode().getIndex() + "(by "
									+ parentNode1.getIndex() + "<->"
									+ parentNode2.getIndex() + ")");
							System.out.println();
						}
					}
				} else {
					if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
						DebugUtils.debug_indirectHeuristicUnmatchedCnt++;
					}

					addUnmatchedNode2Queue(e.getToNode(), pairNode.level + 1);
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

						addUnmatchedNode2Queue(e.getToNode(),
								pairNode.level + 1);
					}
					curNode.setVisited();
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

	/**
	 * By cheating (knowing the normalized tags), we can evaluate how good the matching is. It considers mismatching
	 * (nodes should not be matched have been matched) and unmatching (nodes should be matched have not been matched).
	 */
	protected void evaluateMatching() {

	}

	/**
	 * This should be called after matching the main modules, and the ModuleGraphMerger should not call this function
	 * 
	 * @return
	 */
	public HashMap<String, ModuleGraph> mergeModules() {
		HashMap<String, ModuleGraph> modName2Graph = new HashMap<String, ModuleGraph>();
		HashMap<String, ModuleGraph> modules1 = graph1.getModuleGraphs(), modules2 = graph2
				.getModuleGraphs();
		if (modules1 == null || modules2 == null) {
			return null;
		}
		for (String name : modules1.keySet()) {
			if (modules2.containsKey(name)) {
				ModuleGraph module1 = modules1.get(name), module2 = modules2
						.get(name);
				ModuleGraphMerger mGraphMerger = new ModuleGraphMerger(module1,
						module2);
				try {
					ModuleGraph mergedModuleGraph = (ModuleGraph) mGraphMerger
							.mergeGraph();
					if (mergedGraph == null) {
						System.out
								.println("Conflict occurs when matching module "
										+ name + "!\n");
						hasConflict = true;
					} else {
						modName2Graph.put(name, mergedModuleGraph);
					}
				} catch (WrongEdgeTypeException e) {
					e.printStackTrace();
				}
			}
		}

		if (hasConflict) {
			System.out.println("A conflict happens when matching the grahps!");

		}
		return modName2Graph;
	}
}
