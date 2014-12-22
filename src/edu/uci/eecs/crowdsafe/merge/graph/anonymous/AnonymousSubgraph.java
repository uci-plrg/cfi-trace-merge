package edu.uci.eecs.crowdsafe.merge.graph.anonymous;

import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeSource;

class AnonymousSubgraph extends ModuleGraphCluster<ClusterNode<?>> {

	private static int ID_INDEX = 0;
	
	public final int id = ID_INDEX++;
	public final GraphMergeSource source;
	private ClusterNode<?> blackBoxSingleton = null;

	public AnonymousSubgraph(String name, GraphMergeSource source, AutonomousSoftwareDistribution cluster) {
		super(name, cluster);
		
		this.source = source;
	}

	public boolean isAnonymousBlackBox() {
		return blackBoxSingleton != null;
	}

	public ClusterNode<?> getBlackBoxSingleton() {
		return blackBoxSingleton;
	}

	public void addNode(ClusterNode<?> node) {
		super.addNode(node);

		if (node.isBlackBoxSingleton()) {
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
