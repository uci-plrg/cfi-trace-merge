package edu.uci.plrg.cfi.x86.merge.graph.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.common.log.LogFile;
import edu.uci.plrg.cfi.common.util.ArgumentStack;
import edu.uci.plrg.cfi.common.util.NameDisambiguator;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap;
import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModuleSet;
import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModule;
import edu.uci.plrg.cfi.x86.graph.data.graph.ModuleGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.anonymous.ApplicationAnonymousGraphs;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ApplicationGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.loader.ModuleGraphLoadSession;
import edu.uci.plrg.cfi.x86.graph.data.results.Graph;
import edu.uci.plrg.cfi.x86.graph.io.modular.ModularTraceDataSink;
import edu.uci.plrg.cfi.x86.graph.io.modular.ModularTraceDataSource;
import edu.uci.plrg.cfi.x86.graph.io.modular.ModularTraceDirectory;
import edu.uci.plrg.cfi.x86.graph.main.CommonMergeOptions;
import edu.uci.plrg.cfi.x86.merge.graph.GraphMergeCandidate;
import edu.uci.plrg.cfi.x86.merge.graph.GraphMergeStrategy;
import edu.uci.plrg.cfi.x86.merge.graph.hash.HashMergeDebugLog;

public class ModularGraphTraining {

	private static class ProcessModuleGraph {
		final String name;
		final Map<ApplicationModule, ModuleGraph<ModuleNode<?>>> modules;

		public ProcessModuleGraph(String name, Map<ApplicationModule, ModuleGraph<ModuleNode<?>>> modules) {
			this.name = name;
			this.modules = modules;
		}
	}

	private static class ModuleTrainingConfiguration {
		ApplicationModule module;
		final File sequenceFile;
		final File moduleLogDir;

		ModuleTrainingConfiguration(ApplicationModule module, File sequenceFile, File moduleLogDir) {
			this.module = module;
			this.sequenceFile = sequenceFile;
			this.moduleLogDir = moduleLogDir;
		}
	}

	private class TrainingThread extends Thread {

		private class TrainingDataset implements GraphMergeCandidate {
			private ApplicationGraph graph;
			private final Graph.Process.Builder summaryBuilder = Graph.Process.newBuilder();

			@Override
			public ModuleGraph<?> getModuleGraph(ApplicationModule module) throws IOException {
				return graph.graph;
			}

			@Override
			public ApplicationAnonymousGraphs getAnonymousGraph() throws IOException {
				throw new UnsupportedOperationException("Not implemented");
			}

			@Override
			public Collection<ApplicationModule> getRepresentedModules() {
				return Collections.singleton(currentConfiguration.module);
			}

			@Override
			public void loadData() throws IOException {
				ModuleGraphLoadSession loadSession = new ModuleGraphLoadSession(dataSources.get(datasetIndex));
				graph = new ApplicationGraph(loadSession.loadModuleGraph(currentConfiguration.module));
			}

			@Override
			public String parseTraceName() {
				return "dataset";
			}

			@Override
			public void summarizeModule(ApplicationModule module) {
			}

			@Override
			public Graph.Process summarizeGraph() {
				summaryBuilder.clear().setName("dataset");
				summaryBuilder.addModule(graph.graph.summarize(graph.graph.module.isAnonymous));
				return summaryBuilder.build();
			}
		}

		private class TrainingInstance implements GraphMergeCandidate {
			private ModuleGraph<?> graph;
			private final Graph.Process.Builder summaryBuilder = Graph.Process.newBuilder();

			@Override
			public ModuleGraph<?> getModuleGraph(ApplicationModule module) throws IOException {
				return graph;
			}

			@Override
			public ApplicationAnonymousGraphs getAnonymousGraph() throws IOException {
				throw new UnsupportedOperationException("Not implemented");
			}

			@Override
			public Collection<ApplicationModule> getRepresentedModules() {
				return Collections.singleton(currentConfiguration.module);
			}

			@Override
			public void loadData() throws IOException {
				ModuleGraphLoadSession loadSession = new ModuleGraphLoadSession(dataSources.get(instanceIndex));
				graph = loadSession.loadModuleGraph(currentConfiguration.module);
			}

			@Override
			public String parseTraceName() {
				return runIds.get(instanceIndex);
			}

			@Override
			public void summarizeModule(ApplicationModule module) {
			}

			@Override
			public Graph.Process summarizeGraph() {
				summaryBuilder.clear().setName(parseTraceName());
				summaryBuilder.addModule(graph.summarize(graph.module.isAnonymous));
				return summaryBuilder.build();
			}
		}

		private final int index = THREAD_INDEX++;
		private final MergeTwoGraphs executor = new MergeTwoGraphs(commonOptions);
		private final HashMergeDebugLog debugLog = new HashMergeDebugLog();

		private ModuleTrainingConfiguration currentConfiguration;
		private final TrainingDataset dataset = new TrainingDataset();
		private final TrainingInstance instance = new TrainingInstance();
		private int datasetIndex;
		private int instanceIndex;

		@Override
		public void run() {
			try {
				while (true) {
					currentConfiguration = getNextModule();
					if (currentConfiguration == null)
						break;

					for (datasetIndex = 0; datasetIndex < dataSources.size(); datasetIndex++) {
						if (dataSources.get(datasetIndex).getReprsentedModules().contains(currentConfiguration.module))
							break;
					}

					if (datasetIndex == dataSources.size()) {
						Log.log("Skipping module %s because no runs contain it.", currentConfiguration.module.name);
						continue;
					}

					File logFile = new File(currentConfiguration.moduleLogDir, "dataset.load.log");
					Log.clearThreadOutputs();
					Log.addThreadOutput(logFile);
					dataset.loadData();

					ModularTraceDataSink dataSink = new ModularTraceDirectory(outputDir);
					String filenameFormat = "dataset.%s.%s.%s";
					MergeTwoGraphs.WriteCompletedGraphs completion = new MergeTwoGraphs.WriteCompletedGraphs(dataSink,
							filenameFormat);

					PrintWriter sequenceWriter = new PrintWriter(currentConfiguration.sequenceFile);
					try {
						Log.sharedLog("Thread %d training module %s", index, currentConfiguration.module.name);
						for (instanceIndex = datasetIndex + 1; instanceIndex < dataSources.size(); instanceIndex++) {
							if (!dataSources.get(instanceIndex).getReprsentedModules()
									.contains(currentConfiguration.module))
								continue;

							logFile = new File(currentConfiguration.moduleLogDir, String.format("%s.merge.log",
									runIds.get(instanceIndex)));
							Log.clearThreadOutputs();
							Log.addThreadOutput(logFile);

							instance.loadData();
							sequenceWriter.println(MergeTwoGraphs.getCorrespondingResultsFilename(logFile));
							executor.merge(instance, dataset, strategy, logFile, completion);
						}
					} finally {
						sequenceWriter.flush();
						sequenceWriter.close();
					}
				}
			} catch (Throwable t) {
				fail(t, String.format("\t@@@@ Merge %s on thread %d failed with %s @@@@",
						currentConfiguration.module.name, index, t.getClass().getSimpleName()));
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
	private final OptionArgumentMap.StringOption moduleListOption = OptionArgumentMap.createStringOption('c',
			OptionArgumentMap.OptionMode.REQUIRED);

	private File logDir;
	private File outputDir;
	private final ArgumentStack args;
	private final CommonMergeOptions commonOptions;
	private GraphMergeStrategy strategy;

	private final List<String> moduleNames = new ArrayList<String>();
	private final List<ModularTraceDataSource> dataSources = new ArrayList<ModularTraceDataSource>();
	private final List<String> runIds = new ArrayList<String>();
	private final List<ModuleTrainingConfiguration> trainingConfigurations = new ArrayList<ModuleTrainingConfiguration>();

	public ModularGraphTraining(ArgumentStack args) {
		this.args = args;
		commonOptions = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir,
				CommonMergeOptions.unitModuleOption, logPathOption, threadCountOption, outputDirectoryOption,
				runListOption, moduleListOption);
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

			commonOptions.initializeGraphEnvironment();

			loadModuleList();
			loadDataSources();

			long trainingStart = System.currentTimeMillis();

			{
				for (String moduleName : moduleNames) {
					ApplicationModule module = ApplicationModuleSet.getInstance().establishModuleByFileSystemName(
							moduleName);
					File moduleLogDirectory = new File(logDir, module.name);
					moduleLogDirectory.mkdir();
					File sequenceFile = new File(moduleLogDirectory, "sequence.log");
					trainingConfigurations
							.add(new ModuleTrainingConfiguration(module, sequenceFile, moduleLogDirectory));
				}

				List<TrainingThread> threads = new ArrayList<TrainingThread>();
				int threadCount = Integer.parseInt(threadCountOption.getValue());
				int moduleCount = trainingConfigurations.size();
				int mergeCount = moduleCount * moduleNames.size();
				int partitionSize = mergeCount / threadCount;

				Log.log("Starting %d threads to train %d modules (~%d merges each)", threadCount,
						trainingConfigurations.size(), partitionSize);

				for (int i = 0; i < threadCount; i++) {
					TrainingThread thread = new TrainingThread();
					thread.start();
					threads.add(thread);
				}

				for (TrainingThread thread : threads) {
					thread.join();
				}

				Log.log("\nTraining of %d modules (%d merges) on %d threads in %f seconds.", moduleCount, mergeCount,
						threadCount, ((System.currentTimeMillis() - trainingStart) / 1000.));
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

	private void loadModuleList() throws IOException {
		File listFile = new File(moduleListOption.getValue());
		if (!listFile.exists())
			throw new IllegalStateException(String.format("The module list file %s does not exist!",
					listFile.getAbsolutePath()));

		BufferedReader in = new BufferedReader(new FileReader(listFile));
		try {
			while (in.ready()) {
				String moduleName = in.readLine();
				if (moduleName.length() > 0) {
					moduleNames.add(moduleName);
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

		NameDisambiguator disambiguator = new NameDisambiguator();
		for (String runPath : runPathSet) {
			File runDir = new File(runPath);
			runIds.add(disambiguator.disambiguateName(runDir.getName()));
			ModularTraceDataSource dataSource = new ModularTraceDirectory(runDir).loadExistingFiles();
			dataSources.add(dataSource);
		}
	}

	private synchronized ModuleTrainingConfiguration getNextModule() {
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
		ModularGraphTraining merge = new ModularGraphTraining(new ArgumentStack(args));
		merge.run();
	}
}
