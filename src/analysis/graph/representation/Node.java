package analysis.graph.representation;

import java.util.ArrayList;

/**
 * This is 
 * @author peizhaoo
 *
 */
public class Node {
	private long tag, hash;

	private ArrayList<Edge> outgoingEdges = new ArrayList<Edge>();
	private boolean isVisited;
	// Index in the ArrayList<Node>
	private int index;
	// Divide the node into 3 groups
	private int fromWhichGraph = -1;
	// Meta data of the node
	private MetaNodeType metaNodeType;
	// Indicate if this node is reachable from the entry point
	private boolean reachable = false;
	
	// Incomming edges, just in case they might be needed
	private ArrayList<Edge> incomingEdges = new ArrayList<Edge>();
	
	public void addIncomingEdge(Edge e) {
		if (!incomingEdges.contains(e))
			incomingEdges.add(e);
	}
	
	public ArrayList<Edge> getIncomingEdges() {
		return incomingEdges;
	}
	
	/**
	 * 
	 * @return null for non-call node, edge for the first block of the calling procedure 
	 */
	public Edge getContinuationEdge() {
		int index = getContinuationEdgeIndex();
		if (index == -1) {
			return null;
		} else {
			return outgoingEdges.get(index); 
		}
	}
	
	private int getContinuationEdgeIndex() {
		for (int i = 0; i < outgoingEdges.size(); i++) {
			if (outgoingEdges.get(i).getEdgeType() == EdgeType.Call_Continuation) {
				return i;
			}
		}
		return -1;
	}

	int score = 0;
	
	public void addOutgoingEdge(Edge e) {
		if (!outgoingEdges.contains(e))
			outgoingEdges.add(e);
	}
	
	public ArrayList<Edge> getOutgoingEdges() {
		return outgoingEdges;
	}
	
	public MetaNodeType getMetaNodeType() {
		return this.metaNodeType;
	}
	
	public long getTag() {
		return tag;
	}
	
	public long getHash() {
		return hash;
	}
	
	/**
	 * This is added purely for the purpose of debugging
	 * We should never expose this to the outside world
	 */
	public void setHash(long hash) {
		this.hash = hash;
	}
	
	public void resetVisited() {
		isVisited = false;
	}
	private static int cnt = 0;
	public void setVisited() {
		isVisited = true;
		cnt++;
//		System.out.println(index);
//		if (cnt == 30146) {
//			System.out.println();
//		}
	}
	
	public boolean isVisited() {
		return isVisited;
	}
	
	public int getIndex() {
		return index;
	}
	
	public int getScore() {
		return score;
	}
	
	public void setScore(int score) {
		this.score = score;
	}
	
	public int getFromWhichGraph() {
		return fromWhichGraph;
	}
	
	public void setFromWhichGraph(int fromWhichGraph) {
		this.fromWhichGraph = fromWhichGraph;
	}
	
	public String getHashHex() {
		return "0x" + Long.toHexString(hash);
	}

	public String toString() {
		return "0x" + Long.toHexString(hash) + ":" + score + ":" + index;
	}

	// Not a deep copy, we don't care about edges...
	public Node(Node anotherNode) {
		this(anotherNode.tag, anotherNode.hash, anotherNode.index, MetaNodeType.NORMAl);
		this.score = anotherNode.score;
		this.fromWhichGraph = anotherNode.fromWhichGraph;
		this.metaNodeType = anotherNode.metaNodeType;
	}
	
	// Copy 'everything' except fromWhichGraph
	public Node(Node anotherNode, int fromWhichGraph) {
		this(anotherNode.tag, anotherNode.hash, anotherNode.index, MetaNodeType.NORMAl);
		this.score = anotherNode.score;
		this.fromWhichGraph = fromWhichGraph;
		this.metaNodeType = anotherNode.metaNodeType;
	}
	
	public Node(long hash, int index, MetaNodeType metaNodeType) {
		this.tag = -1;
		this.hash = hash;
		this.index = index;
		this.fromWhichGraph = -1;
		this.metaNodeType = metaNodeType;
	}

	public Node(long tag, long hash, int index, MetaNodeType metaNodeType) {
		this.tag = tag;
		this.hash = hash;
		this.index = index;
		this.isVisited = false;
		this.metaNodeType = metaNodeType;
	}

	public Node(long tag) {
		this.tag = tag;
		isVisited = false;
	}

	/**
	 * In a single execution, tag is the only identifier for the node
	 * This is particularly used in the initialization of the graph,
	 * where hashtables are needed
	 */
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o.getClass() != Node.class) {
			return false;
		}
		Node node = (Node) o;
		if (node.tag == tag)
			return true;
		else
			return false;
	}

	public int hashCode() {
		return ((Long) tag).hashCode() << 5 ^ ((Long) hash).hashCode();
	}

	public boolean isReachable() {
		return reachable;
	}

	public void setReachable(boolean reachable) {
		this.reachable = reachable;
	}
}