package edu.uci.eecs.crowdsafe.merge.graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.CrowdSafeTraceUtil;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.graph.SpeculativeScoreRecord.MatchResult;
import edu.uci.eecs.crowdsafe.merge.graph.SpeculativeScoreRecord.SpeculativeScoreType;
import edu.uci.eecs.crowdsafe.merge.graph.debug.DebugUtils;
import edu.uci.eecs.crowdsafe.merge.util.AnalysisUtil;

public class GraphMergeStatistics {

	public static final float LOW_MATCHING_TRHESHOLD = 0.15f;

	private final GraphMergeSession session;

	private int directEdgeTrialCnt = 0;
	private int indirectEdgeTrialCnt = 0;
	private int indirectEdgeMatchCnt = 0;
	private int pureHeuristicTrialCnt = 0;
	private int pureHeuristicMatchCnt = 0;
	private int directMatchCnt = 0;
	private int callContinuationMatchCnt = 0;
	private int possibleRewrites = 0;

	private float setInterRate = 0f;
	private int totalNodeSize = 0;
	private int totalHashSize = 0;
	private int interHashSize = 0;

	public GraphMergeStatistics(GraphMergeSession session) {
		this.session = session;

		Set<Long> interBlockHashes = AnalysisUtil.intersection(
				session.left.cluster.getGraphData().nodesByHash.keySet(),
				session.right.cluster.getGraphData().nodesByHash.keySet());
		Set<Long> totalBlockHashes = AnalysisUtil.union(
				session.left.cluster.getGraphData().nodesByHash.keySet(),
				session.right.cluster.getGraphData().nodesByHash.keySet());
		interHashSize = interBlockHashes.size();
		totalHashSize = totalBlockHashes.size();
		setInterRate = (float) interBlockHashes.size() / totalHashSize;
	}

	public void reset() {
		directEdgeTrialCnt = 0;
		indirectEdgeTrialCnt = 0;
		indirectEdgeMatchCnt = 0;
		pureHeuristicTrialCnt = 0;
		pureHeuristicMatchCnt = 0;
		directMatchCnt = 0;
		callContinuationMatchCnt = 0;
		possibleRewrites = 0;

		setInterRate = 0f;
		totalNodeSize = 0;
		totalHashSize = 0;
		interHashSize = 0;
	}

	public void tryDirectMatch() {
		directEdgeTrialCnt++;
	}

	public void tryIndirectMatch() {
		indirectEdgeTrialCnt++;
	}

	public void tryPureHeuristicMatch() {
		pureHeuristicTrialCnt++;
	}

	public void indirectMatch() {
		indirectEdgeMatchCnt++;
	}

	public void pureHeuristicMatch() {
		pureHeuristicMatchCnt++;
	}

	public void directMatch() {
		directMatchCnt++;
	}

	public void callContinuationMatch() {
		callContinuationMatchCnt++;
	}

	public void possibleRewrite() {
		possibleRewrites++;
	}

	public ArrayList<ExecutionNode> unmatchedGraph1Nodes() {
		ArrayList<ExecutionNode> unmatchedNodes = new ArrayList<ExecutionNode>();
		for (ExecutionNode n : session.left.cluster.getGraphData().nodesByKey
				.values()) {
			if (!session.matchedNodes.containsLeftKey(n.getKey())) {
				unmatchedNodes.add(n);
			}
		}
		return unmatchedNodes;
	}

	public ArrayList<ExecutionNode> unmatchedGraph2Nodes() {
		ArrayList<ExecutionNode> unmatchedNodes = new ArrayList<ExecutionNode>();
		for (ExecutionNode n : session.right.cluster.getGraphData().nodesByKey
				.values()) {
			if (!session.matchedNodes.containsRightKey(n.getKey())) {
				unmatchedNodes.add(n);
			}
		}
		return unmatchedNodes;
	}

	public boolean lowMatching() {
		float nodeInterRate = (float) session.matchedNodes.size()
				/ totalNodeSize;
		if ((setInterRate - nodeInterRate) > LOW_MATCHING_TRHESHOLD) {
			return true;
		} else {
			return false;
		}
	}

	public void dumpMatchedNodes() {
		for (Node.Key leftKey : session.matchedNodes.getLeftKeySet()) {
			Node.Key rightKey = session.matchedNodes.getMatchByLeftKey(leftKey);
			Log.log(leftKey + "<-->" + rightKey);
		}
	}

	synchronized public void outputMergedGraphInfo() {
		totalNodeSize = session.left.cluster.getGraphData().nodesByKey.size()
				+ session.right.cluster.getGraphData().nodesByKey.size()
				- session.matchedNodes.size();
		Log.log("Block hash count on the left: %d",
				session.left.cluster.getGraphData().nodesByHash.keySet().size());
		Log.log("Block hash count on the right: %d", session.right.cluster
				.getGraphData().nodesByHash.keySet().size());
		Set<Long> totalBlockSet = AnalysisUtil.intersection(
				session.left.cluster.getGraphData().nodesByHash.keySet(),
				session.right.cluster.getGraphData().nodesByHash.keySet());
		Log.log("Block hash count in the merged graph: %d",
				totalBlockSet.size());

		Log.log("Nodes on the left: %d",
				session.left.cluster.getGraphData().nodesByKey.size());
		Log.log("Nodes on the right: %d",
				session.right.cluster.getGraphData().nodesByKey.size());
		Log.log("Nodes in the merged graph: %d", totalNodeSize);

		Log.log("Intersecting nodes: %d; ratio of block hashes: %f %d,%d:%d/%d",
				session.matchedNodes.size(), setInterRate, session.left.cluster
						.getGraphData().nodesByHash.keySet().size(),
				session.right.cluster.getGraphData().nodesByHash.keySet()
						.size(), interHashSize, totalHashSize);

		Log.log("Intersection/left: %f", (float) session.matchedNodes.size()
				/ session.left.cluster.getGraphData().nodesByKey.size());
		Log.log("Intersection/right: %f", (float) session.matchedNodes.size()
				/ session.right.cluster.getGraphData().nodesByKey.size());
		float nodeInterRate = (float) session.matchedNodes.size()
				/ totalNodeSize;
		Log.log("Intersection/union: %f", nodeInterRate);

		Log.log("Indirect edge matched: %d", indirectEdgeMatchCnt);
		Log.log("Pure Heuristic match: %d", pureHeuristicMatchCnt);
		Log.log("CallContinuation Match: %d", callContinuationMatchCnt);
		Log.log("Possible rewritten blocks: %d", possibleRewrites);

		Log.log();
	}

	public void dumpNodesRelationship(String fileName) {
		File file = new File(fileName);
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		PrintWriter pwRelationFile = null;

		try {
			pwRelationFile = new PrintWriter(fileName + ".relation");
			for (Node.Key leftKey : session.matchedNodes.getLeftKeySet()) {
				Node.Key rightKey = session.matchedNodes
						.getMatchByLeftKey(leftKey);
				pwRelationFile.println(leftKey + "->" + rightKey);
			}

			pwRelationFile.flush();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		if (pwRelationFile != null)
			pwRelationFile.close();
	}

	public static void dumpGraph(ModuleGraphCluster graph, String fileName) {
		Log.log("Dump the graph for " + graph + " to " + fileName);
		File file = new File(fileName);
		if (!file.getParentFile().exists()) {
			file.getParentFile().mkdirs();
		}
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

			// This file contains the analysis info for the graph
			pwNodeFile = new PrintWriter(fileName + ".node");
			Set<ExecutionNode> accessibleNodes = graph.searchAccessibleNodes();
			for (ExecutionNode n : graph.getGraphData().nodesByKey.values()) {
				if (!accessibleNodes.contains(n)) {
					pwNodeFile.println(n);
				}

			}

			pwDotFile.println("digraph runGraph {");
			int i = 0;
			for (ExecutionNode n : graph.getGraphData().nodesByKey.values()) {
				pwDotFile.println(i + "[label=\"" + n + "\"]");
				i++;

				List<Edge<ExecutionNode>> edges = n.getOutgoingEdges();
				for (Edge<ExecutionNode> e : edges) {
					String branchType;
					switch (e.getEdgeType()) {
						case INDIRECT:
							branchType = "i";
							break;
						case DIRECT:
							branchType = "d";
							break;
						case CALL_CONTINUATION:
							branchType = "c";
							break;
						case UNEXPECTED_RETURN:
							branchType = "u";
							break;
						case MODULE_ENTRY:
							branchType = "xk";
							break;
						case CROSS_CUSTOM_MODULE:
							branchType = "xc";
							break;
						default:
							branchType = "";
							break;
					}
					String edgeLabel = i + "->" + e.getToNode().getKey()
							+ "[label=\"" + branchType + "_";
					if (e.getEdgeType() == EdgeType.MODULE_ENTRY) {
						edgeLabel = edgeLabel
								+ e.getFromNode().getRelativeTag() + "->"
								+ e.getToNode().getRelativeTag() + "\"]";
					} else {
						edgeLabel = edgeLabel + e.getOrdinal() + "\"]";
					}
					pwDotFile.println(edgeLabel);
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
		Log.log("Finish dumping the graph for " + graph + ".");
	}

	public static void dumpHashCollision(ProcessExecutionGraph graph) {

	}

	public int getTotalNodeSize() {
		return totalNodeSize;
	}

	public float getSetInterRate() {
		return setInterRate;
	}
	
	void collectScoreRecord(List<Node> leftCandidates, Node rightNode,
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
				if (CrowdSafeTraceUtil.isTailNode(rightNode)) {
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
}
