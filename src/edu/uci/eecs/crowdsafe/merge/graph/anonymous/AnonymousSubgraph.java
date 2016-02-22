package edu.uci.eecs.crowdsafe.merge.graph.anonymous;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeSource;

public class AnonymousSubgraph extends ModuleGraphCluster<ClusterNode<?>> {

	private class Analysis {
		
		final Set<ClusterNode<?>> visitedNodes = new HashSet<ClusterNode<?>>();
		final Set<ClusterNode<?>> unvisitedNodes = new HashSet<ClusterNode<?>>();
		final Queue<ClusterNode<?>> bfsQueue = new LinkedList<ClusterNode<?>>();
		final List<ClusterNode<?>> orphanedEntries = new ArrayList<ClusterNode<?>>();
		
		void reset() {
			visitedNodes.clear();
			unvisitedNodes.clear();
			bfsQueue.clear();
			orphanedEntries.clear();
		}

		void log(boolean isFinal) {
			Log.log("\nGraph traversal for cluster %s (0x%x)", cluster, hashCode());

			for (ClusterNode<?> node : getAllNodes()) {
				if (node.getType().isApplicationNode) {
					if (node.getIncomingEdges().size() == 0)
						orphanedEntries.add(node);
					else
						unvisitedNodes.add(node);
				} else if (node.getType() == MetaNodeType.CLUSTER_ENTRY) {
					bfsQueue.add(node);
				}
			}

			traverseFromEntries();
			
			if (!isFinal)
				return;

			if (!orphanedEntries.isEmpty()) {
				for (ClusterNode<?> orphanedEntry : orphanedEntries) {
					Log.log(" ## Subgraph reachable only from orphaned entry point %s:", orphanedEntry);
					bfsQueue.add(orphanedEntry);
					traverseFromEntries();
				}
			}

			if (!unvisitedNodes.isEmpty()) {
				for (ClusterNode<?> node : unvisitedNodes) {
					Log.error("Node %s is only reachable from nodes outside the subgraph!", node);
					OrdinalEdgeList<ClusterNode<?>> edgeList = node.getIncomingEdges();
					try {
						for (Edge<ClusterNode<?>> edge : edgeList)
							Log.log("\t%s", edge);
					} finally {
						edgeList.release();
					}
				}
				throw new InvalidGraphException("Subgraph partition failed!", unvisitedNodes);
			}
		}

		void traverseFromEntries() {
			while (bfsQueue.size() > 0) {
				ClusterNode<?> node = bfsQueue.remove();
				visitedNodes.add(node);
				unvisitedNodes.remove(node);

				OrdinalEdgeList<ClusterNode<?>> edgeList = node.getOutgoingEdges();
				try {
					for (Edge<ClusterNode<?>> edge : edgeList) {
						ClusterNode<?> neighbor = edge.getToNode();
						if (!visitedNodes.contains(neighbor)) {
							bfsQueue.add(neighbor);
							visitedNodes.add(neighbor);
							unvisitedNodes.remove(node);
						}
						Log.log(edge);
					}
				} finally {
					edgeList.release();
				}
			}
		}
	}

	private static int ID_INDEX = 0;

	public final int id = ID_INDEX++;
	public final GraphMergeSource source;
	private ClusterNode<?> blackBoxSingleton = null;

	private Analysis analysis = null;

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
				// throw new IllegalArgumentException(String.format(
				// "Cannot add a black box singleton to a graph having executable nodes (%d).",
				// getExecutableNodeCount()));
			}
			blackBoxSingleton = node;
		} else if (node.getType().isExecutable) {
			if (blackBoxSingleton != null)
				Log.warn("Skipping dynamic node %s: cannot add an executable node to a black box graph.", node);
		}
	}
	
	@Override
	public void logGraph() {
		this.logGraph(false);
	}

	public void logGraph(boolean isFinal) {
		if (analysis == null)
			analysis = new Analysis();
		else
			analysis.reset();
		analysis.log(isFinal);
	}
}
