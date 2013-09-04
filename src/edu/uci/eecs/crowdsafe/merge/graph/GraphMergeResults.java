package edu.uci.eecs.crowdsafe.merge.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.data.results.Statistics;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.graph.data.results.Merge;
import edu.uci.eecs.crowdsafe.merge.util.AnalysisUtil;

public class GraphMergeResults {

	private static class Builder {
		final Merge.MergeResults.Builder results = Merge.MergeResults.newBuilder();
		final Merge.ClusterMerge.Builder cluster = Merge.ClusterMerge.newBuilder();
		final Merge.ClusterMerge.UnmatchedNodeSummary.Builder unmatchedNodeSummary = Merge.ClusterMerge.UnmatchedNodeSummary
				.newBuilder();
		final Merge.ClusterMerge.TraceCompilationProfile.Builder traceProfile = Merge.ClusterMerge.TraceCompilationProfile
				.newBuilder();
		final Merge.ClusterMerge.MergeSummary.Builder summary = Merge.ClusterMerge.MergeSummary.newBuilder();
		final Statistics.IntegerStatistic.Builder integer = Statistics.IntegerStatistic.newBuilder();
		final Statistics.Ratio.Builder ratio = Statistics.Ratio.newBuilder();
	}

	private class ClusterResults {
		private ClusterMergeSession session;

		private int hashUnionSize = 0;
		private int hashIntersectionSize = 0;
		private int hashIntersectionBlockCount = 0;
		private int hashIntersectionLeftBlockCount = 0;
		private int hashIntersectionRightBlockCount = 0;
		private float hashIntersectionRatio = 0f;

		private int mergedGraphNodeCount = 0;
		private float nodeIntersectionRatio = 0f;

		ClusterResults(ClusterMergeSession session) {
			this.session = session;
		}

		void mergeCompleted() {
			computeResults();
			reportUnmatchedNodes();
			outputMergedGraphInfo();

			builder.results.addCluster(builder.cluster.build());
		}

		private void computeResults() {
			Set<Long> hashIntersection = AnalysisUtil.intersection(
					session.left.cluster.getGraphData().nodesByHash.keySet(),
					session.right.cluster.getGraphData().nodesByHash.keySet());
			Set<Long> hashUnion = AnalysisUtil.union(session.left.cluster.getGraphData().nodesByHash.keySet(),
					session.right.cluster.getGraphData().nodesByHash.keySet());
			hashIntersectionSize = hashIntersection.size();
			hashUnionSize = hashUnion.size();
			hashIntersectionRatio = hashIntersectionSize / (float) hashUnionSize;

			for (Long hash : hashIntersection) {
				hashIntersectionBlockCount += session.mergedGraph.nodesByHash.get(hash).size();
				hashIntersectionLeftBlockCount += session.left.cluster.getGraphData().nodesByHash.get(hash).size();
				hashIntersectionRightBlockCount += session.right.cluster.getGraphData().nodesByHash.get(hash).size();
			}

			mergedGraphNodeCount = session.mergedGraph.nodesByHash.getNodeCount();
			nodeIntersectionRatio = session.matchedNodes.size() / (float) mergedGraphNodeCount;
		}

		private void reportUnmatchedNodes() {
			reportUnmatchedNodes(session.left.cluster, session.right.cluster, "left");
			builder.cluster.setLeftUnmatched(builder.unmatchedNodeSummary.build());

			reportUnmatchedNodes(session.right.cluster, session.left.cluster, "right");
			builder.cluster.setRightUnmatched(builder.unmatchedNodeSummary.build());
		}

		private void reportUnmatchedNodes(ModuleGraphCluster cluster, ModuleGraphCluster oppositeCluster, String side) {
			Set<Node.Key> unmatchedNodes = new HashSet<Node.Key>(cluster.getGraphData().nodesByKey.keySet());
			unmatchedNodes.removeAll(session.matchedNodes.getLeftKeySet());
			int totalUnmatchedCount = unmatchedNodes.size();
			int unreachableUnmatchedCount = 0;
			for (Node unreachable : cluster.getUnreachableNodes()) {
				unmatchedNodes.remove(unreachable.getKey());
				unreachableUnmatchedCount++;
			}
			int hashExclusionCount = 0;
			for (Node.Key unmatchedKey : new ArrayList<Node.Key>(unmatchedNodes)) {
				Node unmatchedNode = cluster.getGraphData().nodesByKey.get(unmatchedKey);
				if (!oppositeCluster.getGraphData().nodesByHash.keySet().contains(unmatchedNode.getHash())) {
					unmatchedNodes.remove(unmatchedKey);
					hashExclusionCount++;
				}
			}

			builder.unmatchedNodeSummary.clear().setNodeCount(totalUnmatchedCount);
			builder.unmatchedNodeSummary.setEligibleNodeCount(unmatchedNodes.size());
			builder.unmatchedNodeSummary.setUnreachableNodeCount(unreachableUnmatchedCount);
			builder.unmatchedNodeSummary.setHashExclusiveNodeCount(hashExclusionCount);
		}

		private void outputMergedGraphInfo() {
			builder.traceProfile.clear().setUnion(hashUnionSize);
			builder.traceProfile.setIntersection(hashIntersectionSize);
			builder.traceProfile.setLeft(session.left.cluster.getGraphData().nodesByHash.keySet().size());
			builder.traceProfile.setRight(session.right.cluster.getGraphData().nodesByHash.keySet().size());
			builder.traceProfile.setLeftExclusive(session.left.cluster.getGraphData().nodesByHash.keySet().size()
					- hashIntersectionSize);
			builder.traceProfile.setRightExclusive(session.right.cluster.getGraphData().nodesByHash.keySet().size()
					- hashIntersectionSize);
			builder.cluster.setHashProfile(builder.traceProfile.build());

			builder.traceProfile.clear().setUnion(mergedGraphNodeCount);
			builder.traceProfile.setIntersection(session.matchedNodes.size());
			builder.traceProfile.setLeft(session.left.cluster.getGraphData().nodesByKey.size());
			builder.traceProfile.setRight(session.right.cluster.getGraphData().nodesByKey.size());
			builder.traceProfile.setLeftExclusive(session.left.cluster.getGraphData().nodesByKey.size()
					- session.matchedNodes.size());
			builder.traceProfile.setRightExclusive(session.right.cluster.getGraphData().nodesByKey.size()
					- session.matchedNodes.size());
			builder.cluster.setHashProfile(builder.traceProfile.build());

			builder.summary.clear().setId(0);
			builder.summary.setName("Merge Potential");

			builder.ratio.clear().setId("p-hash").setName("Hash Potential");
			builder.ratio.setNumerator(hashIntersectionBlockCount).setDenominator(mergedGraphNodeCount);
			builder.summary.addRatio(builder.ratio.build());

			builder.ratio.clear().setId("t-left-int").setName("Left node intersection / total");
			builder.ratio.setNumerator(session.matchedNodes.size()).setDenominator(
					session.left.cluster.getGraphData().nodesByKey.size());
			builder.summary.addRatio(builder.ratio.build());

			builder.ratio.clear().setId("p-left-int").setName("Left node intersection / hash potential");
			builder.ratio.setNumerator(session.matchedNodes.size()).setDenominator(hashIntersectionLeftBlockCount);
			builder.summary.addRatio(builder.ratio.build());

			builder.ratio.clear().setId("t-right-int").setName("Right node intersection / total");
			builder.ratio.setNumerator(session.matchedNodes.size()).setDenominator(
					session.right.cluster.getGraphData().nodesByKey.size());
			builder.summary.addRatio(builder.ratio.build());

			builder.ratio.clear().setId("p-right-int").setName("Right node intersection / hash potential");
			builder.ratio.setNumerator(session.matchedNodes.size()).setDenominator(hashIntersectionRightBlockCount);
			builder.summary.addRatio(builder.ratio.build());

			builder.ratio.clear().setId("t-merged-int").setName("Merged node intersection / union");
			builder.ratio.setNumerator(session.matchedNodes.size()).setDenominator(mergedGraphNodeCount);
			builder.summary.addRatio(builder.ratio.build());

			builder.ratio.clear().setId("p-merged-int").setName("Merged node intersection / hash potential");
			builder.ratio.setNumerator(session.matchedNodes.size()).setDenominator(hashIntersectionBlockCount);
			builder.summary.addRatio(builder.ratio.build());

			builder.integer.clear().setId("ind-match").setName("Indirect edges matched");
			builder.integer.setValue(session.statistics.getIndirectEdgeMatchCount());
			builder.summary.addIntStat(builder.integer.build());

			builder.integer.clear().setId("heur-match").setName("Pure heuristic node matches");
			builder.integer.setValue(session.statistics.getPureHeuristicMatchCount());
			builder.summary.addIntStat(builder.integer.build());

			builder.integer.clear().setId("cc-match").setName("Call continuation edges matched");
			builder.integer.setValue(session.statistics.getCallContinuationMatchCount());
			builder.summary.addIntStat(builder.integer.build());

			builder.integer.clear().setId("rewrites").setName("Possible rewrites");
			builder.integer.setValue(session.statistics.getPossibleRewrites());
			builder.summary.addIntStat(builder.integer.build());

			builder.integer.clear().setId("miss-count").setName("Mismatches by module-relative tag");
			builder.integer.setValue(session.matchedNodes.HACK_getMismatchCount());
			builder.summary.addIntStat(builder.integer.build());

			builder.cluster.addMergeSummary(builder.summary.build());
		}
	}

	private final Builder builder = new Builder();
	private final Map<AutonomousSoftwareDistribution, ClusterResults> resultsByCluster = new HashMap<AutonomousSoftwareDistribution, ClusterResults>();

	private ClusterResults currentCluster = null;

	public GraphMergeResults(ProcessExecutionGraph leftGraph, ProcessExecutionGraph rightGraph) {
		builder.results.setLeft(leftGraph.summarizeProcess());
		builder.results.setRight(rightGraph.summarizeProcess());
	}
	
	Merge.MergeResults getResults() {
		return builder.results.build();
	}

	void beginCluster(ClusterMergeSession session) {
		currentCluster = new ClusterResults(session);
		resultsByCluster.put(session.left.cluster.distribution, currentCluster);

		Log.log("\n  === Merging cluster %s ===", session.left.cluster.distribution.name);
	}

	void clusterMergeCompleted() {
		currentCluster.mergeCompleted();
		currentCluster = null;
	}
}
