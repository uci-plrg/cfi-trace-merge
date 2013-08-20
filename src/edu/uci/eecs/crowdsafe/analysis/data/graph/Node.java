package edu.uci.eecs.crowdsafe.analysis.data.graph;

import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;

public abstract class Node implements NodeList {

	@Override
	public Node get(int index) {
		return this;
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}
}
