package edu.uci.eecs.crowdsafe.merge.graph.hash;

import java.util.HashSet;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;

public class ClusterHashMergeTarget {

	final ModuleGraphCluster<? extends Node<?>> cluster;

	final Set<Edge<? extends Node<?>>> visitedEdges = new HashSet<Edge<? extends Node<?>>>();
	final Set<Node<?>> visitedAsUnmatched = new HashSet<Node<?>>();

	ClusterHashMergeTarget(ModuleGraphCluster<? extends Node<?>> cluster) {
		this.cluster = cluster;
	}
}
