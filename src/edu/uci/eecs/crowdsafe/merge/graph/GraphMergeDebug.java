package edu.uci.eecs.crowdsafe.merge.graph;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.graph.debug.DebugUtils;
import edu.uci.eecs.crowdsafe.merge.graph.debug.MatchingInstance;
import edu.uci.eecs.crowdsafe.merge.graph.debug.MatchingType;

public class GraphMergeDebug {

	static void initializeMerge(ModuleGraphCluster left,
			ModuleGraphCluster right) {
		// In the OUTPUT_SCORE debug mode, initialize the PrintWriter for this
		// merging process
		if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
			if (DebugUtils.getScorePW() != null) {
				DebugUtils.getScorePW().flush();
				DebugUtils.getScorePW().close();
			}
			String fileName = left.getGraphData().containingGraph.dataSource
					.getProcessName()
					+ ".score-"
					+ left.getGraphData().containingGraph.dataSource
							.getProcessId()
					+ "-"
					+ right.getGraphData().containingGraph.dataSource
							.getProcessId() + ".txt";
			DebugUtils.setScorePW(fileName);
		}
	}

	static void directMatch(PairNode pairNode, Edge<? extends Node> rightEdge,
			Node leftChild) {
		if (DebugUtils.debug) {
			MatchingType matchType = rightEdge.getEdgeType() == EdgeType.DIRECT ? MatchingType.DirectBranch
					: MatchingType.CallingContinuation;
			DebugUtils.debug_matchingTrace.addInstance(new MatchingInstance(
					pairNode.level, leftChild.getKey(), rightEdge.getToNode()
							.getKey(), matchType, rightEdge.getToNode()
							.getKey()));
		}

		if (DebugUtils.debug_decision(DebugUtils.PRINT_MATCHING_HISTORY)) {
			MatchingType matchType = rightEdge.getEdgeType() == EdgeType.DIRECT ? MatchingType.DirectBranch
					: MatchingType.CallingContinuation;
			Log.log(matchType + ": " + leftChild.getKey() + "<->"
					+ rightEdge.getToNode().getKey() + "(by "
					+ pairNode.getLeftNode().getKey() + "<->"
					+ rightEdge.getToNode().getKey() + ")");
		}
	}

	static void indirectMatch(PairNodeEdge nodeEdgePair,
			Edge<? extends Node> rightEdge, Node leftChild) {
		if (DebugUtils.debug) {
			DebugUtils.debug_matchingTrace.addInstance(new MatchingInstance(
					nodeEdgePair.level, leftChild.getKey(), rightEdge
							.getToNode().getKey(), MatchingType.IndirectBranch,
					nodeEdgePair.getRightParentNode().getKey()));
		}

		if (DebugUtils.debug_decision(DebugUtils.PRINT_MATCHING_HISTORY)) {
			// Print out indirect nodes that must be decided by
			// heuristic
			System.out.print("Indirect: " + leftChild.getKey() + "<->"
					+ rightEdge.getToNode().getKey() + "(by "
					+ nodeEdgePair.getLeftParentNode().getKey() + "<->"
					+ nodeEdgePair.getRightParentNode().getKey() + ")");
			Log.log();
		}
	}

	static void heuristicMatch(PairNode pairNode, Node leftChild) {
		if (DebugUtils.debug) {
			DebugUtils.debug_matchingTrace.addInstance(new MatchingInstance(
					pairNode.level, leftChild.getKey(), pairNode.getRightNode()
							.getKey(), MatchingType.PureHeuristic, null));
		}

		if (DebugUtils.debug_decision(DebugUtils.PRINT_MATCHING_HISTORY)) {
			// Print out indirect nodes that must be decided by
			// heuristic
			Log.log("PureHeuristic: " + leftChild.getKey() + "<->"
					+ pairNode.getRightNode().getKey() + "(by pure heuristic)");
		}
	}

	static void mergeCompleted() {
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
		}
	}
}
