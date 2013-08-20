package edu.uci.eecs.crowdsafe.analysis.data.graph;

import java.util.ArrayList;

/**
 * This is
 * 
 * @author peizhaoo
 * 
 */
public class Node implements NodeList {
	
	public static class Key {
		public final long tag;

		public final int version;

		public Key(long tag, int tagVersion) {
			this.tag = tag;
			this.version = tagVersion;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (int) (tag ^ (tag >>> 32));
			result = prime * result + version;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Key other = (Key) obj;
			if (tag != other.tag)
				return false;
			if (version != other.version)
				return false;
			return true;
		}
	}

	// Index in the ArrayList<Node>
	private int index;

	private final ModuleDescriptor module;

	private final Key key;

	private final long hash;

	private MetaNodeType metaNodeType;

	private ArrayList<Edge> outgoingEdges = new ArrayList<Edge>();

	private ArrayList<Edge> incomingEdges = new ArrayList<Edge>();

	// TODO: what's this for? Try to normalize this.
	private final ProcessExecutionGraph containingGraph;

	// TODO: is processing state necessary on a Node?
	private boolean isVisited = false;
	// Indicate if this node is reachable from the entry point
	private boolean reachable = false;

	// !!!Not a deep copy, we don't copy edges...
	// TODO: only for MODULE_BOUNDARY nodes--is this value used now?
	/**
	 * <pre>
	public Node(ProcessExecutionGraph containingGraph, Node anotherNode) {
		this(containingGraph, anotherNode.tag, anotherNode.hash,
				MetaNodeType.NORMAL);
		this.index = anotherNode.index;
		this.score = anotherNode.score;
		this.metaNodeType = anotherNode.metaNodeType;
	}
	 */

	public Node(ProcessExecutionGraph containingGraph,
			MetaNodeType metaNodeType, long tag, int tagVersion, long hash) {
		this.containingGraph = containingGraph;
		this.module = containingGraph.getModules().getModule(tag);
		this.metaNodeType = metaNodeType;
		this.key = new Key(tag, tagVersion);
		this.hash = hash;
	}

	public ProcessExecutionGraph getContainingGraph() {
		return containingGraph;
	}

	public void setMetaNodeType(MetaNodeType metaNodeType) {
		this.metaNodeType = metaNodeType;
	}

	@Override
	public Node get(int index) {
		return this;
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	@Override
	public NodeList copy(ProcessExecutionGraph containingGraph) {
		// TODO: won't this be the wrong tag for the new graph?
		return new Node(containingGraph, metaNodeType, key.tag, key.version,
				hash);
	}

	public long getTag() {
		return key.tag;
	}

	public long getRelativeTag() {
		if (module == null)
			return key.tag;
		return key.tag - module.beginAddr;
	}
	
	public int getTagVersion() {
		return key.version;
	}
	
	public ModuleDescriptor getModule() {
		return module;
	}

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
			if (outgoingEdges.get(i).getEdgeType() == EdgeType.CallContinuation) {
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

	public Edge getOutgoingEdge(Node node) {
		for (Edge edge : outgoingEdges) {
			if (edge.getToNode().getTag() == node.getTag())
				return edge;
		}

		return null;
	}

	public MetaNodeType getMetaNodeType() {
		return this.metaNodeType;
	}

	public long getHash() {
		return hash;
	}

	public void resetVisited() {
		isVisited = false;
	}

	public void setVisited() {
		isVisited = true;
	}

	public boolean isVisited() {
		return isVisited;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		this.score = score;
	}

	public String getHashHex() {
		return "0x" + Long.toHexString(hash);
	}

	public boolean isReachable() {
		return reachable;
	}

	public void setReachable(boolean reachable) {
		this.reachable = reachable;
	}

	/**
	 * In a single execution, tag combined with the version number is the only identifier for the normal nodes. This is
	 * particularly used in the initialization of the graph, where hashtables are needed.
	 * 
	 * For signature nodes, the node should be empty if they are both signature nodes and they have the same hash
	 * signature.
	 */
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o.getClass() != Node.class) {
			return false;
		}
		Node node = (Node) o;
		if (node.metaNodeType == metaNodeType
				&& metaNodeType == MetaNodeType.SIGNATURE_HASH) {
			return (node.hash == hash);
		}

		return (node.key.equals(key) && node.containingGraph == containingGraph);
	}

	public int hashCode() {
		if (metaNodeType == MetaNodeType.SIGNATURE_HASH) {
			return ((Long) hash).hashCode();
		}
		return new Long(key.tag).hashCode() << 5 ^ new Long(hash).hashCode();
	}

	public String toString() {
		if (metaNodeType != MetaNodeType.SIGNATURE_HASH) {
			return "0x" + Long.toHexString(hash) + ":" + key.tag + "v"
					+ key.version;
		} else {
			return "SIG: 0x" + Long.toHexString(hash);
		}
	}
}