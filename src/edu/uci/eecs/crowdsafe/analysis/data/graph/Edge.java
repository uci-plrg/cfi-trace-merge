package edu.uci.eecs.crowdsafe.analysis.data.graph;

public class Edge<NodeType extends Node> {
	private NodeType toNode;
	private EdgeType edgeType;
	private int ordinal;

	// Add this filed for debugging reason, cause it provides
	// more information when debugging
	private NodeType fromNode;

	public Edge(NodeType fromNode, NodeType toNode, EdgeType edgeType,
			int ordinal) {
		this.fromNode = fromNode;
		this.toNode = toNode;
		this.edgeType = edgeType;
		this.ordinal = ordinal;
	}

	public void setEdgeType(EdgeType edgeType) {
		this.edgeType = edgeType;
	}

	public NodeType getFromNode() {
		return fromNode;
	}

	public NodeType getToNode() {
		return toNode;
	}

	public EdgeType getEdgeType() {
		return edgeType;
	}

	public int getOrdinal() {
		return ordinal;
	}

	@SuppressWarnings("unchecked")
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o.getClass() != Edge.class)
			return false;
		Edge<NodeType> e = (Edge<NodeType>) o;
		if (e.fromNode.equals(fromNode) && e.toNode.equals(toNode)
				&& e.edgeType == edgeType && e.ordinal == ordinal)
			return true;
		return false;

	}

	public int hashCode() {
		return fromNode.hashCode() << 5 ^ toNode.hashCode();
	}

	public String toString() {
		return fromNode + "(" + edgeType + ")--" + ordinal + "-->" + toNode;
	}
}
