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
		public final long relativeTag;

		public final int version;

		public final ModuleInstance module;

		public Key(long tag, int tagVersion, ModuleInstance module) {
			this.relativeTag = (tag - module.start);
			this.version = tagVersion;
			this.module = module;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((module == null) ? 0 : module.hashCode());
			result = prime * result
					+ (int) (relativeTag ^ (relativeTag >>> 32));
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
			if (module == null) {
				if (other.module != null)
					return false;
			} else if (!module.equals(other.module))
				return false;
			if (relativeTag != other.relativeTag)
				return false;
			if (version != other.version)
				return false;
			return true;
		}
	}

	private final Key key;

	// Index in the ArrayList<Node>
	private int index;

	private final ModuleInstance module;

	private final long hash;

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
			ModuleInstance module, MetaNodeType metaNodeType, long tag,
			int tagVersion, long hash) {
		this.key = new Key(tag, tagVersion, module);
		this.containingGraph = containingGraph;
		this.module = module;
		this.metaNodeType = metaNodeType;
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

	// TODO: this is not a good identifier anymore! Need to use the key.
	public long getTag() {
		return (key.module == null ? 0L : key.module.start) + key.relativeTag;
	}

	public long getRelativeTag() {
		return key.relativeTag;
	}

	public int getTagVersion() {
		return key.version;
	}

	public ModuleInstance getModule() {
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
		return key.hashCode() << 5 ^ new Long(hash).hashCode();
	}

	public String toString() {
		if (metaNodeType != MetaNodeType.SIGNATURE_HASH) {
			return String.format("0x%x:0x%x-v%d", hash, getTag(), key.version);
		} else {
			return String.format("SIG: 0x%x", hash);
		}
	}
}