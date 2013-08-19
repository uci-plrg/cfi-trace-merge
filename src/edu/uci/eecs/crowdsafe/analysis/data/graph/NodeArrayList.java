package edu.uci.eecs.crowdsafe.analysis.data.graph;

import java.util.ArrayList;

public class NodeArrayList extends ArrayList<Node> implements NodeList {
	@Override
	public NodeList copy(ProcessExecutionGraph containingGraph) {
		NodeArrayList copy = new NodeArrayList();
		for (Node node : this) {
			copy.add(new Node(containingGraph, node));
		}
		return copy;
	}

	@Override
	public boolean isSingleton() {
		return false;
	}
}
