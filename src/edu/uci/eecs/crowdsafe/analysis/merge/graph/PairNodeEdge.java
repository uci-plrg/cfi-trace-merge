package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import edu.uci.eecs.crowdsafe.analysis.data.graph.Edge;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;

/**
 * In this class, parentNode1 is from the graph1, which is fixed; and
 * parentNode2 is from graph2, which is traversed. curNodeEdge2 is the edge
 * which comes from parentNode2. When you use this class, you can assume that
 * parentNode1 and parentNode2 are matched and you are trying to match the
 * "toNode" of curNodeEdge2.
 * 
 * @author peizhaoo
 * 
 */
public class PairNodeEdge {
	private ExecutionNode parentNode1;
	private Edge curNodeEdge2;

	private ExecutionNode parentNode2;

	public PairNodeEdge(ExecutionNode parentNode1, Edge curNodeEdge2, ExecutionNode parentNode2) {
		this.parentNode1 = parentNode1;
		this.curNodeEdge2 = curNodeEdge2;
		this.parentNode2 = parentNode2;
	}

	public ExecutionNode getParentNode1() {
		return parentNode1;
	}

	public Edge getCurNodeEdge() {
		return curNodeEdge2;
	}

	public ExecutionNode getParentNode2() {
		return parentNode2;
	}

	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o.getClass() != PairNodeEdge.class)
			return false;
		PairNodeEdge pairNodeEdge = (PairNodeEdge) o;
		if (pairNodeEdge.parentNode1.equals(parentNode1)
				&& pairNodeEdge.parentNode2.equals(curNodeEdge2)
				&& pairNodeEdge.curNodeEdge2.equals(curNodeEdge2))
			return true;
		else
			return false;
	}

	public int hashCode() {
		return parentNode1.hashCode() << 5 ^ parentNode2.hashCode() << 5
				^ curNodeEdge2.hashCode();
	}
}
