package edu.uci.eecs.crowdsafe.merge.graph;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.io.TraceDataSourceException;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.ApplicationAnonymousGraphs;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.loader.ProcessGraphLoadSession;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.loader.ModuleGraphLoadSession;
import edu.uci.eecs.crowdsafe.graph.data.results.Graph;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceDirectory;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceDirectory;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashMergeDebugLog;

public interface GraphMergeCandidate {

	abstract void loadData() throws IOException;

	abstract String parseTraceName();

	abstract void summarizeModule(ApplicationModule module);

	abstract Graph.Process summarizeGraph();

	abstract Collection<ApplicationModule> getRepresentedModules();

	abstract ModuleGraph<?> getModuleGraph(ApplicationModule module) throws IOException;

	abstract ApplicationAnonymousGraphs getAnonymousGraph() throws IOException;

	static class Execution implements GraphMergeCandidate {

		private final HashMergeDebugLog debugLog;

		private final ExecutionTraceDataSource dataSource;

		private ProcessExecutionGraph graph;

		public Execution(File directory, HashMergeDebugLog debugLog) {
			this.debugLog = debugLog;
			dataSource = new ExecutionTraceDirectory(directory, ProcessExecutionGraph.EXECUTION_GRAPH_FILE_TYPES,
					ProcessExecutionGraph.EXECUTION_GRAPH_REQUIRED_FILE_TYPES);
		}

		public Execution(ProcessExecutionGraph graph, HashMergeDebugLog debugLog) {
			this.debugLog = debugLog;
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
		public String parseTraceName() {
			return dataSource.getProcessName() + "." + dataSource.getProcessId();
		}

		@Override
		public void summarizeModule(ApplicationModule module) {
		}

		@Override
		public Graph.Process summarizeGraph() {
			return graph.summarizeProcess();
		}

		@Override
		public Collection<ApplicationModule> getRepresentedModules() {
			return graph.getRepresentedModules();
		}

		@Override
		public ModuleGraph<?> getModuleGraph(ApplicationModule module) {
			return graph.getModuleGraph(module);
		}
		
		@Override
		public ApplicationAnonymousGraphs getAnonymousGraph() throws IOException {
			throw new UnsupportedOperationException("Not implemented");
		}
	}

	static class Modular implements GraphMergeCandidate {

		private final HashMergeDebugLog debugLog;

		private ModularTraceDataSource dataSource;
		private ModuleGraphLoadSession loadSession;
		private Graph.Process.Builder summaryBuilder = Graph.Process.newBuilder();

		private final Map<ApplicationModule, ModuleGraph<?>> graphs = new HashMap<ApplicationModule, ModuleGraph<?>>();

		public Modular(File directory, HashMergeDebugLog debugLog) throws TraceDataSourceException, IOException {
			this.debugLog = debugLog;
			dataSource = new ModularTraceDirectory(directory).loadExistingFiles();
			summaryBuilder.setName(directory.getName());
		}

		public Modular(ModularTraceDataSource dataSource, String name, HashMergeDebugLog debugLog) {
			this.debugLog = debugLog;

			this.dataSource = dataSource;
			summaryBuilder.setName(name);
		}

		@Override
		public void loadData() throws IOException {
			loadSession = new ModuleGraphLoadSession(dataSource);
		}

		@Override
		public String parseTraceName() {
			return dataSource.parseTraceName();
		}

		@Override
		public void summarizeModule(ApplicationModule module) {
			ModuleGraph<?> graph = graphs.remove(module);
			if (graph != null) {
				summaryBuilder.addModule(graph.summarize(graph.module.isAnonymous));

				if (graph.metadata.isMain()) {
					Log.log("Setting interval metadata on the main graph %s of %s", graph.module.name, dataSource
							.getDirectory().getName());
					summaryBuilder.setMetadata(graph.metadata.summarizeProcess());
					if (graph.metadata.getRootSequence() != null) { // hack! FIXME
						Log.log("Execution index for <%s> main is %d", graph.name,
								graph.metadata.getRootSequence().executions.size());
					}
				}
			}
		}

		@Override
		public Graph.Process summarizeGraph() {
			return summaryBuilder.build();
		}

		@Override
		public Collection<ApplicationModule> getRepresentedModules() {
			return dataSource.getReprsentedModules();
		}

		@Override
		public ModuleGraph<?> getModuleGraph(ApplicationModule module) throws IOException {
			ModuleGraph<?> graph = loadSession.loadModuleGraph(module, debugLog);
			if (graph != null) {
				graphs.put(module, graph);
			}
			return graph;
		}

		@Override
		public ApplicationAnonymousGraphs getAnonymousGraph() throws IOException {
			return loadSession.loadAnonymousGraphs();
		}
	}

	static class LoadedModules implements GraphMergeCandidate {

		private final HashMergeDebugLog debugLog;

		private final String name;
		private final Map<ApplicationModule, ModuleGraph<ModuleNode<?>>> graphs;
		private final ApplicationAnonymousGraphs anonymousGraphs;
		private final Graph.Process.Builder summaryBuilder = Graph.Process.newBuilder();

		public LoadedModules(String name, Map<ApplicationModule, ModuleGraph<ModuleNode<?>>> graphs,
				ApplicationAnonymousGraphs anonymousGraphs, HashMergeDebugLog debugLog) {

			this.debugLog = debugLog;

			this.name = name;
			this.graphs = graphs;
			this.anonymousGraphs = anonymousGraphs;
			summaryBuilder.setName(name);
		}

		@Override
		public void loadData() throws IOException {
		}

		@Override
		public String parseTraceName() {
			return name;
		}

		@Override
		public void summarizeModule(ApplicationModule module) {
		}

		@Override
		public Graph.Process summarizeGraph() {
			for (ModuleGraph<ModuleNode<?>> graph : graphs.values()) {
				summaryBuilder.addModule(graph.summarize(graph.module.isAnonymous));
			}
			return summaryBuilder.buildPartial();
		}

		@Override
		public Collection<ApplicationModule> getRepresentedModules() {
			return graphs.keySet();
		}

		@Override
		public ModuleGraph<?> getModuleGraph(ApplicationModule module) throws IOException {
			return graphs.get(module);
		}

		@Override
		public ApplicationAnonymousGraphs getAnonymousGraph() throws IOException {
			return null;
		}
	}
}
