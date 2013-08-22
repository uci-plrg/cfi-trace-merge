package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import edu.uci.eecs.crowdsafe.analysis.data.graph.Node;

public class PairNode {
	private Node node1, node2;
	// The level of the BFS traverse
	public final int level;
	// Marker for whether this node should be matched anymore
	// It's only used in unmatched queue
	public final boolean neverMatched;

	public PairNode(Node node1, Node node2, int level) {
		this.node1 = node1;
		this.node2 = node2;
		this.level = level;
		this.neverMatched = false;
	}

	public PairNode(Node node1, Node node2, int level, boolean neverMatched) {
		this.node1 = node1;
		this.node2 = node2;
		this.level = level;
		this.neverMatched = neverMatched;
	}

	public String toString() {
		String node1Str = node1 == null ? "null" : node1.getKey().toString(), node2Str = node2 == null ? "null"
				: node2.getKey().toString();
		return node1Str + "<->" + node2Str;
	}

	public Node getLeftNode() {
		return node1;
	}

	public Node getRightNode() {
		return node2;
	}

	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o.getClass() != PairNode.class)
			return false;
		PairNode pairNode = (PairNode) o;
		if (pairNode.node1.equals(node1) && pairNode.node2.equals(node2))
			return true;
		else
			return false;
	}

	public int hashCode() {
		return node1.hashCode() << 5 ^ node2.hashCode();
	}
}