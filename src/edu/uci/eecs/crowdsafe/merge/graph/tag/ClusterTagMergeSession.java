package edu.uci.eecs.crowdsafe.merge.graph.tag;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;

public class ClusterTagMergeSession {

	class PendingEdgeQueue {
		final List<ClusterNode<?>> rightFromNodes = new ArrayList<ClusterNode<?>>();
		final List<Edge<?>> leftEdges = new ArrayList<Edge<?>>();

		int size() {
			return leftEdges.size();
		}
	}

	public static ClusterGraph mergeTwoGraphs(ModuleGraphCluster<?> left, ModuleGraphCluster<ClusterNode<?>> right,
			ClusterTagMergeResults results) {
		ClusterTagMergeSession session = new ClusterTagMergeSession(left, right, results);
		ClusterTagMergeEngine engine = new ClusterTagMergeEngine(session);
		engine.mergeGraph();
		session.results.clusterMergeCompleted();
		return session.right;
	}

	final ModuleGraphCluster<?> left;
	final ClusterGraph right;

	final ClusterTagMergedSubgraphs subgraphs = new ClusterTagMergedSubgraphs();

	final ClusterTagMergeStatistics statistics = new ClusterTagMergeStatistics();
	final ClusterTagMergeResults results;

	final PendingEdgeQueue edgeQueue = new PendingEdgeQueue();

	public ClusterTagMergeSession(ModuleGraphCluster<?> left, ModuleGraphCluster<ClusterNode<?>> right,
			ClusterTagMergeResults results) {
		this.left = left;
		this.right = new ClusterGraph(right);
		this.results = results;
		
		results.beginCluster(this);
	}
}
