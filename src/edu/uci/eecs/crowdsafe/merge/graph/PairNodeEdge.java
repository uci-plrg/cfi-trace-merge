package edu.uci.eecs.crowdsafe.merge.graph;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;

/**
 * In this class, parentNode1 is from the graph1, which is fixed; and parentNode2 is from graph2, which is traversed.
 * curNodeEdge2 is the edge which comes from parentNode2. When you use this class, you can assume that parentNode1 and
 * parentNode2 are matched and you are trying to match the "toNode" of curNodeEdge2.
 * 
 * @author peizhaoo
 * 
 */
public class PairNodeEdge {
	private Node leftParentNode;
	private Edge<? extends Node> rightEdge;

	private Node rightParentNode;

	public PairNodeEdge(Node leftParentNode, Edge<? extends Node> rightEdge,
			Node rightParentNode) {
		this.leftParentNode = leftParentNode;
		this.rightEdge = rightEdge;
		this.rightParentNode = rightParentNode;
	}

	public Node getLeftParentNode() {
		return leftParentNode;
	}

	public Edge<? extends Node> getRightEdge() {
		return rightEdge;
	}

	public Node getRightParentNode() {
		return rightParentNode;
	}

	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o.getClass() != PairNodeEdge.class)
			return false;
		PairNodeEdge pairNodeEdge = (PairNodeEdge) o;
		if (pairNodeEdge.leftParentNode.equals(leftParentNode)
				&& pairNodeEdge.rightParentNode.equals(rightEdge)
				&& pairNodeEdge.rightEdge.equals(rightEdge))
			return true;
		else
			return false;
	}

	public int hashCode() {
		return leftParentNode.hashCode() << 5 ^ rightParentNode.hashCode() << 5
				^ rightEdge.hashCode();
	}
}
