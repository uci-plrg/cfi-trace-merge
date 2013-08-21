package edu.uci.eecs.crowdsafe.analysis.data.graph.merged;

import edu.uci.eecs.crowdsafe.analysis.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.Node;
import edu.uci.eecs.crowdsafe.analysis.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.analysis.data.graph.NodeList;

public class MergedClusterGraph {

	private final NodeHashMap nodesByHash = new NodeHashMap();

	public MergedNode addNode(long hash, MetaNodeType type) {
		NodeList collisions = nodesByHash.get(hash);
		int collisionCount = 0;
		if (collisions != null) {
			collisionCount = collisions.size();
		}

		MergedNode node = new MergedNode(hash, collisionCount, type);
		nodesByHash.add(hash, node);
		return node;
	}

	public MergedNode getNode(MergedNode.Key key) {
		NodeList nodes = nodesByHash.get(key.hash);
		if (nodes.isSingleton()) {
			return (MergedNode) nodes.get(0);
		} else {
			for (int i = 0; i < nodes.size(); i++) {
				Node node = nodes.get(i);
				if (node.getKey() == key)
					return (MergedNode) node;
			}
			return null;
		}
	}
}
