package analysis.graph.representation;


public class PairNode {
	private Node n1, n2;

	public PairNode(Node n1, Node n2) {
		this.n1 = n1;
		this.n2 = n2;
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