package edu.uci.eecs.crowdsafe.merge.graph.hash;

import java.util.HashSet;
import java.util.Set;

import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;

public class HashMergeTarget {

	final ModuleGraph<? extends Node<?>> module;

	final Set<Edge<? extends Node<?>>> visitedEdges = new HashSet<Edge<? extends Node<?>>>();
	final Set<Node<?>> visitedAsUnmatched = new HashSet<Node<?>>();

	HashMergeTarget(ModuleGraph<? extends Node<?>> module) {
		this.module = module;
	}
}
