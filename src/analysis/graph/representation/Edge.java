package analysis.graph.representation;

public class Edge {
	private Node node;
	private EdgeType edgeType;
	private int ordinal;
	
	public Node getNode() {
		return node;
	}
	
	public EdgeType getEdgeType() {
		return edgeType;
	}
	
	public int getOrdinal() {
		return ordinal;
	}

	public Edge(Node node, EdgeType edgeType, int ordinal) {
		this.node = node;
		this.edgeType = edgeType;
		this.ordinal = ordinal;
	}

	public Edge(Node node, int flag) {
		this.node = node;
		this.ordinal = flag % 256;
		edgeType = EdgeType.values()[flag / 256];
	}

	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o.getClass() != Edge.class)
			return false;
		Edge e = (Edge) o;
		if (e.node.getIndex() == node.getIndex() && e.edgeType == edgeType
				&& e.ordinal == ordinal)
			return true;
		return false;

	}

	public int hashCode() {
		return node.getIndex();
	}
}
