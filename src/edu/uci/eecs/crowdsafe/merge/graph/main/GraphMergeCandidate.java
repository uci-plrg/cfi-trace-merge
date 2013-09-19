package edu.uci.eecs.crowdsafe.merge.graph.main;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader.ClusterGraphLoadSession;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.loader.ProcessGraphLoadSession;
import edu.uci.eecs.crowdsafe.common.data.results.Graph;
import edu.uci.eecs.crowdsafe.common.datasource.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.cluster.ClusterTraceDirectory;
import edu.uci.eecs.crowdsafe.common.datasource.execution.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.execution.ExecutionTraceDirectory;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.graph.MergeDebugLog;

abstract class GraphMergeCandidate {

	final MergeDebugLog debugLog;

	GraphMergeCandidate(MergeDebugLog debugLog) {
		this.debugLog = debugLog;
	}

	abstract void loadData(File directory) throws IOException;

	abstract Graph.Process summarizeGraph();

	abstract Collection<AutonomousSoftwareDistribution> getRepresentedClusters();

	abstract ModuleGraphCluster<? extends Node> getClusterGraph(AutonomousSoftwareDistribution cluster)
			throws IOException;

	static class Execution extends GraphMergeCandidate {

		private ProcessExecutionGraph graph;

		public Execution(MergeDebugLog debugLog) {
			super(debugLog);
		}

		public Execution(ProcessExecutionGraph graph, MergeDebugLog debugLog) {
			super(debugLog);
			this.graph = graph;
		}

		@Override
		public void loadData(File directory) throws IOException {
			if (graph != null)
				return;

			ExecutionTraceDataSource dataSource = new ExecutionTraceDirectory(directory,
					ProcessExecutionGraph.EXECUTION_GRAPH_FILE_TYPES);

			long start = System.currentTimeMillis();

			ProcessGraphLoadSession loadSession = new ProcessGraphLoadSession();
			Log.log("Loading graph %s", directory.getAbsolutePath());
			graph = loadSession.loadGraph(dataSource, debugLog);

			Log.log("\nGraph loaded in %f seconds.", ((System.currentTimeMillis() - start) / 1000.));
		}

		@Override
		public Graph.Process summarizeGraph() {
			return graph.summarizeProcess();
		}

		@Override
		public Collection<AutonomousSoftwareDistribution> getRepresentedClusters() {
			return graph.getRepresentedClusters();
		}

		@Override
		public ModuleGraphCluster<? extends Node> getClusterGraph(AutonomousSoftwareDistribution cluster) {
			return graph.getModuleGraphCluster(cluster);
		}
	}

	static class Cluster extends GraphMergeCandidate {

		private ClusterTraceDataSource dataSource;
		private ClusterGraphLoadSession loadSession;
		private Graph.Process.Builder summaryBuilder = Graph.Process.newBuilder();

		public Cluster(MergeDebugLog debugLog) {
			super(debugLog);
		}

		@Override
		public void loadData(File directory) throws IOException {
			dataSource = new ClusterTraceDirectory(directory);
			loadSession = new ClusterGraphLoadSession(dataSource);
		}

		@Override
		public Graph.Process summarizeGraph() {
			return summaryBuilder.buildPartial();
		}

		@Override
		public Collection<AutonomousSoftwareDistribution> getRepresentedClusters() {
			return dataSource.getReprsentedClusters();
		}

		@Override
		public ModuleGraphCluster<? extends Node> getClusterGraph(AutonomousSoftwareDistribution cluster)
				throws IOException {
			return loadSession.loadClusterGraph(cluster);
		}
	}
}
