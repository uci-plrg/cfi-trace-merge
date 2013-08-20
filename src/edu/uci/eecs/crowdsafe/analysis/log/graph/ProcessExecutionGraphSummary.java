package edu.uci.eecs.crowdsafe.analysis.log.graph;

import edu.uci.eecs.crowdsafe.analysis.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.analysis.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.analysis.data.graph.ProcessExecutionGraph;

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
			int clusterNodeCount = 0;
			for (ModuleGraph module : cluster.getGraphs()) {
				// Output basic nodes info
				int realNodeCount = module.getGraphData().getNodeCount()
						- module.getCrossModuleSignatureCount();
				clusterNodeCount += realNodeCount;
				System.out
						.println(String
								.format("Module %s has %d nodes and %d cross-module entry points, with %d accessible nodes.",
										module.softwareUnit.name,
										realNodeCount,
										module.getCrossModuleSignatureCount(),
										module.getAccessibleNodes().size()));

				System.out.println(String.format(
						"Cluster %s has %d modules and %d total nodes",
						cluster.distribution.name,
						cluster.distribution.distributionUnits.size(),
						clusterNodeCount));
			}
		}
	}
}
