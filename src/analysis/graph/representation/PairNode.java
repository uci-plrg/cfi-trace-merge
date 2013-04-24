package analysis.graph.representation;


public class PairNode {
	private Node n1, n2;
	// The level of the BFS traverse
	public final int level;
	// Marker for whether this node should be matched anymore
	// It's only used in unmatched queue
	public final boolean neverMatched;
	
	public PairNode(Node n1, Node n2, int level) {
		this.n1 = n1;
		this.n2 = n2;
		this.level = level;
		this.neverMatched = false;
	}
	
	public PairNode(Node n1, Node n2, int level, boolean neverMatched) {
		this.n1 = n1;
		this.n2 = n2;
		this.level = level;
		this.neverMatched = neverMatched;
	}
	
	public String toString() {
		return n1.getIndex() + "<->" + n2.getIndex();
	}
	
	public Node getNode1() {
		return n1;
	}
	
	public Node getNode2() {
		return n2;
	}
}