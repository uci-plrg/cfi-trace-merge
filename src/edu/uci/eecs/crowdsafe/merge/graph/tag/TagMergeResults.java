package edu.uci.eecs.crowdsafe.merge.graph.tag;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.results.Graph;
import edu.uci.eecs.crowdsafe.graph.data.results.NodeResultsFactory;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeStrategy;
import edu.uci.eecs.crowdsafe.merge.graph.MergeResults;
import edu.uci.eecs.crowdsafe.merge.graph.results.TagMerge;

public class TagMergeResults implements MergeResults {

	private class Builder {
		final TagMerge.TagMergeResults.Builder results = TagMerge.TagMergeResults.newBuilder();
		final TagMerge.ModuleTagMerge.Builder merge = TagMerge.ModuleTagMerge.newBuilder();
		final TagMerge.Mismatch.Builder mismatch = TagMerge.Mismatch.newBuilder();
		final TagMerge.Subgraph.Builder subgraph = TagMerge.Subgraph.newBuilder();
		final NodeResultsFactory nodeFactory = new NodeResultsFactory();
		final Graph.Process.Builder fragment = Graph.Process.newBuilder();
	}

	private class ClusterResults {
		TagMergeSession session;

		ClusterResults(TagMergeSession session) {
			this.session = session;
		}

		void mergeCompleted() {
			Log.log("Match count %d, hash mismatch count %d", session.statistics.getAddedNodeCount(),
					session.statistics.hashMismatches.size());

			builder.merge.clear().setDistributionName(session.left.module.name);
			builder.merge.setMergedNodes(session.right.graph.getNodeCount());
			builder.merge.setMergedEdges(session.statistics.getMatchedEdgeCount()
					+ session.statistics.getAddedEdgeCount());
			builder.merge.setAddedNodes(session.statistics.getAddedNodeCount());
			builder.merge.setAddedEdges(session.statistics.getAddedEdgeCount());

			for (TagMergedSubgraphs.Subgraph subgraph : session.subgraphs.getSubgraphs()) {
				builder.subgraph.clear().setNodeCount(subgraph.getNodeCount());
				builder.subgraph.setBridgeCount(subgraph.getBridgeCount());
				builder.subgraph.setInstanceCount(subgraph.getInstanceCount());
				builder.merge.addAddedSubgraph(builder.subgraph.build());
			}

			for (int i = 0; i < session.statistics.hashMismatches.size(); i++) {
				builder.mismatch.clear().setLeft(
						builder.nodeFactory.buildNode(session.statistics.hashMismatches.left.get(i)));
				builder.mismatch
						.setRight(builder.nodeFactory.buildNode(session.statistics.hashMismatches.right.get(i)));
				builder.merge.addHashMismatch(builder.mismatch.build());
			}

			for (int i = 0; i < session.statistics.edgeMismatches.size(); i++) {
				builder.mismatch.clear().setLeft(
						builder.nodeFactory.buildNode(session.statistics.edgeMismatches.left.get(i)));
				builder.mismatch
						.setRight(builder.nodeFactory.buildNode(session.statistics.edgeMismatches.right.get(i)));
				builder.merge.addEdgeMismatch(builder.mismatch.build());
			}

			builder.results.addMerge(builder.merge.build());
			builder.fragment.addModule(session.mergeFragment.summarizeCurrentCluster());
			session = null;
		}
	}

	private final Builder builder = new Builder();
	private final Map<ApplicationModule, ClusterResults> resultsByCluster = new HashMap<ApplicationModule, ClusterResults>();

	private ClusterResults currentCluster = null;

	@Override
	public void setGraphSummaries(Graph.Process leftGraphSummary, Graph.Process rightGraphSummary) {
		builder.results.setLeft(leftGraphSummary);
		builder.results.setRight(rightGraphSummary);

		builder.fragment.setName(leftGraphSummary.getName() + " <merge fragment>");
		builder.fragment.setMetadata(leftGraphSummary.getMetadata());
	}

	@Override
	public TagMerge.TagMergeResults getResults() {
		builder.results.setMergeFragment(builder.fragment.build());
		return builder.results.build();
	}

	@Override
	public GraphMergeStrategy getStrategy() {
		return GraphMergeStrategy.TAG;
	}

	void beginCluster(TagMergeSession session) {
		currentCluster = new ClusterResults(session);
		resultsByCluster.put(session.left.module, currentCluster);

		Log.log("\n  === Merging cluster %s ===\n", session.left.module.name);
	}

	void clusterMergeCompleted() {
		currentCluster.mergeCompleted();
		currentCluster = null;
	}
}
