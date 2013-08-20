package edu.uci.eecs.crowdsafe.analysis.data.graph;

import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;

public interface NodeList {
	int size();

	boolean isSingleton();

	Node get(int index);
}
