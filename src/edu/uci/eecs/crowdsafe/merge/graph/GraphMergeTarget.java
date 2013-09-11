package edu.uci.eecs.crowdsafe.merge.graph;

import java.util.HashSet;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;

public class GraphMergeTarget {

	private final ClusterMergeSession session;

	final ModuleGraphCluster cluster;

	final Set<Edge<? extends Node>> visitedEdges = new HashSet<Edge<? extends Node>>();
	final Set<Node> visitedAsUnmatched = new HashSet<Node>();

	GraphMergeTarget(ClusterMergeSession session, ModuleGraphCluster cluster) {
		this.session = session;
		this.cluster = cluster;
	}

	int getProcessId() {
		return cluster.getGraphData().containingGraph.dataSource.getProcessId();
	}

	String getProcessName() {
		return cluster.getGraphData().containingGraph.dataSource.getProcessName();
	}
}
