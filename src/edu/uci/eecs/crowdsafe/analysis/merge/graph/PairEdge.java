package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;


public class PairEdge {
	private ExecutionNode parent, child;
	private boolean isDirect;
	private int ordinal;

	public ExecutionNode getParent() {
		return parent;
	}

	public ExecutionNode getChild() {
		return child;
	}

	public boolean getIsDirect() {
		return isDirect;
	}

	public int getOrdinal() {
		return ordinal;
	}

	public PairEdge(ExecutionNode parent, ExecutionNode child, boolean isDirect, int ordinal) {
		this.parent = parent;
		this.child = child;
		this.isDirect = isDirect;
		this.ordinal = ordinal;
	}
}
