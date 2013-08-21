package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import java.util.HashSet;
import java.util.Set;

import edu.uci.eecs.crowdsafe.analysis.data.graph.Node;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ModuleGraphCluster;

public class GraphMergeTarget {

	private final GraphMergeSession session;

	final ModuleGraphCluster cluster;

	final Set<Node> visitedNodes = new HashSet<Node>();

	GraphMergeTarget(GraphMergeSession session, ModuleGraphCluster cluster) {
		this.session = session;
		this.cluster = cluster;
	}

	int getProcessId() {
		return cluster.getGraphData().containingGraph.dataSource.getProcessId();
	}

	String getProcessName() {
		return cluster.getGraphData().containingGraph.dataSource
				.getProcessName();
	}
}
