package edu.uci.eecs.crowdsafe.merge.graph.data;

import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.common.data.graph.NodeList;

public class MergedClusterGraph {

	public final NodeHashMap nodesByHash = new NodeHashMap();

	public MergedNode addNode(long hash, SoftwareModule module, MetaNodeType type) {
		NodeList collisions = nodesByHash.get(hash);
		int collisionCount = 0;
		if (collisions != null) {
			collisionCount = collisions.size();
		}

		MergedNode node = new MergedNode(hash, collisionCount, new MergedModule(module.unit, module.version), type);
		nodesByHash.add(node);
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
