package edu.uci.eecs.crowdsafe.merge.graph.anonymous;

import java.util.ArrayList;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeSource;

public class AnonymousSubgraph extends ModuleGraphCluster<ClusterNode<?>> {

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
			if (getExecutableNodeCount() > 0) {
				logGraph();
				Log.warn("Removing %d executable nodes for incoming black box singleton", getExecutableNodeCount());
				for (ClusterNode<?> walk : new ArrayList<ClusterNode<?>>(getAllNodes())) {
					Log.log("    %s", walk);
				}
				// It would be nicer to skip the anonymous part of the merge, but I'm not really sure how to copy an
				// anonymous module
//				throw new IllegalArgumentException(String.format(
//						"Cannot add a black box singleton to a graph having executable nodes (%d).",
//						getExecutableNodeCount()));
			}
			blackBoxSingleton = node;
		} else if (node.getType().isExecutable) {
			if (blackBoxSingleton != null)
				Log.warn("Skipping dynamic node %s: cannot add an executable node to a black box graph.", node);
		}
	}
}
