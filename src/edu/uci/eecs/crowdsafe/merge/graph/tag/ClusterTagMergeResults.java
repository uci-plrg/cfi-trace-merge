package edu.uci.eecs.crowdsafe.merge.graph.tag;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.results.Graph;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.graph.MergeResults;
import edu.uci.eecs.crowdsafe.merge.graph.results.TagMerge;

public class ClusterTagMergeResults implements MergeResults {

	private class Builder {
		final TagMerge.TagMergeResults.Builder results = TagMerge.TagMergeResults.newBuilder();

	}

	private class ClusterResults {
		ClusterTagMergeSession session;

		ClusterResults(ClusterTagMergeSession session) {
			this.session = session;
		}

		void mergeCompleted() {

		}
	}

	private final Builder builder = new Builder();
	private final Map<AutonomousSoftwareDistribution, ClusterResults> resultsByCluster = new HashMap<AutonomousSoftwareDistribution, ClusterResults>();

	private ClusterResults currentCluster = null;

	@Override
	public void setGraphSummaries(Graph.Process leftGraphSummary, Graph.Process rightGraphSummary) {
		builder.results.setLeft(leftGraphSummary);
		builder.results.setRight(rightGraphSummary);
	}

	@Override
	public TagMerge.TagMergeResults getResults() {
		return builder.results.build();
	}

	void beginCluster(ClusterTagMergeSession session) {
		currentCluster = new ClusterResults(session);
		resultsByCluster.put(session.left.cluster.cluster, currentCluster);

		Log.log("\n  === Merging cluster %s ===\n", session.left.cluster.cluster.name);
	}

	void clusterMergeCompleted() {
		currentCluster.mergeCompleted();
		currentCluster = null;
	}
}
