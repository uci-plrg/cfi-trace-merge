package edu.uci.eecs.crowdsafe.merge.graph.hash;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashNodeMatch.MatchType;

public class ClusterHashMergeSession {

	public static ClusterGraph mergeTwoGraphs(ModuleGraphCluster<?> left, ModuleGraphCluster<?> right,
			ClusterHashMergeResults results, ClusterHashMergeDebugLog debugLog) {
		ClusterHashMergeSession session = new ClusterHashMergeSession(left, right, results, debugLog);
		return mergeTwoGraphs(session);
	}

	public static ClusterGraph mergeTwoGraphs(ModuleGraphCluster<?> left, ModuleGraphCluster<?> right,
			ContextMatchState.Evaluator matchEvaluator, ClusterHashMergeResults results,
			ClusterHashMergeDebugLog debugLog) {
		ClusterHashMergeSession session = new ClusterHashMergeSession(left, right, results, debugLog);
		session.contextRecord.setEvaluator(matchEvaluator);
		return mergeTwoGraphs(session);
	}

	private static ClusterGraph mergeTwoGraphs(ClusterHashMergeSession session) {
		ClusterHashMergeEngine engine = new ClusterHashMergeEngine(session);
		engine.mergeGraph();
		session.results.clusterMergeCompleted();
		return session.mergedGraphBuilder;
	}

	enum State {
		INITIALIZATION,
		ENTRY_POINTS,
		AD_HOC,
		FINALIZE
	}

	State state = State.INITIALIZATION;

	final ClusterHashMergeTarget left;
	final ClusterHashMergeTarget right;
	final ClusterGraph mergedGraphBuilder;

	final ClusterHashMergeStatistics statistics;
	final ClusterHashMergeResults results;
	final ClusterHashMergeDebugLog debugLog;

	final HashMatchedNodes matchedNodes;
	final ClusterHashMatchState matchState;

	final ContextMatchRecord contextRecord = new ContextMatchRecord();

	private final Map<Node<?>, Integer> scoresByLeftNode = new HashMap<Node<?>, Integer>();

	boolean hasConflict;

	final ClusterHashMergeEngine engine = new ClusterHashMergeEngine(this);

	ClusterHashMergeSession(ModuleGraphCluster<?> left, ModuleGraphCluster<?> right, ClusterHashMergeResults results,
			ClusterHashMergeDebugLog debugLog) {
		this.left = new ClusterHashMergeTarget(left);
		this.right = new ClusterHashMergeTarget(right);
		this.results = results;
		results.beginCluster(this);
		this.debugLog = debugLog;

		matchedNodes = new HashMatchedNodes(this);
		matchState = new ClusterHashMatchState(this);
		statistics = new ClusterHashMergeStatistics(this);
		mergedGraphBuilder = new ClusterGraph(left.cluster);
	}

	public void initializeMerge() {
		right.visitedEdges.clear();
		right.visitedAsUnmatched.clear();
		matchedNodes.clear();
		matchState.clear();
		statistics.reset();
		hasConflict = false;

		for (long hash : right.cluster.getEntryHashes()) {
			if (left.cluster.getEntryHashes().contains(hash)) {
				Node<?> leftNode = left.cluster.getEntryPoint(hash);
				Node<?> rightNode = right.cluster.getEntryPoint(hash);

				debugLog.debugCheck(leftNode);
				debugLog.debugCheck(rightNode);

				if (leftNode.hasCompatibleEdges(rightNode)) {
					matchState.enqueueMatch(new HashNodeMatch(leftNode, rightNode, MatchType.ENTRY_POINT));
					matchedNodes.addPair(leftNode, rightNode, 0);
					statistics.directMatch();
					continue;
				}
			}

			Node<?> rightEntryPoint = right.cluster.getEntryPoint(hash);
			matchState.enqueueUnmatch(rightEntryPoint);
			engine.addUnmatchedNode2Queue(rightEntryPoint);
		}
	}

	boolean acceptContext(Node<?> candidate) {
		int score = contextRecord.evaluate();
		if (score < 7) // TODO: should be configurable also
			return false;
		setScore(candidate, score);
		return true;
	}

	void setScore(Node<?> leftNode, int score) {
		scoresByLeftNode.put(leftNode, score);
	}

	int getScore(Node<?> leftNode) {
		Integer score = scoresByLeftNode.get(leftNode);
		if (score != null)
			return score;
		return 0;
	}

	boolean isMutuallyUnreachable(Node<?> node) {
		return right.cluster.getUnreachableNodes().contains(node) && left.cluster.getUnreachableNodes().contains(node);
	}
}
