package edu.uci.eecs.crowdsafe.merge.graph.anonymous;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;

class AnonymousSubgraph extends ModuleGraphCluster<ClusterNode<?>> {

	private ClusterNode<?> blackBoxSingleton = null;

	public AnonymousSubgraph(AutonomousSoftwareDistribution cluster) {
		super(cluster);
	}

	public boolean isAnonymousBlackBox() {
		return blackBoxSingleton != null;
	}

	public ClusterNode<?> getBlackBoxSingleton() {
		return blackBoxSingleton;
	}

	public void addNode(ClusterNode<?> node) {
		super.addNode(node);

		if (node.getType() == MetaNodeType.BLACK_BOX_SINGLETON) {
			if (getExecutableNodeCount() > 0)
				throw new IllegalArgumentException(
						"Cannot add a black box singleton to a graph having executable nodes.");
			blackBoxSingleton = node;
		} else if (node.getType().isExecutable) {
			if (blackBoxSingleton != null)
				throw new IllegalArgumentException("Cannot add an executable node to a black box graph.");
		}
	}
}
