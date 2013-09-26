package edu.uci.eecs.crowdsafe.merge.graph.tag;

import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterGraph;

public class ClusterTagMergeSession {

	public static ClusterGraph mergeTwoGraphs(ModuleGraphCluster<?> left, ModuleGraphCluster<?> right,
			ClusterTagMergeResults results) {
		ClusterTagMergeSession session = new ClusterTagMergeSession(left, right, results);
		ClusterTagMergeEngine engine = new ClusterTagMergeEngine(session);
		engine.mergeGraph();
		session.results.clusterMergeCompleted();
		return session.mergedGraph;
	}

	final ModuleGraphCluster<?> left;
	final ModuleGraphCluster<?> right;
	
	final ClusterTagMergedSubgraphs subgraphs = new ClusterTagMergedSubgraphs();

	final ClusterTagMergeResults results;

	public ClusterTagMergeSession(ModuleGraphCluster<?> left, ModuleGraphCluster<?> right,
			ClusterTagMergeResults results) {
		this.left = left;
		this.right = right;
		this.results = results;

		mergedGraph = new ClusterGraph(left.cluster);
	}

}
