package analysis.graph.representation;

public class Edge {
	private Node node;
	private boolean isDirect;
	private int ordinal;
	
	public Node getNode() {
		return node;
	}
	
	public boolean getIsDirect() {
		return isDirect;
	}
	
	public int getOrdinal() {
		return ordinal;
	}

	public Edge(Node node, boolean isDirect, int ordinal) {
		this.node = node;
		this.isDirect = isDirect;
		this.ordinal = ordinal;
	}

	public Edge(Node node, int flag) {
		this.node = node;
		this.ordinal = flag % 256;
		isDirect = flag / 256 == 1;
	}

	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o.getClass() != Edge.class)
			return false;
		Edge e = (Edge) o;
		if (e.node.getIndex() == node.getIndex() && e.isDirect == isDirect
				&& e.ordinal == ordinal)
			return true;
		return false;

	}

	public int hashCode() {
		return node.getIndex();
	}
}
