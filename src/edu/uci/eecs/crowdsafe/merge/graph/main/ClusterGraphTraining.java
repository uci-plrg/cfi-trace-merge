package edu.uci.eecs.crowdsafe.merge.graph.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader.ClusterGraphLoadSession;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.writer.ClusterGraphWriter;
import edu.uci.eecs.crowdsafe.common.data.results.Graph;
import edu.uci.eecs.crowdsafe.common.data.results.Graph.Process;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceDataSink;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceDirectory;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.log.LogFile;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeStrategy;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeDebugLog;

public class ClusterGraphTraining {

	private static class ProcessClusterGraph {
		final String name;
		final Map<AutonomousSoftwareDistribution, ModuleGraphCluster<ClusterNode<?>>> clusters;

		public ProcessClusterGraph(String name,
				Map<AutonomousSoftwareDistribution, ModuleGraphCluster<ClusterNode<?>>> clusters) {
			this.name = name;
			this.clusters = clusters;
		}
	}

	private static class ClusterTrainingConfiguration {
		AutonomousSoftwareDistribution cluster;
		final File sequenceFile;
		final File clusterLogDir;

		ClusterTrainingConfiguration(AutonomousSoftwareDistribution cluster, File sequenceFile, File clusterLogDir) {
			this.cluster = cluster;
			this.sequenceFile = sequenceFile;
			this.clusterLogDir = clusterLogDir;
		}
	}

	private class TrainingThread extends Thread {

		private class TrainingDataset implements GraphMergeCandidate {
			private ClusterGraph graph;
			private final Graph.Process.Builder summaryBuilder = Graph.Process.newBuilder();

			@Override
			public ModuleGraphCluster<?> getClusterGraph(AutonomousSoftwareDistribution cluster) throws IOException {
				return graph.graph;
			}

			@Override
			public Collection<AutonomousSoftwareDistribution> getRepresentedClusters() {
				return Collections.singleton(currentConfiguration.cluster);
			}

			@Override
			public void loadData() throws IOException {
				ClusterGraphLoadSession loadSession = new ClusterGraphLoadSession(dataSources.get(datasetIndex));
				graph = new ClusterGraph(loadSession.loadClusterGraph(currentConfiguration.cluster));
			}

			@Override
			public String parseTraceName() {
				return "dataset";
			}

			@Override
			public Process summarizeGraph() {
				summaryBuilder.setName("dataset");
				summaryBuilder.addCluster(graph.graph.summarize());
				return summaryBuilder.build();
			}
		}

		private class TrainingInstance implements GraphMergeCandidate {
			private ModuleGraphCluster<?> graph;
			private final Graph.Process.Builder summaryBuilder = Graph.Process.newBuilder();

			@Override
			public ModuleGraphCluster<?> getClusterGraph(AutonomousSoftwareDistribution cluster) throws IOException {
				return graph;
			}

			@Override
			public Collection<AutonomousSoftwareDistribution> getRepresentedClusters() {
				return Collections.singleton(currentConfiguration.cluster);
			}

			@Override
			public void loadData() throws IOException {
				ClusterGraphLoadSession loadSession = new ClusterGraphLoadSession(dataSources.get(instanceIndex));
				graph = loadSession.loadClusterGraph(currentConfiguration.cluster);
			}

			@Override
			public String parseTraceName() {
				return dataSources.get(instanceIndex).parseTraceName();
			}

			@Override
			public Process summarizeGraph() {
				summaryBuilder.setName(parseTraceName());
				summaryBuilder.addCluster(graph.summarize());
				return summaryBuilder.build();
			}
		}

		private final int index = THREAD_INDEX++;
		private final MergeTwoGraphs executor = new MergeTwoGraphs(commonOptions);
		private final ClusterHashMergeDebugLog debugLog = new ClusterHashMergeDebugLog();

		private ClusterTrainingConfiguration currentConfiguration;
		private final TrainingDataset dataset = new TrainingDataset();
		private final TrainingInstance instance = new TrainingInstance();
		private int datasetIndex;
		private int instanceIndex;

		@Override
		public void run() {
			try {
				while (true) {
					currentConfiguration = getNextCluster();
					if (currentConfiguration == null)
						break;

					for (datasetIndex = 0; datasetIndex < dataSources.size(); datasetIndex++) {
						if (dataSources.get(datasetIndex).getReprsentedClusters()
								.contains(currentConfiguration.cluster))
							break;
					}

					if (datasetIndex == dataSources.size()) {
						Log.log("Skipping cluster %s because no runs contain it.", currentConfiguration.cluster.name);
						continue;
					}

					dataset.loadData();

					Log.sharedLog("Thread %d training cluster %s", index, currentConfiguration.cluster.name);
					for (instanceIndex = datasetIndex + 1; instanceIndex < dataSources.size(); instanceIndex++) {
						if (!dataSources.get(instanceIndex).getReprsentedClusters()
								.contains(currentConfiguration.cluster))
							continue;

						instance.loadData();

						File logFile = new File(currentConfiguration.clusterLogDir, logFilenames.get(instanceIndex));
						Log.clearThreadOutputs();
						Log.addThreadOutput(logFile);

						// TODO: write to the sequence log
						// TODO: results logs are getting crossed up, see e.g. ntdll
						executor.merge(instance, dataset, strategy, logFile);
					}

					ClusterTraceDataSink dataSink = new ClusterTraceDirectory(outputDir);
					String filenameFormat = "dataset.%s.%s.%s";
					dataSink.addCluster(currentConfiguration.cluster, filenameFormat);
					ClusterGraphWriter writer = new ClusterGraphWriter(dataset.graph, dataSink);
					writer.writeGraph();
				}
			} catch (Throwable t) {
				fail(t, String.format("\t@@@@ Merge %s on thread %d failed with %s @@@@",
						currentConfiguration.cluster.name, index, t.getClass().getSimpleName()));
			}
		}
	}

	private static void fail(Throwable t, String sharedLogMessage) {
		Log.sharedLog(sharedLogMessage);
		Log.sharedLog(t);

		Log.log("\t@@@@ Merge failed with %s @@@@", t.getClass().getSimpleName());
		Log.log(t);
		System.err.println(String.format("!! Merge failed with %s !!", t.getClass().getSimpleName()));
	}

	private static final String MAIN_LOG_FILENAME = "train.log";

	private static int THREAD_INDEX = 0;

	private final OptionArgumentMap.StringOption logPathOption = OptionArgumentMap.createStringOption('l',
			OptionArgumentMap.OptionMode.REQUIRED);
	private static final OptionArgumentMap.StringOption strategyOption = OptionArgumentMap.createStringOption('s',
			GraphMergeStrategy.TAG.id);
	private final OptionArgumentMap.StringOption threadCountOption = OptionArgumentMap.createStringOption('t');
	private final OptionArgumentMap.StringOption outputDirectoryOption = OptionArgumentMap.createStringOption('o',
			OptionArgumentMap.OptionMode.REQUIRED);
	private final OptionArgumentMap.StringOption runListOption = OptionArgumentMap.createStringOption('r',
			OptionArgumentMap.OptionMode.REQUIRED);
	private final OptionArgumentMap.StringOption clusterListOption = OptionArgumentMap.createStringOption('c',
			OptionArgumentMap.OptionMode.REQUIRED);

	private File logDir;
	private File outputDir;
	private final ArgumentStack args;
	private final CommonMergeOptions commonOptions;
	private GraphMergeStrategy strategy;

	private final List<String> clusterNames = new ArrayList<String>();
	private final List<ClusterTraceDataSource> dataSources = new ArrayList<ClusterTraceDataSource>();
	private final List<String> logFilenames = new ArrayList<String>();
	private final List<ClusterTrainingConfiguration> trainingConfigurations = new ArrayList<ClusterTrainingConfiguration>();

	public ClusterGraphTraining(ArgumentStack args) {
		this.args = args;
		commonOptions = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir,
				CommonMergeOptions.unitClusterOption, logPathOption, threadCountOption, outputDirectoryOption,
				runListOption, clusterListOption);
	}

	void run() {
		boolean parsingArguments = true;

		try {
			commonOptions.parseOptions();

			logDir = new File(logPathOption.getValue());
			if (logDir.exists()) {
				throw new IllegalStateException(String.format("Log directory %s already exists! Exiting now.",
						logDir.getAbsolutePath()));
			}
			logDir.mkdir();
			File mainLogFile = LogFile.create(new File(logDir, MAIN_LOG_FILENAME), LogFile.CollisionMode.ERROR,
					LogFile.NoSuchPathMode.ERROR);
			Log.addOutput(mainLogFile);
			System.out.println("Logging to " + mainLogFile.getAbsolutePath());

			strategy = GraphMergeStrategy.forId(strategyOption.getValue());
			if (strategy == null)
				Log.log("Unknown merge strategy %s. Exiting now.", strategyOption.getValue());

			outputDir = new File(outputDirectoryOption.getValue());

			parsingArguments = false;

			commonOptions.initializeMerge();

			loadClusterList();
			loadDataSources();

			long trainingStart = System.currentTimeMillis();

			{
				for (String clusterName : clusterNames) {
					AutonomousSoftwareDistribution cluster = ConfiguredSoftwareDistributions.getInstance()
							.establishCluster(clusterName);
					File logDirectory = new File(logDir, cluster.name);
					logDirectory.mkdir();
					File sequenceFile = new File(logDir, String.format("%s.sequence.log", cluster.name));
					trainingConfigurations.add(new ClusterTrainingConfiguration(cluster, sequenceFile, logDirectory));
				}

				List<TrainingThread> threads = new ArrayList<TrainingThread>();
				int threadCount = Integer.parseInt(threadCountOption.getValue());
				int mergeCount = trainingConfigurations.size() * clusterNames.size();
				int partitionSize = mergeCount / threadCount;

				Log.log("Starting %d threads to train %d clusters (~%d merges each)", threadCount,
						trainingConfigurations.size(), partitionSize);

				for (int i = 0; i < threadCount; i++) {
					TrainingThread thread = new TrainingThread();
					thread.start();
					threads.add(thread);
				}

				for (TrainingThread thread : threads) {
					thread.join();
				}

				Log.log("\nTraining of %d clusters (%d merges) on %d threads in %f seconds.",
						trainingConfigurations.size(), mergeCount, threadCount,
						((System.currentTimeMillis() - trainingStart) / 1000.));
			}

		} catch (Throwable t) {
			if (parsingArguments) {
				t.printStackTrace();
				printUsageAndExit();
			} else {
				fail(t, "Round-robin main thread failed.");
			}
		}
	}

	private void loadClusterList() throws IOException {
		File listFile = new File(clusterListOption.getValue());
		if (!listFile.exists())
			throw new IllegalStateException(String.format("The cluster list file %s does not exist!",
					listFile.getAbsolutePath()));

		BufferedReader in = new BufferedReader(new FileReader(listFile));
		try {
			while (in.ready()) {
				String clusterName = in.readLine();
				if (clusterName.length() > 0) {
					clusterNames.add(clusterName);
				}
			}
		} finally {
			in.close();
		}
	}

	private void loadDataSources() throws IOException {
		File listFile = new File(runListOption.getValue());
		if (!listFile.exists())
			throw new IllegalStateException(String.format("The run list file %s does not exist!",
					listFile.getAbsolutePath()));

		Set<String> runPathSet = new HashSet<String>();
		BufferedReader in = new BufferedReader(new FileReader(listFile));
		try {
			while (in.ready()) {
				String runPath = in.readLine();
				if (runPath.length() > 0) {
					if (runPathSet.contains(runPath))
						throw new IllegalStateException(String.format(
								"Found multiple runs in directory %s. Exiting now.", runPath));
					runPathSet.add(runPath);
				}
			}
		} finally {
			in.close();
		}

		for (String runPath : runPathSet) {
			File runDir = new File(runPath);
			logFilenames.add(String.format("%s.merge.log", runDir.getName()));
			ClusterTraceDataSource dataSource = new ClusterTraceDirectory(runDir).loadExistingFiles();
			dataSources.add(dataSource);
		}
	}

	private synchronized ClusterTrainingConfiguration getNextCluster() {
		if (trainingConfigurations.isEmpty())
			return null;
		return trainingConfigurations.remove(trainingConfigurations.size() - 1);
	}

	private void printUsageAndExit() {
		System.out
				.println(String
						.format("Usage: %s -l <log-path> [ -c <cluster-name>,... ]\n\t[ -d <crowd-safe-common-dir> ][ -t <thread-count> ]\n\t[ -u (include unity merge) ] <run-list-file>",
								getClass().getSimpleName()));
		System.exit(1);
	}

	public static void main(String[] args) {
		ClusterGraphTraining merge = new ClusterGraphTraining(new ArgumentStack(args));
		merge.run();
	}
}
