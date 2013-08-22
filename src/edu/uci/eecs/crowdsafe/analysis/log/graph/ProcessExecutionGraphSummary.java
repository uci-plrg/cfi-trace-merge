package edu.uci.eecs.crowdsafe.analysis.log.graph;

import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.util.log.Log;

public class ProcessExecutionGraphSummary {
	/**
	 * This function is called when the graph is constructed. It can contain any kind of analysis of the current
	 * ExecutionGraph and output it the the console.
	 * 
	 * It could actually contains information like: 1. Number of nodes & signature nodes in the main module and in every
	 * module. 2. Number of non-kernel modules in the main module.
	 */
	public static void summarizeGraph(ProcessExecutionGraph graph) {
		for (ModuleGraphCluster cluster : graph.getAutonomousClusters()) {
			int clusterNodeCount = cluster.getGraphData().nodesByKey.size();
			Log.log(String
					.format("Cluster %s has %d nodes and %d cross-module entry points, with %d accessible nodes.",
							cluster.distribution.name, clusterNodeCount,
							cluster.getEntryNodeCount(), cluster
									.searchAccessibleNodes().size()));
		}
	}
}
