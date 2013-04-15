package analysis.graph.representation;


public class PairNode {
	private Node n1, n2;
	// the level of the BFS traverse
	public final int level;

	public PairNode(Node n1, Node n2, int level) {
		this.n1 = n1;
		this.n2 = n2;
		this.level = level;
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