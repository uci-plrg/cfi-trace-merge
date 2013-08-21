package edu.uci.eecs.crowdsafe.analysis.data.graph;

import java.util.List;

public abstract class Node implements NodeList {

	public interface Key {
	}

	public abstract Key getKey();

	public abstract long getHash();
	
	public abstract MetaNodeType getType();

	public abstract Edge<? extends Node> getContinuationEdge();

	public abstract List<? extends Edge<? extends Node>> getIncomingEdges();

	public abstract List<? extends Edge<? extends Node>> getOutgoingEdges();

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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + getKey().hashCode();
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
		Node other = (Node) obj;
		return getKey().equals(other.getKey());
	}
}
