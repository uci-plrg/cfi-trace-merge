package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import edu.uci.eecs.crowdsafe.analysis.data.graph.Node;

public class PairEdge {
	private Node parent, child;
	private boolean isDirect;
	private int ordinal;

	public Node getParent() {
		return parent;
	}

	public Node getChild() {
		return child;
	}

	public boolean getIsDirect() {
		return isDirect;
	}

	public int getOrdinal() {
		return ordinal;
	}

	public PairEdge(Node parent, Node child, boolean isDirect,
			int ordinal) {
		this.parent = parent;
		this.child = child;
		this.isDirect = isDirect;
		this.ordinal = ordinal;
	}
}
