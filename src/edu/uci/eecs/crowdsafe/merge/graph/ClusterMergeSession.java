package edu.uci.eecs.crowdsafe.merge.graph;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.merge.graph.PairNode.MatchType;

public class ClusterMergeSession {

	public static void mergeTwoGraphs(ModuleGraphCluster<?> left, ModuleGraphCluster<?> right, GraphMergeResults results,
			MergeDebugLog debugLog) {
		ClusterMergeSession session = new ClusterMergeSession(left, right, results, debugLog);

		GraphMergeEngine engine = new GraphMergeEngine(session);
		engine.mergeGraph();
		session.results.clusterMergeCompleted();
	}

	enum State {
		INITIALIZATION,
		ENTRY_POINTS,
		AD_HOC,
		FINALIZE
	}

	State state = State.INITIALIZATION;

	final GraphMergeTarget left;
	final GraphMergeTarget right;
	final ClusterGraph mergedGraph;

	final GraphMergeStatistics statistics;
	final GraphMergeResults results;
	final MergeDebugLog debugLog;

	final MatchedNodes matchedNodes;
	final GraphMatchState matchState;

	// The speculativeScoreList, which records the detail of the scoring of
	// all the possible cases
	final SpeculativeScoreList speculativeScoreList = new SpeculativeScoreList(this);

	final ContextMatchRecord contextRecord = new ContextMatchRecord();

	private final Map<Node<?>, Integer> scoresByLeftNode = new HashMap<Node<?>, Integer>();

	boolean hasConflict;

	final GraphMergeEngine engine = new GraphMergeEngine(this);

	ClusterMergeSession(ModuleGraphCluster<?> left, ModuleGraphCluster<?> right, GraphMergeResults results,
			MergeDebugLog debugLog) {
		this.left = new GraphMergeTarget(this, left);
		this.right = new GraphMergeTarget(this, right);
		this.results = results;
		results.beginCluster(this);
		this.debugLog = debugLog;
		debugLog.setSession(this);

		matchedNodes = new MatchedNodes(this);
		matchState = new GraphMatchState(this);
		statistics = new GraphMergeStatistics(this);
		mergedGraph = new ClusterGraph(left.cluster);
	}

	public void initializeMerge() {
		right.visitedEdges.clear();
		right.visitedAsUnmatched.clear();
		matchedNodes.clear();
		matchState.clear();
		speculativeScoreList.clear();
		statistics.reset();
		hasConflict = false;

		for (long hash : right.cluster.getEntryHashes()) {
			if (left.cluster.getEntryHashes().contains(hash)) {
				Node<?> leftNode = left.cluster.getEntryPoint(hash);
				Node<?> rightNode = right.cluster.getEntryPoint(hash);

				debugLog.debugCheck(leftNode);
				debugLog.debugCheck(rightNode);

				if (leftNode.hasCompatibleEdges(rightNode)) {
					matchState.enqueueMatch(new PairNode(leftNode, rightNode, MatchType.ENTRY_POINT));
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
		if (score < 7)
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
}
