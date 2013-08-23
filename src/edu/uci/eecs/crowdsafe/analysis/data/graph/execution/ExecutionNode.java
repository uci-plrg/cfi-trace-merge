package edu.uci.eecs.crowdsafe.analysis.data.graph.execution;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.analysis.data.graph.Edge;
import edu.uci.eecs.crowdsafe.analysis.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.Node;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.InvalidGraphException;

/**
 * This is
 * 
 * @author peizhaoo
 * 
 */
public class ExecutionNode extends Node {

	public static class Key implements Node.Key {
		public static Key create(long tag, int tagVersion, ModuleInstance module) {
			return new Key(tag - module.start, tagVersion, module,
					MetaNodeType.NORMAL);
		}

		public final long relativeTag;

		public final int version;

		public final ModuleInstance module;

		private Key(long relativeTag, int tagVersion, ModuleInstance module,
				MetaNodeType type) {
			this.relativeTag = relativeTag;
			this.version = tagVersion;
			this.module = module;

			if ((type == MetaNodeType.NORMAL)
					&& ((relativeTag < 0L) || (relativeTag > (module.end - module.start)))) {
				throw new InvalidGraphException(
						"Relative tag 0x%x is outside the module's relative bounds [0-%d]",
						relativeTag, (module.end - module.start));
			}
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

		@Override
		public String toString() {
			return String.format("%s(0x%x-v%d)", module.unit.filename,
					relativeTag, version);
		}
	}

	private final Key key;

	private final long hash;

	private MetaNodeType metaNodeType;

	private List<Edge<ExecutionNode>> outgoingEdges = new ArrayList<Edge<ExecutionNode>>();

	private List<Edge<ExecutionNode>> incomingEdges = new ArrayList<Edge<ExecutionNode>>();

	public ExecutionNode(ModuleInstance module, MetaNodeType metaNodeType,
			long tag, int tagVersion, long hash) {
		Key key;
		switch (metaNodeType) {
			case CLUSTER_ENTRY:
			case CLUSTER_EXIT:
				key = new Key(hash, 0, module, metaNodeType);
				break;
			default:
				key = Key.create(tag, tagVersion, module);
		}
		this.key = key;
		this.metaNodeType = metaNodeType;
		this.hash = hash;
	}

	@Override
	public ExecutionNode.Key getKey() {
		return key;
	}

	public void setMetaNodeType(MetaNodeType metaNodeType) {
		this.metaNodeType = metaNodeType;
	}

	public String identify() {
		switch (metaNodeType) {
			case CLUSTER_ENTRY:
				return String.format("ClusterEntry(0x%x)", hash);
			case CLUSTER_EXIT:
				return String.format("ClusterExit(0x%x)", hash);
			default:
				return key.toString();
		}
	}

	public long getRelativeTag() {
		return key.relativeTag;
	}

	public int getTagVersion() {
		return key.version;
	}

	public ModuleInstance getModule() {
		return key.module;
	}

	public ExecutionNode changeHashCode(long newHash) {
		return new ExecutionNode(key.module, metaNodeType, key.module.start
				+ key.relativeTag, key.version, newHash);
	}

	public void addIncomingEdge(Edge<ExecutionNode> e) {
		incomingEdges.add(e);
	}

	public List<Edge<ExecutionNode>> getIncomingEdges() {
		return incomingEdges;
	}

	/**
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

	public Edge<ExecutionNode> getOutgoingEdge(ExecutionNode toNode) {
		for (Edge<ExecutionNode> edge : outgoingEdges) {
			if (edge.getToNode().getKey().equals(toNode.getKey()))
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
				&& metaNodeType == MetaNodeType.CLUSTER_ENTRY) {
			return (node.hash == hash);
		}

		return node.key.equals(key);
	}

	public int hashCode() {
		// if (metaNodeType == MetaNodeType.CLUSTER_ENTRY) {
		// return ((Long) hash).hashCode();
		// }
		// return key.hashCode() << 5 ^ new Long(hash).hashCode();
		return key.hashCode();
	}

	public String toString() {
		return identify();
	}
}