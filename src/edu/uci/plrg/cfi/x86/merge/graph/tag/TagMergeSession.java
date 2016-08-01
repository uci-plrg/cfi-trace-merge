package edu.uci.plrg.cfi.x86.merge.graph.tag;

import java.util.ArrayList;
import java.util.List;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.ModuleGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.OrdinalEdgeList;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ApplicationGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.metadata.ModuleUIB;

public class TagMergeSession {

	class PendingEdgeQueue {
		final List<ModuleNode<?>> rightFromNodes = new ArrayList<ModuleNode<?>>();
		final List<Edge<?>> leftEdges = new ArrayList<Edge<?>>();

		int size() {
			return leftEdges.size();
		}
	}

	public static ApplicationGraph mergeTwoGraphs(ModuleGraph<?> left, ModuleGraph<ModuleNode<?>> right,
			TagMergeResults results) {
		TagMergeSession session = new TagMergeSession(left, right, results);
		TagMergeEngine engine = new TagMergeEngine(session);

		engine.mergeGraph();
		//session.logSuspiciousUIB();
		session.results.clusterMergeCompleted();
		return session.right;
	}

	final ModuleGraph<?> left;
	final ApplicationGraph right;
	final TagMergeFragment mergeFragment = new TagMergeFragment(this);

	final TagMergedSubgraphs subgraphs = new TagMergedSubgraphs();

	final TagMergeStatistics statistics = new TagMergeStatistics();
	final TagMergeResults results;

	final PendingEdgeQueue edgeQueue = new PendingEdgeQueue();

	boolean subgraphAnalysisEnabled;

	public TagMergeSession(ModuleGraph<?> left, ModuleGraph<ModuleNode<?>> right,
			TagMergeResults results) {
		this.left = left;
		this.right = new ApplicationGraph(right);
		this.results = results;

		results.beginCluster(this);
	}

	public void logSuspiciousUIB() {
		if (left.metadata.isSingletonExecution()) {
			for (ModuleUIB uib : left.metadata.getSingletonExecution().uibs) {
				if (!uib.isAdmitted) {
					String ku;
					int targetCount = 0;
					if (subgraphs.isMatched(uib.edge.getFromNode())) {
						ku = "K";

						OrdinalEdgeList<ModuleNode<?>> rightEdges = right.graph.getNode(
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
