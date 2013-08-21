package edu.uci.eecs.crowdsafe.analysis.data.graph.execution;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.analysis.data.graph.Edge;
import edu.uci.eecs.crowdsafe.analysis.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.Node;

/**
 * This is
 * 
 * @author peizhaoo
 * 
 */
public class ExecutionNode extends Node {

	public static class Key implements Node.Key {
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

	private final long hash;

	private final Key key;

	private MetaNodeType metaNodeType;

	private List<Edge<ExecutionNode>> outgoingEdges = new ArrayList<Edge<ExecutionNode>>();

	private List<Edge<ExecutionNode>> incomingEdges = new ArrayList<Edge<ExecutionNode>>();

	// TODO: what's this for? Try to normalize this.
	private final ProcessExecutionGraph containingGraph;

	// TODO: is processing state necessary on a Node?
	private boolean isVisited = false;
	// Indicate if this node is reachable from the entry point
	private boolean reachable = false;

	public ExecutionNode(ProcessExecutionGraph containingGraph,
			MetaNodeType metaNodeType, long tag, int tagVersion, long hash) {
		this.containingGraph = containingGraph;
		this.module = containingGraph.getModules().getModule(tag);
		this.metaNodeType = metaNodeType;
		this.key = new Key(tag, tagVersion);
		this.hash = hash;
	}

	@Override
	public ExecutionNode.Key getKey() {
		return key;
	}

	public ProcessExecutionGraph getContainingGraph() {
		return containingGraph;
	}

	public void setMetaNodeType(MetaNodeType metaNodeType) {
		this.metaNodeType = metaNodeType;
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

	public void addIncomingEdge(Edge<ExecutionNode> e) {
		incomingEdges.add(e);
	}

	public List<Edge<ExecutionNode>> getIncomingEdges() {
		return incomingEdges;
	}

	/**
	 * 
	 * @return null for non-call node, edge for the first block of the calling procedure
	 */
	public Edge<ExecutionNode> getContinuationEdge() {
		int index = getContinuationEdgeIndex();
		if (index == -1) {
			return null;
		} else {
			return outgoingEdges.get(index);
		}
	}

	private int getContinuationEdgeIndex() {
		for (int i = 0; i < outgoingEdges.size(); i++) {
			if (outgoingEdges.get(i).getEdgeType() == EdgeType.CALL_CONTINUATION) {
				return i;
			}
		}
		return -1;
	}

	public void addOutgoingEdge(Edge<ExecutionNode> e) {
		outgoingEdges.add(e);
	}

	public List<Edge<ExecutionNode>> getOutgoingEdges() {
		return outgoingEdges;
	}

	public Edge<ExecutionNode> getOutgoingEdge(ExecutionNode node) {
		for (Edge<ExecutionNode> edge : outgoingEdges) {
			if (edge.getToNode().getTag() == node.getTag())
				return edge;
		}

		return null;
	}

	public MetaNodeType getType() {
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
		if (o.getClass() != ExecutionNode.class) {
			return false;
		}
		ExecutionNode node = (ExecutionNode) o;
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
			return "0x" + Long.toHexString(hash) + ":0x"
					+ Long.toHexString(key.tag) + "-v" + key.version;
		} else {
			return "SIG: 0x" + Long.toHexString(hash);
		}
	}
}