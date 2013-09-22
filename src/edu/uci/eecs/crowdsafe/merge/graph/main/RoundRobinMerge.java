package edu.uci.eecs.crowdsafe.merge.graph.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader.ClusterGraphLoadSession;
import edu.uci.eecs.crowdsafe.common.datasource.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.cluster.ClusterTraceDirectory;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.log.LogFile;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.merge.graph.MergeDebugLog;

public class RoundRobinMerge {

	private static class ProcessClusterGraph {
		final String name;
		final Map<AutonomousSoftwareDistribution, ModuleGraphCluster<ClusterNode<?>>> clusters;

		public ProcessClusterGraph(String name,
				Map<AutonomousSoftwareDistribution, ModuleGraphCluster<ClusterNode<?>>> clusters) {
			this.name = name;
			this.clusters = clusters;
		}
	}

	private static class MergePair {
		final ProcessClusterGraph left;
		final ProcessClusterGraph right;
		final String logFilename;

		MergePair(ProcessClusterGraph left, ProcessClusterGraph right, String logFilename) {
			this.left = left;
			this.right = right;
			this.logFilename = logFilename;
		}
	}

	private class GraphLoadThread extends Thread {
		private final MergeDebugLog debugLog = new MergeDebugLog();
		private final List<ProcessClusterGraph> loadedGraphs = new ArrayList<ProcessClusterGraph>();

		@Override
		public void run() {
			try {
				while (true) {
					String graphPath = getNextGraphPath();
					if (graphPath == null)
						break;

					ClusterTraceDataSource dataSource = new ClusterTraceDirectory(new File(graphPath),
							ClusterGraph.CLUSTER_GRAPH_STREAM_TYPES);
					ClusterGraphLoadSession session = new ClusterGraphLoadSession(dataSource);
					Map<AutonomousSoftwareDistribution, ModuleGraphCluster<ClusterNode<?>>> graphsByCluster = new HashMap<AutonomousSoftwareDistribution, ModuleGraphCluster<ClusterNode<?>>>();
					for (AutonomousSoftwareDistribution cluster : commonOptions.clusterMergeSet) {
						graphsByCluster.put(cluster, session.loadClusterGraph(cluster));
					}
					String graphName = graphPath;
					if (graphName.endsWith(File.separator))
						graphName = graphName.substring(0, graphName.length() - 1);
					int lastSlash = graphName.lastIndexOf(File.separatorChar);
					if (lastSlash >= 0)
						graphName = graphName.substring(lastSlash + 1);
					loadedGraphs.add(new ProcessClusterGraph(graphName, graphsByCluster));
				}
			} catch (Throwable t) {
				fail(t);
			}
		}
	}

	private class MergeThread extends Thread {
		private final int index = THREAD_INDEX++;
		private final MergeTwoGraphs executor = new MergeTwoGraphs(commonOptions);
		private final MergeDebugLog debugLog = new MergeDebugLog();

		@Override
		public void run() {
			try {
				while (true) {
					MergePair merge = getNextMergePair();
					if (merge == null)
						break;

					Log.sharedLog("Thread %d starting merge %s", index,
							merge.logFilename.substring(0, merge.logFilename.length() - ".merge.log".length()));

					File logFile = new File(logDir, merge.logFilename);
					Log.clearThreadOutputs();
					Log.addThreadOutput(logFile);

					GraphMergeCandidate leftCandidate = new GraphMergeCandidate.LoadedClusters(merge.left.name,
							merge.left.clusters, debugLog);
					GraphMergeCandidate rightCandidate = new GraphMergeCandidate.LoadedClusters(merge.right.name,
							merge.right.clusters, debugLog);
					executor.merge(leftCandidate, rightCandidate, logFile);
				}
			} catch (Throwable t) {
				fail(t);
			}
		}
	}

	private static void fail(Throwable t) {
		Log.log("\t@@@@ Merge failed with %s @@@@", t.getClass().getSimpleName());
		Log.log(t);
		System.err.println(String.format("!! Merge failed with %s !!", t.getClass().getSimpleName()));
	}

	private static final String MAIN_LOG_FILENAME = "rr.log";

	private static int THREAD_INDEX = 0;

	private final OptionArgumentMap.StringOption logPathOption = OptionArgumentMap.createStringOption('l', true);
	private final OptionArgumentMap.StringOption threadCountOption = OptionArgumentMap.createStringOption('t');
	private final OptionArgumentMap.BooleanOption unityOption = OptionArgumentMap.createBooleanOption('u');
	private final OptionArgumentMap.BooleanOption clusterGraphOption = OptionArgumentMap.createBooleanOption('y');

	private File logDir;
	private final ArgumentStack args;
	private final CommonMergeOptions commonOptions;

	private final List<String> graphPaths = new ArrayList<String>();
	private final List<MergePair> mergePairs = new ArrayList<MergePair>();

	public RoundRobinMerge(ArgumentStack args) {
		this.args = args;
		commonOptions = new CommonMergeOptions(args, logPathOption, threadCountOption, unityOption);
	}

	void run() {
		boolean parsingArguments = true;

		try {
			commonOptions.parseOptions();

			if (clusterGraphOption.getValue()) {
				throw new UnsupportedOperationException(String.format(
						"%s merge does not yet support cluster graphs :-(", getClass().getSimpleName()));
			}

			logDir = new File(logPathOption.getValue());
			if (logDir.exists()) {
				throw new IllegalStateException(String.format("Log directory %s already exists! Exiting now.",
						logDir.getAbsolutePath()));
			}
			logDir.mkdir();
			File mainLogFile = LogFile.create(new File(logDir, "rr.log"), LogFile.CollisionMode.ERROR,
					LogFile.NoSuchPathMode.ERROR);
			Log.addOutput(mainLogFile);
			System.out.println("Logging to " + mainLogFile.getAbsolutePath());

			parsingArguments = false;

			commonOptions.initializeMerge();

			long startTime = System.currentTimeMillis();

			String graphListPath = args.pop();
			graphPaths.addAll(loadGraphList(graphListPath));

			int threadCount = Integer.parseInt(threadCountOption.getValue());
			int partitionSize = graphPaths.size() / threadCount;
			List<ProcessClusterGraph> graphs = new ArrayList<ProcessClusterGraph>();

			{
				Log.log("Starting %d threads to load %d graphs (~%d each).", threadCount, graphPaths.size(),
						partitionSize);

				List<GraphLoadThread> threads = new ArrayList<GraphLoadThread>();
				for (int i = 0; i < threadCount; i++) {
					GraphLoadThread thread = new GraphLoadThread();
					thread.start();
					threads.add(thread);
				}

				for (GraphLoadThread thread : threads) {
					thread.join();
					graphs.addAll(thread.loadedGraphs);
				}
			}

			long mergeStart = System.currentTimeMillis();
			Log.log("Loaded %d graphs in %f seconds.", graphs.size(), ((mergeStart - startTime) / 1000.));

			{
				expandMergePairs(graphs);
				List<MergeThread> threads = new ArrayList<MergeThread>();
				int mergeCount = mergePairs.size();
				partitionSize = mergeCount / threadCount;

				Log.log("Starting %d threads to process %d merges (~%d each)", threadCount, mergeCount, partitionSize);

				for (int i = 0; i < threadCount; i++) {
					MergeThread thread = new MergeThread();
					thread.start();
					threads.add(thread);
				}

				for (MergeThread thread : threads) {
					thread.join();
				}

				Log.log("\nRound-robin merge of %d graphs (%d merges) on %d threads in %f seconds.", graphs.size(),
						mergeCount, threadCount, ((System.currentTimeMillis() - mergeStart) / 1000.));
			}

		} catch (Throwable t) {
			if (parsingArguments) {
				t.printStackTrace();
				printUsageAndExit();
			} else {
				fail(t);
			}
		}
	}

	private void expandMergePairs(List<ProcessClusterGraph> graphs) {
		Set<String> logFilenames = new HashSet<String>();
		boolean includeUnityMerges = unityOption.getValue();
		String disambiguator = "";
		int disambiguatorIndex = 0;
		for (int i = 0; i < graphs.size(); i++) {
			for (int j = i; j < graphs.size(); j++) {
				if ((i == j) && !includeUnityMerges)
					continue;

				ProcessClusterGraph left = graphs.get(i);
				ProcessClusterGraph right = graphs.get(j);

				String logFilename = null;
				do {
					disambiguatorIndex++;
					logFilename = String.format("%s~%s%s.merge.log", left.name, right.name, disambiguator);
					disambiguator = "_" + disambiguatorIndex;
				} while (logFilenames.contains(logFilename));

				logFilenames.add(logFilename);

				disambiguator = "";
				disambiguatorIndex = 0;

				mergePairs.add(new MergePair(graphs.get(i), graphs.get(j), logFilename));
			}
		}
	}

	private Collection<String> loadGraphList(String listPath) throws IOException {
		File listFile = new File(listPath);
		if (!listFile.exists())
			throw new IllegalStateException(String.format("The graph list file %s does not exist!",
					listFile.getAbsolutePath()));

		Set<String> graphPaths = new HashSet<String>();
		BufferedReader in = new BufferedReader(new FileReader(listFile));
		try {
			while (in.ready()) {
				String graphPath = in.readLine();
				if (graphPath.length() > 0) {
					if (graphPaths.contains(graphPath))
						throw new IllegalStateException(String.format(
								"Found multiple runs in directory %s. Exiting now.", graphPath));
					graphPaths.add(graphPath);
				}
			}
		} finally {
			in.close();
		}

		return graphPaths;
	}

	private synchronized String getNextGraphPath() {
		if (graphPaths.isEmpty())
			return null;
		return graphPaths.remove(graphPaths.size() - 1);
	}

	private synchronized MergePair getNextMergePair() {
		if (mergePairs.isEmpty())
			return null;
		return mergePairs.remove(mergePairs.size() - 1);
	}

	private void printUsageAndExit() {
		System.out
				.println(String
						.format("Usage: %s -l <log-path> [ -c <cluster-name>,... ]\n\t[ -d <crowd-safe-common-dir> ][ -t <thread-count> ]\n\t[ -u (include unity merge) ] <run-list-file>",
								getClass().getSimpleName()));
		System.exit(1);
	}

	public static void main(String[] args) {
		RoundRobinMerge merge = new RoundRobinMerge(new ArgumentStack(args));
		merge.run();
	}
}