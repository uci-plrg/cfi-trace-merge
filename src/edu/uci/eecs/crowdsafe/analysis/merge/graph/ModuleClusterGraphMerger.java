package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import java.util.HashMap;
import java.util.LinkedList;

import edu.uci.eecs.crowdsafe.analysis.data.graph.MatchedNodes;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ModuleGraph;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.DebugUtils;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.MatchingInstance;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.MatchingType;

public class ModuleClusterGraphMerger {

	private final ModuleGraphCluster left, right;

	public ModuleClusterGraphMerger(ModuleGraphCluster left,
			ModuleGraphCluster right) {
		if (left.distribution != right.distribution) {
			throw new IllegalArgumentException(
					"Module graph matching requires the same clusters!");
		}

		if (left.calculateTotalNodeCount() > right.calculateTotalNodeCount()) {
			this.left = left;
			this.right = right;
		} else {
			this.left = right;
			this.right = left;
		}

		System.out.println("\n    ==== " + left.distribution.name + " & "
				+ right.distribution.name + " ====");

		/**
		 * <pre>
		if (DebugUtils.debug_decision(DebugUtils.DUMP_GRAPH)) {
			GraphMergingInfo.dumpGraph(graph1,
					DebugUtils.GRAPH_DIR + graph1.gCG dataSource.getProcessName()
							+ graph1.dataSource.getProcessName() + ".dot");
			GraphMergingInfo.dumpGraph(graph2,
					DebugUtils.GRAPH_DIR + graph2.dataSource.getProcessName()
							+ graph2.dataSource.getProcessName() + ".dot");
		}
		 */
	}

	public void merge() {
		GraphMergeSession session = new GraphMergeSession();

		for (ModuleGraph leftModule : left.getGraphs()) {
			session.left.addModule(leftModule);
		}
		for (ModuleGraph rightModule : right.getGraphs()) {
			session.right.addModule(rightModule);
		}

		session.merge();
	}
}
