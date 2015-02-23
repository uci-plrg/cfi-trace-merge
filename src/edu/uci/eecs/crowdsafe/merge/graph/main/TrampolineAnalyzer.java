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
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeList;
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

	private class AnonymousModuleAnalyzer {
		private final Map<AutonomousSoftwareDistribution, Set<Long>> hashesByTrampolineGenerator = new HashMap<AutonomousSoftwareDistribution, Set<Long>>();

		private void analyze(ModuleGraphCluster<ClusterNode<?>> anonymous) {
			if (anonymous == null) {
				Log.warn("No anonymous graph for run %d", runIndex);
				return;
			}

			Set<AnonymousSubgraph> anonymousSubgraphs;

			try { // hack!
				anonymousSubgraphs = MaximalSubgraphs.getMaximalSubgraphs(GraphMergeSource.LEFT, anonymous);
			} catch (Exception e) {
				Log.error(
						"Failed to divide the anonymous graph into maximal subgraphs: %s. Skipping this graph completely.",
						e);
				return;
			}

			for (AnonymousSubgraph subgraph : anonymousSubgraphs) {
				if (subgraph.isAnonymousBlackBox())
					continue;

				AutonomousSoftwareDistribution cluster = null;
				for (ClusterNode<?> entryPoint : subgraph.getEntryPoints()) {
					if (ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousGencodeHash(
							entryPoint.getHash()) != null)
						continue;

					cluster = ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousEntryHash(
							entryPoint.getHash());
					cluster = AnonymousModule.resolveAlias(cluster);
					if (AnonymousModule.isEligibleOwner(cluster))
						break;
					else
						cluster = null;
				}

				if (cluster == null) {
					Log.warn("Cannot find the owner for an anonymous subgraph of %d nodes",
							subgraph.getExecutableNodeCount());
					continue;
				}

				Set<Long> hashes = hashesByTrampolineGenerator.get(cluster);
				if (hashes == null) {
					hashes = new HashSet<Long>();
					hashesByTrampolineGenerator.put(cluster, hashes);
				}

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
		System.out.println("# Entries must be in execution sequence, one per line..");
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		TrampolineAnalyzer analyzer = new TrampolineAnalyzer(stack);
		analyzer.run();
	}
}
