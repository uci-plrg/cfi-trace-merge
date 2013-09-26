package edu.uci.eecs.crowdsafe.merge.graph.hash;

import edu.uci.eecs.crowdsafe.common.data.graph.Node;

public class HashNodeMatch {
	public enum MatchType {
		ENTRY_POINT,
		DIRECT_BRANCH,
		INDIRECT_BRANCH,
		HEURISTIC
	}

	private Node<?> leftNode, rightNode;
	public final MatchType type;

	// The level of the BFS traverse

	public HashNodeMatch(Node<?> left, Node<?> right, MatchType type) {
		this.leftNode = left;
		this.rightNode = right;
		this.type = type;
	}

	public Node<?> getLeftNode() {
		return leftNode;
	}

	public Node<?> getRightNode() {
		return rightNode;
	}

	public boolean isValid() {
		return !leftNode.isModuleRelativeMismatch(rightNode);
	}

	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o.getClass() != HashNodeMatch.class)
			return false;
		HashNodeMatch pairNode = (HashNodeMatch) o;
		if (pairNode.leftNode.equals(leftNode) && pairNode.rightNode.equals(rightNode))
			return true;
		else
			return false;
	}

	public int hashCode() {
		return leftNode.hashCode() << 5 ^ rightNode.hashCode();
	}

	public String toString() {
		String node1Str = leftNode == null ? "null" : leftNode.getKey().toString(), node2Str = rightNode == null ? "null"
				: rightNode.getKey().toString();
		return node1Str + "<->" + node2Str;
	}
}