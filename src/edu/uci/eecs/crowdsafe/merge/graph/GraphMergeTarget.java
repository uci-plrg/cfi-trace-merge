package edu.uci.eecs.crowdsafe.merge.graph;

import java.util.HashSet;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;

public class GraphMergeTarget {

	private final ClusterMergeSession session;

	final ModuleGraphCluster<? extends Node<?>> cluster;

	final Set<Edge<? extends Node<?>>> visitedEdges = new HashSet<Edge<? extends Node<?>>>();
	final Set<Node<?>> visitedAsUnmatched = new HashSet<Node<?>>();

	GraphMergeTarget(ClusterMergeSession session, ModuleGraphCluster<? extends Node<?>> cluster) {
		this.session = session;
		this.cluster = cluster;
	}
}
