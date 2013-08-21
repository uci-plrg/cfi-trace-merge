package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ModuleGraphCluster;

public class ModuleClusterGraphMerger {

	private final ModuleGraphCluster left, right;

	public ModuleClusterGraphMerger(ModuleGraphCluster left,
			ModuleGraphCluster right) {
		if (left.distribution != right.distribution) {
			throw new IllegalArgumentException(
					"Module graph matching requires the same clusters!");
		}

		if (left.getGraphData().nodesByKey.size() > right.getGraphData().nodesByKey
				.size()) {
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
		GraphMergeSession session = new GraphMergeSession(left, right);
		session.merge();
	}
}
