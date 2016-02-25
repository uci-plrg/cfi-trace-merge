package edu.uci.eecs.crowdsafe.merge.graph.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.AnonymousGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.ApplicationAnonymousGraphs;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.MaximalSubgraphs;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.loader.ModuleGraphLoadSession;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceDirectory;
import edu.uci.eecs.crowdsafe.graph.main.CommonMergeOptions;

public class TrampolineAnalyzer {

	private static class BoundaryEdge {

		private final Edge<ModuleNode<?>> edge;
		private final ApplicationModule module;

		BoundaryEdge(Edge<ModuleNode<?>> edge, ApplicationModule module) {
			this.edge = edge;
			this.module = module;
		}

		@Override
		public String toString() {
			return String.format("%s--%d|%s-->%s", module.filename, edge.getOrdinal(), edge.getEdgeType().code,
					edge.getToNode());
		}
	}

	private class AnonymousModuleAnalyzer {
		private final Map<ApplicationModule, Set<Long>> hashesByTrampolineGenerator = new HashMap<ApplicationModule, Set<Long>>();

		private void analyze(ApplicationAnonymousGraphs graphs) {
			if (graphs == null) {
				Log.warn("No anonymous graph for run %d", runIndex);
				return;
			}

			List<Edge<ModuleNode<?>>> gencodeEntries = new ArrayList<Edge<ModuleNode<?>>>();
			List<Edge<ModuleNode<?>>> executionEntries = new ArrayList<Edge<ModuleNode<?>>>();
			List<Edge<ModuleNode<?>>> executionExits = new ArrayList<Edge<ModuleNode<?>>>();
			List<ModuleNode<?>> returnNodes = new ArrayList<ModuleNode<?>>();

			Set<ApplicationModule> entryModules = new HashSet<ApplicationModule>();
			Set<ApplicationModule> owners = new HashSet<ApplicationModule>();

			for (AnonymousGraph subgraph : graphs.getAllGraphs()) {
				if (subgraph.isJIT())
					continue;

				Log.log("Reporting graph 0x%x", subgraph.hashCode());

				gencodeEntries.clear();
				executionEntries.clear();
				executionExits.clear();
				returnNodes.clear();
				owners.clear();
				entryModules.clear();
				ApplicationModule fromModule, owner = null;
				for (ModuleNode<?> entryPoint : subgraph.getEntryPoints()) {
					ModuleBoundaryNode.HashLabel label = ApplicationModuleSet.getInstance().crossModuleLabels
							.get(entryPoint.getHash());
					OrdinalEdgeList<ModuleNode<?>> edges = entryPoint.getOutgoingEdges();
					try {
						fromModule = ApplicationModuleSet.getInstance().modulesByFilename.get(label.fromModuleFilename);
						if (label.isGencode()) {
							gencodeEntries.addAll(edges);
							owners.add(fromModule);
						} else {
							executionEntries.addAll(edges);
							entryModules.add(fromModule);
						}
					} finally {
						edges.release();
					}
				}

				owners.retainAll(entryModules);

				for (ModuleNode<?> exitPoint : subgraph.getExitPoints()) {
					ModuleBoundaryNode.HashLabel label = ApplicationModuleSet.getInstance().crossModuleLabels
							.get(exitPoint.getHash());

					if (label == null) {
						Log.error("Error: can't find the label for cross-module hash 0x%x", exitPoint.getHash());
					} else if (!label.isGencode()) {
						OrdinalEdgeList<ModuleNode<?>> edges = exitPoint.getIncomingEdges();
						try {
							executionExits.addAll(edges);
						} finally {
							edges.release();
						}
					}
				}

				for (ModuleNode<?> node : subgraph.getAllNodes()) {
					if (node.getType() == MetaNodeType.RETURN)
						returnNodes.add(node);
				}

				if (owners.size() == 1) {
					owner = owners.iterator().next();
				} else {
					if (owners.isEmpty()) {
						Log.error(
								" ### Cannot find the owner for an anonymous subgraph of %d nodes with entry points %s",
								subgraph.getExecutableNodeCount(), subgraph.getEntryPoints());
					} else {
						Log.error(
								" ### Multiple potential owners for an anonymous subgraph of %d nodes with entry points %s",
								subgraph.getExecutableNodeCount(), subgraph.getEntryPoints());
					}
					subgraph.logGraph(true);
					Log.warn(" ### ownership\n");
					continue;
				}

				if (executionExits.isEmpty() && returnNodes.isEmpty()) {
					Log.error(" ### Missing exit from anonymous subgraph of %d nodes with entry points %s",
							subgraph.getExecutableNodeCount(), subgraph.getEntryPoints());
					subgraph.logGraph(true);
					Log.warn(" ### exit\n");
					continue;
				}

				Log.log(" === SDR of %d executable nodes with %d entries owned by %s:",
						subgraph.getExecutableNodeCount(), executionEntries.size(), owner.filename);
				Log.log("\t--- Gencode entries:");
				for (Edge<ModuleNode<?>> gencodeEdge : gencodeEntries)
					Log.log("\t%s", gencodeEdge);
				Log.log("\t--- Execution entries:");
				for (Edge<ModuleNode<?>> executionEdge : executionEntries)
					Log.log("\t%s", executionEdge);
				Log.log("\t--- Execution exits:");
				for (Edge<ModuleNode<?>> executionEdge : executionExits)
					Log.log("\t%s", executionEdge);
				for (ModuleNode<?> returnNode : returnNodes)
					Log.log("\t%s", returnNode);
				Log.log(" === SDR end\n");
			}
		}
	}

	private final ArgumentStack args;
	private final CommonMergeOptions options;

	private ModularTraceDataSource dataSource;
	private ModuleGraphLoadSession loadSession;

	private int runIndex = 0;

	private AnonymousModuleAnalyzer anonymousModuleAnalyzer = new AnonymousModuleAnalyzer();

	private TrampolineAnalyzer(ArgumentStack args) {
		this.args = args;
		this.options = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir);
	}

	private void run() {
		try {
			options.parseOptions();
			options.initializeGraphEnvironment();

			Log.addOutput(System.out);

			String path = args.pop();
			File runCatalog = new File(path);
			if (!(runCatalog.exists() && runCatalog.isFile())) {
				Log.error("Illegal run catalog '%s'; no such file.", runCatalog.getAbsolutePath());
				printUsageAndExit();
			}

			List<File> runDirectories = new ArrayList<File>();
			BufferedReader in = new BufferedReader(new FileReader(runCatalog));
			boolean failed = false;
			while (in.ready()) {
				String runPath = in.readLine();
				File runDirectory = new File(runPath);
				if (!(runDirectory.exists() && runDirectory.isDirectory())) {
					Log.error("Run catalog contains an invalid run directory: %s. Exiting now.",
							runDirectory.getAbsolutePath());
					failed = true;
					break;
				}
				runDirectories.add(runDirectory);
			}
			in.close();
			if (failed)
				return;

			for (File runDirectory : runDirectories) {
				runIndex++;

				dataSource = new ModularTraceDirectory(runDirectory).loadExistingFiles();
				loadSession = new ModuleGraphLoadSession(dataSource);

				anonymousModuleAnalyzer.analyze(loadSession.loadAnonymousGraphs(ApplicationModule.ANONYMOUS_MODULE));
			}
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}
	}

	private void printUsageAndExit() {
		System.out.println(String.format("Usage: %s <run-catalog>", getClass().getSimpleName()));
		System.out.println("# The run catalog lists relative paths to the run directories.");
		System.out.println("# Entries must be in execution sequence, one per line.");
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		TrampolineAnalyzer analyzer = new TrampolineAnalyzer(stack);
		analyzer.run();
	}
}
