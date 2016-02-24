package edu.uci.eecs.crowdsafe.merge.graph.hash;

import com.google.protobuf.GeneratedMessage;

import edu.uci.eecs.crowdsafe.graph.data.results.Graph;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeStrategy;
import edu.uci.eecs.crowdsafe.merge.graph.MergeResults;

public interface HashMergeResults extends MergeResults {

	void beginCluster(HashMergeSession session);

	void clusterMergeCompleted();

	public static class Empty implements HashMergeResults {

		public static Empty INSTANCE = new Empty();

		@Override
		public void setGraphSummaries(Graph.Process leftGraphSummary, Graph.Process rightGraphSummary) {
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
		public void beginCluster(HashMergeSession session) {
		}

		@Override
		public void clusterMergeCompleted() {
		}
	}
}
