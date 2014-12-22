package edu.uci.eecs.crowdsafe.merge.graph.tag;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterUIB;

public class ClusterTagMergeSession {

	class PendingEdgeQueue {
		final List<ClusterNode<?>> rightFromNodes = new ArrayList<ClusterNode<?>>();
		final List<Edge<?>> leftEdges = new ArrayList<Edge<?>>();

		int size() {
			return leftEdges.size();
		}
	}

	public static ClusterGraph mergeTwoGraphs(ModuleGraphCluster<?> left, ModuleGraphCluster<ClusterNode<?>> right,
			ClusterTagMergeResults results) {
		ClusterTagMergeSession session = new ClusterTagMergeSession(left, right, results);
		ClusterTagMergeEngine engine = new ClusterTagMergeEngine(session);

		engine.mergeGraph();
		//session.logSuspiciousUIB();
		session.results.clusterMergeCompleted();
		return session.right;
	}

	final ModuleGraphCluster<?> left;
	final ClusterGraph right;
	final ClusterTagMergeFragment mergeFragment = new ClusterTagMergeFragment(this);

	final ClusterTagMergedSubgraphs subgraphs = new ClusterTagMergedSubgraphs();

	final ClusterTagMergeStatistics statistics = new ClusterTagMergeStatistics();
	final ClusterTagMergeResults results;

	final PendingEdgeQueue edgeQueue = new PendingEdgeQueue();

	boolean subgraphAnalysisEnabled;

	public ClusterTagMergeSession(ModuleGraphCluster<?> left, ModuleGraphCluster<ClusterNode<?>> right,
			ClusterTagMergeResults results) {
		this.left = left;
		this.right = new ClusterGraph(right);
		this.results = results;

		results.beginCluster(this);
	}

	public void logSuspiciousUIB() {
		if (left.metadata.isSingletonExecution()) {
			for (ClusterUIB uib : left.metadata.getSingletonExecution().uibs) {
				if (!uib.isAdmitted) {
					String ku;
					int targetCount = 0;
					if (subgraphs.isMatched(uib.edge.getFromNode())) {
						ku = "K";

						OrdinalEdgeList<ClusterNode<?>> rightEdges = right.graph.getNode(
								uib.edge.getFromNode().getKey()).getOutgoingEdges(uib.edge.getOrdinal());
						try {
							targetCount = rightEdges.size();
						} finally {
							rightEdges.release();
						}
					} else {
						ku = "U";
					}
					ku += "->";
					ku += subgraphs.isMatched(uib.edge.getToNode()) ? "K" : "U";
					Log.log(" > UIB: %s %dI %dT of %s <%d>", ku, uib.instanceCount, uib.traversalCount, uib.edge,
							targetCount);
				}
			}
		}
	}
}
