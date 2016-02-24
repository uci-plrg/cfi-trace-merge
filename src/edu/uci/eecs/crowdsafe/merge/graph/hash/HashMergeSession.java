package edu.uci.eecs.crowdsafe.merge.graph.hash;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ApplicationGraph;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashNodeMatch.MatchType;

public class HashMergeSession {

	public interface MergeEvaluator {
		void reset();

		boolean attemptMerge(HashMergeSession session);

		int evaluateMatch(ContextMatchState state);

		boolean acceptGraphs(HashMergeSession session);
	}

	public static class DefaultEvaluator implements HashMergeSession.MergeEvaluator {
		@Override
		public void reset() {
		}

		@Override
		public boolean attemptMerge(HashMergeSession session) {
			return true;
		}

		@Override
		public int evaluateMatch(ContextMatchState state) {
			if (state.complete) {
				if (state.reachedTargetDepth)
					return 10000;
				else if (state.reachedTargetDepth)
					return 1000;
				else
					return state.matchedNodeCount * 3;
			}

			if (state.reachedTargetDepth) {
				if (state.hasAmbiguity)
					return state.matchedNodeCount * 2;
				else
					return state.matchedNodeCount * 10;
			} else {
				if (state.hasAmbiguity)
					return state.matchedNodeCount / 2;
				else
					return state.matchedNodeCount;
			}
		}

		@Override
		public boolean acceptGraphs(HashMergeSession session) {
			return true;
		}
	}

	public static boolean evaluateTwoGraphs(ModuleGraph<?> left, ModuleGraph<?> right,
			MergeEvaluator mergeEvaluator, HashMergeDebugLog debugLog) {
		mergeEvaluator.reset();
		HashMergeSession session = new HashMergeSession(left, right,
				HashMergeResults.Empty.INSTANCE, debugLog);

		if (!mergeEvaluator.attemptMerge(session))
			return false;

		// Log.log("\nEvaluation merge: subgraphs of %d and %d nodes", left.getExecutableNodeCount(),
		// right.getExecutableNodeCount());
		session.contextRecord.setEvaluator(mergeEvaluator);
		HashMergeEngine engine = new HashMergeEngine(session);
		engine.mergeGraph();

		return mergeEvaluator.acceptGraphs(session);
	}

	public static ApplicationGraph mergeTwoGraphs(ModuleGraph<?> left, ModuleGraph<?> right,
			HashMergeResults results, MergeEvaluator mergeEvaluator, HashMergeDebugLog debugLog) {
		mergeEvaluator.reset();
		HashMergeSession session = new HashMergeSession(left, right, results, debugLog);
		return mergeTwoGraphs(session, mergeEvaluator);
	}

	public static ApplicationGraph mergeTwoGraphs(ModuleGraph<?> left, ModuleGraph<?> right,
			MergeEvaluator mergeEvaluator, HashMergeResults results, HashMergeDebugLog debugLog) {
		mergeEvaluator.reset();
		HashMergeSession session = new HashMergeSession(left, right, results, debugLog);
		session.contextRecord.setEvaluator(mergeEvaluator);
		return mergeTwoGraphs(session, mergeEvaluator);
	}

	private static ApplicationGraph mergeTwoGraphs(HashMergeSession session, MergeEvaluator mergeEvaluator) {
		if (!mergeEvaluator.attemptMerge(session))
			return null;

		HashMergeEngine engine = new HashMergeEngine(session);
		engine.mergeGraph();
		session.results.clusterMergeCompleted();
		if (mergeEvaluator.acceptGraphs(session)) {
			engine.buildMergedGraph();
			return session.mergedGraphBuilder;
		} else {
			return null;
		}
	}

	enum State {
		INITIALIZATION,
		ENTRY_POINTS,
		AD_HOC,
		FINALIZE
	}

	State state = State.INITIALIZATION;

	final HashMergeTarget left;
	final HashMergeTarget right;
	final ApplicationGraph mergedGraphBuilder;

	final HashMergeStatistics statistics;
	final HashMergeResults results;
	final HashMergeDebugLog debugLog;

	final HashMatchedNodes matchedNodes;
	final HashMatchState matchState;

	final ContextMatchRecord contextRecord = new ContextMatchRecord();

	private final Map<Node<?>, Integer> scoresByLeftNode = new HashMap<Node<?>, Integer>();

	boolean hasConflict;

	final HashMergeEngine engine = new HashMergeEngine(this);

	HashMergeSession(ModuleGraph<?> left, ModuleGraph<?> right, HashMergeResults results,
			HashMergeDebugLog debugLog) {
		this.left = new HashMergeTarget(left);
		this.right = new HashMergeTarget(right);
		this.results = results;
		results.beginCluster(this);
		this.debugLog = debugLog;

		matchedNodes = new HashMatchedNodes(this);
		matchState = new HashMatchState(this);
		statistics = new HashMergeStatistics(this);
		mergedGraphBuilder = new ApplicationGraph(String.format("merge of %s and %s", left.name, right.name), left.module);
	}

	public void initializeMerge() {
		right.visitedEdges.clear();
		right.visitedAsUnmatched.clear();
		matchedNodes.clear();
		matchState.clear();
		statistics.reset();
		hasConflict = false;

		for (long hash : right.module.getEntryHashes()) {
			if (left.module.getEntryHashes().contains(hash)) {
				Node<?> leftNode = left.module.getEntryPoint(hash);
				Node<?> rightNode = right.module.getEntryPoint(hash);

				debugLog.debugCheck(leftNode);
				debugLog.debugCheck(rightNode);

				if (leftNode.hasCompatibleEdges(rightNode)) {
					matchState.enqueueMatch(new HashNodeMatch(leftNode, rightNode, MatchType.ENTRY_POINT));
					matchedNodes.addPair(leftNode, rightNode, 0);
					statistics.directMatch();
					continue;
				}
			}

			Node<?> rightEntryPoint = right.module.getEntryPoint(hash);
			matchState.enqueueUnmatch(rightEntryPoint);
			engine.addUnmatchedNode2Queue(rightEntryPoint);
		}
	}

	public ModuleGraph<? extends Node<?>> getLeft() {
		return left.module;
	}

	public ModuleGraph<? extends Node<?>> getRight() {
		return right.module;
	}

	public HashMatchedNodes getMatchedNodes() {
		return matchedNodes;
	}

	public void setMatchEvaluator(HashMergeSession.MergeEvaluator evaluator) {
		contextRecord.setEvaluator(evaluator);
	}

	public boolean isFailed() {
		return contextRecord.isFailed();
	}

	boolean acceptContext(Node<?> candidate) {
		int score = contextRecord.evaluate();
		if (score < 0)
			return false;
		setScore(candidate, score);
		return true;
	}

	void setScore(Node<?> leftNode, int score) {
		scoresByLeftNode.put(leftNode, score);
	}

	public int getScore(Node<?> leftNode) {
		Integer score = scoresByLeftNode.get(leftNode);
		if (score != null)
			return score;
		return 0;
	}

	boolean isMutuallyUnreachable(Node<?> node) {
		return right.module.getUnreachableNodes().contains(node) && left.module.getUnreachableNodes().contains(node);
	}
}
