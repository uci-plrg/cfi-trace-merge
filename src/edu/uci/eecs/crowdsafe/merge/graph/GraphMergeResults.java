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
		final Merge.UnmatchedNodeSummary.Builder unmatchedNodeSummary = Merge.UnmatchedNodeSummary.newBuilder();
		final Merge.TraceCompilationProfile.Builder traceProfile = Merge.TraceCompilationProfile.newBuilder();
		final Merge.MergeSummary.Builder summary = Merge.MergeSummary.newBuilder();
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

			builder.cluster.clear().setDistributionName(session.left.cluster.distribution.name);
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
			builder.cluster.setHashProfile(builder.traceProfile.build());

			builder.traceProfile.clear().setUnion(mergedGraphNodeCount); // simple node count of the entire merged graph
			builder.traceProfile.setIntersection(session.matchedNodes.size());
			builder.traceProfile.setLeft(session.left.cluster.getGraphData().nodesByKey.size());
			builder.traceProfile.setRight(session.right.cluster.getGraphData().nodesByKey.size());
			builder.cluster.setGraphProfile(builder.traceProfile.build());

			builder.traceProfile.clear().setUnion(mergedGraphNodeCount); // simple node count of the entire merged graph
			builder.traceProfile.setIntersection(hashIntersectionBlockCount);
			builder.traceProfile.setLeft(hashIntersectionLeftBlockCount);
			builder.traceProfile.setRight(hashIntersectionRightBlockCount);
			builder.cluster.setGraphWithinHashIntersection(builder.traceProfile.build());

			builder.summary.clear().setIndirectEdgesMatched(session.statistics.getIndirectEdgeMatchCount());
			builder.summary.setPureHeuristicMatches(session.statistics.getPureHeuristicMatchCount());
			builder.summary.setCallContinuationEdgesMatched(session.statistics.getCallContinuationMatchCount());
			builder.summary.setPossiblyRewrittenBlocks(session.statistics.getPossibleRewrites());
			builder.summary.setModuleRelativeTagMismatches(session.matchedNodes.HACK_getMismatchCount());
			builder.cluster.setMergeSummary(builder.summary.build());
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
