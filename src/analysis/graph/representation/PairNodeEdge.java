package analysis.graph.representation;

public class PairNodeEdge {
	private Node parentNode1;
	private Edge curNodeEdge;
	
	private Node parentNode2;
	
	public PairNodeEdge(Node parentNode1, Edge curNodeEdge, Node parentNode2) {
		this.parentNode1 = parentNode1;
		this.curNodeEdge = curNodeEdge;
		this.parentNode2 = parentNode2;
	}
	
	public Node getParentNode1() {
		return parentNode1;
	}

	public Edge getCurNodeEdge() {
		return curNodeEdge;
	}
	
	public Node getParentNode2() {
		return parentNode2;
	}
}
