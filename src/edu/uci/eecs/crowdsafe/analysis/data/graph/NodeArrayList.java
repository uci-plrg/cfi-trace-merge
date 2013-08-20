package edu.uci.eecs.crowdsafe.analysis.data.graph;

import java.util.ArrayList;

import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;

public class NodeArrayList extends ArrayList<ExecutionNode> implements NodeList {

	@Override
	public boolean isSingleton() {
		return false;
	}
}
