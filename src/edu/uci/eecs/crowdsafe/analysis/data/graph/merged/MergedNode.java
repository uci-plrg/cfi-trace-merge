package edu.uci.eecs.crowdsafe.analysis.data.graph.merged;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.analysis.data.graph.Edge;
import edu.uci.eecs.crowdsafe.analysis.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.Node;

public class MergedNode extends Node {
	public static class Key implements Node.Key {
		final long hash;
		final int instanceId;

		public Key(long hash, int instanceId) {
			this.hash = hash;
			this.instanceId = instanceId;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (int) (hash ^ (hash >>> 32));
			result = prime * result + instanceId;
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
			if (hash != other.hash)
				return false;
			if (instanceId != other.instanceId)
				return false;
			return true;
		}
	}

	private final Key key;

	private final MetaNodeType type;

	private List<Edge<MergedNode>> outgoingEdges = new ArrayList<Edge<MergedNode>>();

	private List<Edge<MergedNode>> incomingEdges = new ArrayList<Edge<MergedNode>>();

	MergedNode(long hash, int instanceId, MetaNodeType type) {
		this.key = new Key(hash, instanceId);
		this.type = type;
	}

	@Override
	public MergedNode.Key getKey() {
		return key;
	}

	public MetaNodeType getType() {
		return type;
	}

	@Override
	public long getHash() {
		return 0;
	}

	@Override
	public Edge<? extends Node> getContinuationEdge() {
		return null;
	}

	@Override
	public List<Edge<MergedNode>> getIncomingEdges() {
		return incomingEdges;
	}

	@Override
	public List<Edge<MergedNode>> getOutgoingEdges() {
		return outgoingEdges;
	}

	public void addOutgoingEdge(Edge<MergedNode> edge) {
		outgoingEdges.add(edge);
	}

	public void addIncomingEdge(Edge<MergedNode> edge) {
		incomingEdges.add(edge);
	}
}
