package edu.uci.eecs.crowdsafe.merge.graph.main;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import javax.sql.CommonDataSource;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader.ClusterGraphLoadSession;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.loader.ProcessGraphLoadSession;
import edu.uci.eecs.crowdsafe.common.data.results.Graph;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceDirectory;
import edu.uci.eecs.crowdsafe.common.io.execution.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.common.io.execution.ExecutionTraceDirectory;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.graph.MergeDebugLog;

abstract class GraphMergeCandidate {

	final MergeDebugLog debugLog;

	GraphMergeCandidate(MergeDebugLog debugLog) {
		this.debugLog = debugLog;
	}

	abstract void loadData() throws IOException;

	abstract String parseTraceName();

	abstract Graph.Process summarizeGraph();

	abstract Collection<AutonomousSoftwareDistribution> getRepresentedClusters();

	abstract ModuleGraphCluster<?> getClusterGraph(AutonomousSoftwareDistribution cluster) throws IOException;

	static class Execution extends GraphMergeCandidate {

		private final ExecutionTraceDataSource dataSource;

		private ProcessExecutionGraph graph;

		public Execution(File directory, MergeDebugLog debugLog) {
			super(debugLog);
			dataSource = new ExecutionTraceDirectory(directory, ProcessExecutionGraph.EXECUTION_GRAPH_FILE_TYPES);
		}

		public Execution(ProcessExecutionGraph graph, MergeDebugLog debugLog) {
			super(debugLog);
			this.graph = graph;
			dataSource = null;
		}

		@Override
		public void loadData() throws IOException {
			if (graph != null)
				return;

			long start = System.currentTimeMillis();

			ProcessGraphLoadSession loadSession = new ProcessGraphLoadSession();
			Log.log("Loading graph from data source %s", dataSource);
			graph = loadSession.loadGraph(dataSource, debugLog);

			Log.log("\nGraph loaded in %f seconds.", ((System.currentTimeMillis() - start) / 1000.));
		}

		@Override
		String parseTraceName() {
			return dataSource.getProcessName() + "." + dataSource.getProcessId();
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
		public ModuleGraphCluster<?> getClusterGraph(AutonomousSoftwareDistribution cluster) {
			return graph.getModuleGraphCluster(cluster);
		}
	}

	static class Cluster extends GraphMergeCandidate {

		private ClusterTraceDataSource dataSource;
		private ClusterGraphLoadSession loadSession;
		private Graph.Process.Builder summaryBuilder = Graph.Process.newBuilder();

		public Cluster(File directory, MergeDebugLog debugLog) {
			super(debugLog);
			dataSource = new ClusterTraceDirectory(directory).loadExistingFiles();
			summaryBuilder.setName(directory.getName());
		}

		public Cluster(ClusterTraceDataSource dataSource, String name, MergeDebugLog debugLog) {
			super(debugLog);
			this.dataSource = dataSource;
			summaryBuilder.setName(name);
		}

		@Override
		public void loadData() throws IOException {
			loadSession = new ClusterGraphLoadSession(dataSource);
		}

		@Override
		String parseTraceName() {
			return dataSource.parseTraceName();
		}

		@Override
		public Graph.Process summarizeGraph() {
			return summaryBuilder.build();
		}

		@Override
		public Collection<AutonomousSoftwareDistribution> getRepresentedClusters() {
			return dataSource.getReprsentedClusters();
		}

		@Override
		public ModuleGraphCluster<?> getClusterGraph(AutonomousSoftwareDistribution cluster) throws IOException {
			ModuleGraphCluster<?> graph = loadSession.loadClusterGraph(cluster, debugLog);
			if (graph != null) 
				summaryBuilder.addCluster(graph.summarize());
			return graph;
		}
	}

	static class LoadedClusters extends GraphMergeCandidate {

		private String name;
		private Map<AutonomousSoftwareDistribution, ModuleGraphCluster<ClusterNode<?>>> graphs;
		private Graph.Process.Builder summaryBuilder = Graph.Process.newBuilder();

		public LoadedClusters(String name,
				Map<AutonomousSoftwareDistribution, ModuleGraphCluster<ClusterNode<?>>> graphs, MergeDebugLog debugLog) {
			super(debugLog);

			this.name = name;
			this.graphs = graphs;
			summaryBuilder.setName(name);
		}

		@Override
		public void loadData() throws IOException {
		}

		@Override
		String parseTraceName() {
			return name;
		}

		@Override
		public Graph.Process summarizeGraph() {
			for (ModuleGraphCluster<ClusterNode<?>> graph : graphs.values()) {
				summaryBuilder.addCluster(graph.summarize());
			}
			return summaryBuilder.buildPartial();
		}

		@Override
		public Collection<AutonomousSoftwareDistribution> getRepresentedClusters() {
			return graphs.keySet();
		}

		@Override
		public ModuleGraphCluster<?> getClusterGraph(AutonomousSoftwareDistribution cluster) throws IOException {
			return graphs.get(cluster);
		}
	}
}
