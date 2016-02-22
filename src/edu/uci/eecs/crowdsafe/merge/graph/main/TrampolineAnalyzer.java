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
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.loader.ClusterGraphLoadSession;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDirectory;
import edu.uci.eecs.crowdsafe.graph.main.CommonMergeOptions;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeSource;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousModule;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousSubgraph;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.MaximalSubgraphs;

public class TrampolineAnalyzer {

	private static class BoundaryEdge {

		private final Edge<ClusterNode<?>> edge;
		private final AutonomousSoftwareDistribution cluster;

		BoundaryEdge(Edge<ClusterNode<?>> edge, AutonomousSoftwareDistribution cluster) {
			this.edge = edge;
			this.cluster = cluster;
		}

		@Override
		public String toString() {
			return String.format("%s--%d|%s-->%s", cluster.getUnitFilename(), edge.getOrdinal(),
					edge.getEdgeType().code, edge.getToNode());
		}
	}

	private class AnonymousModuleAnalyzer {
		private final Map<AutonomousSoftwareDistribution, Set<Long>> hashesByTrampolineGenerator = new HashMap<AutonomousSoftwareDistribution, Set<Long>>();

		private void analyze(ModuleGraphCluster<ClusterNode<?>> anonymous) {
			if (anonymous == null) {
				Log.warn("No anonymous graph for run %d", runIndex);
				return;
			}

			Log.log(" === Original anonymous graph:");
			anonymous.logGraph();

			Set<AnonymousSubgraph> anonymousSubgraphs = MaximalSubgraphs.getMaximalSubgraphs(GraphMergeSource.LEFT,
					anonymous);
			List<BoundaryEdge> gencodeEntries = new ArrayList<BoundaryEdge>();
			List<BoundaryEdge> executionEntries = new ArrayList<BoundaryEdge>();
			List<BoundaryEdge> executionExits = new ArrayList<BoundaryEdge>();
			List<ClusterNode<?>> returnNodes = new ArrayList<ClusterNode<?>>();

			Set<AutonomousSoftwareDistribution> entryModules = new HashSet<AutonomousSoftwareDistribution>();
			Set<AutonomousSoftwareDistribution> owners = new HashSet<AutonomousSoftwareDistribution>();

			for (AnonymousSubgraph subgraph : anonymousSubgraphs) {
				if (subgraph.isAnonymousBlackBox())
					continue;

				Log.log("Reporting graph 0x%x", subgraph.hashCode());

				gencodeEntries.clear();
				executionEntries.clear();
				executionExits.clear();
				returnNodes.clear();
				owners.clear();
				entryModules.clear();
				AutonomousSoftwareDistribution cluster = null, owner = null, gencodeFromModule;
				for (ClusterNode<?> entryPoint : subgraph.getEntryPoints()) {
					gencodeFromModule = ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousGencodeHash(
							entryPoint.getHash());
					if (gencodeFromModule != null) {
						OrdinalEdgeList<ClusterNode<?>> edges = entryPoint.getOutgoingEdges();
						try {
							for (Edge<ClusterNode<?>> gencodeEdge : edges)
								gencodeEntries.add(new BoundaryEdge(gencodeEdge, gencodeFromModule));
						} finally {
							edges.release();
						}
						owners.add(gencodeFromModule);
						continue;
					}

					cluster = ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousEntryHash(
							entryPoint.getHash());
					OrdinalEdgeList<ClusterNode<?>> edges = entryPoint.getOutgoingEdges();
					try {
						for (Edge<ClusterNode<?>> executionEdge : edges)
							executionEntries.add(new BoundaryEdge(executionEdge, cluster));
					} finally {
						edges.release();
					}
					entryModules.add(cluster);
				}

				owners.retainAll(entryModules);

				for (ClusterNode<?> exitPoint : subgraph.getExitPoints()) {
					cluster = ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousExitHash(
							exitPoint.getHash());
					if (cluster == null)
						continue; // can have gencode edges

					OrdinalEdgeList<ClusterNode<?>> edges = exitPoint.getIncomingEdges();
					try {
						for (Edge<ClusterNode<?>> executionEdge : edges)
							executionExits.add(new BoundaryEdge(executionEdge, cluster));
					} finally {
						edges.release();
					}
				}

				for (ClusterNode<?> node : subgraph.getAllNodes()) {
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
						subgraph.getExecutableNodeCount(), executionEntries.size(), owner.getUnitFilename());
				Log.log("\t--- Gencode entries:");
				for (BoundaryEdge gencodeEdge : gencodeEntries)
					Log.log("\t%s", gencodeEdge);
				Log.log("\t--- Execution entries:");
				for (BoundaryEdge executionEdge : executionEntries)
					Log.log("\t%s", executionEdge);
				Log.log("\t--- Execution exits:");
				for (BoundaryEdge executionEdge : executionExits)
					Log.log("\t%s", executionEdge);
				for (ClusterNode<?> returnNode : returnNodes)
					Log.log("\t%s", returnNode);
				Log.log(" === SDR end\n");

				Set<Long> hashes = hashesByTrampolineGenerator.get(cluster);
				if (hashes == null) {
					hashes = new HashSet<Long>();
					hashesByTrampolineGenerator.put(cluster, hashes);
				}

				if (false) {
					NodeHashMap<?> nodeMap = anonymous.getGraphData().nodesByHash;
					for (Long hash : nodeMap.keySet()) {
						NodeList<?> nodes = nodeMap.get(hash);
						for (int i = 0; i < nodes.size(); i++) {
							ClusterNode<?> node = (ClusterNode<?>) nodes.get(i);
							if (!hashes.contains(node.getHash())) {
								hashes.add(node.getHash());
								Log.log("#%d: Found new hash 0x%x in %s's trampoline", runIndex, node.getHash(),
										cluster.getSingletonUnit().filename);
							}
						}
					}
				}
			}
		}
	}

	private final ArgumentStack args;
	private final CommonMergeOptions options;

	private ClusterTraceDataSource dataSource;
	private ClusterGraphLoadSession loadSession;

	private int runIndex = 0;

	private AnonymousModuleAnalyzer anonymousModuleAnalyzer = new AnonymousModuleAnalyzer();

	private TrampolineAnalyzer(ArgumentStack args) {
		this.args = args;
		this.options = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir);
	}

	private void run() {
		try {
			AnonymousModule.initialize();

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

				dataSource = new ClusterTraceDirectory(runDirectory).loadExistingFiles();
				loadSession = new ClusterGraphLoadSession(dataSource);

				anonymousModuleAnalyzer.analyze(loadSession
						.loadClusterGraph(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER));
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
