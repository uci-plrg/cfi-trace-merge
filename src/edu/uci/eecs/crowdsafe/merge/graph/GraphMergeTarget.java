package edu.uci.eecs.crowdsafe.merge.graph;

import java.util.HashSet;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;

public class GraphMergeTarget {

	private final ClusterMergeSession session;

	final ModuleGraphCluster cluster;

	final Set<Node> visitedNodes = new HashSet<Node>();

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
