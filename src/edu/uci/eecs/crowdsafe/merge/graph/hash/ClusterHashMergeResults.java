package edu.uci.eecs.crowdsafe.merge.graph.hash;

import com.google.protobuf.GeneratedMessage;

import edu.uci.eecs.crowdsafe.common.data.results.Graph.Process;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeStrategy;
import edu.uci.eecs.crowdsafe.merge.graph.MergeResults;

public interface ClusterHashMergeResults extends MergeResults {

	void beginCluster(ClusterHashMergeSession session);

	void clusterMergeCompleted();

	public static class Empty implements ClusterHashMergeResults {

		public static Empty INSTANCE = new Empty();

		@Override
		public void setGraphSummaries(Process leftGraphSummary, Process rightGraphSummary) {
		}

		@Override
		public GeneratedMessage getResults() {
			return null;
		}

		@Override
		public GraphMergeStrategy getStrategy() {
			return null;
		}

		@Override
		public void beginCluster(ClusterHashMergeSession session) {
		}

		@Override
		public void clusterMergeCompleted() {
		}
	}
}
