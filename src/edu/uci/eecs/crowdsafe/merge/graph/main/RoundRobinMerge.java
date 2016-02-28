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

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.log.LogFile;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.NameDisambiguator;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.ApplicationAnonymousGraphs;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ApplicationGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.loader.ModuleGraphLoadSession;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceDirectory;
import edu.uci.eecs.crowdsafe.graph.main.CommonMergeOptions;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeCandidate;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeStrategy;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashMergeDebugLog;

public class RoundRobinMerge {

	private static class ProcessModuleGraph {
		final String name;
		final Map<ApplicationModule, ModuleGraph<ModuleNode<?>>> modules;
		final ApplicationAnonymousGraphs anonymousGraphs;

		public ProcessModuleGraph(String name, Map<ApplicationModule, ModuleGraph<ModuleNode<?>>> modules,
				ApplicationAnonymousGraphs anonymousGraphs) {
			this.name = name;
			this.modules = modules;
			this.anonymousGraphs = anonymousGraphs;
		}
	}

	private static class MergePair {
		final ProcessModuleGraph left;
		final ProcessModuleGraph right;
		final String logFilename;

		MergePair(ProcessModuleGraph left, ProcessModuleGraph right, String logFilename) {
			this.left = left;
			this.right = right;
			this.logFilename = logFilename;
		}
	}

	private class GraphLoadThread extends Thread {
		private final HashMergeDebugLog debugLog = new HashMergeDebugLog();
		private final List<ProcessModuleGraph> loadedGraphs = new ArrayList<ProcessModuleGraph>();

		private String currentGraphPath;

		@Override
		public void run() {
			try {
				while (true) {
					currentGraphPath = getNextGraphPath();
					if (currentGraphPath == null)
						break;

					ModularTraceDataSource dataSource = new ModularTraceDirectory(new File(currentGraphPath),
							ApplicationGraph.MODULAR_GRAPH_STREAM_TYPES).loadExistingFiles();
					ModuleGraphLoadSession session = new ModuleGraphLoadSession(dataSource);
					Map<ApplicationModule, ModuleGraph<ModuleNode<?>>> graphsByModule = new HashMap<ApplicationModule, ModuleGraph<ModuleNode<?>>>();
					ApplicationAnonymousGraphs anonymousGraphs = session.loadAnonymousGraphs(debugLog);
					for (ApplicationModule module : dataSource.getReprsentedModules()) {
						graphsByModule.put(module, session.loadModuleGraph(module, debugLog));
					}
					String graphName = currentGraphPath;
					if (graphName.endsWith(File.separator))
						graphName = graphName.substring(0, graphName.length() - 1);
					int lastSlash = graphName.lastIndexOf(File.separatorChar);
					if (lastSlash >= 0)
						graphName = graphName.substring(lastSlash + 1);
					loadedGraphs.add(new ProcessModuleGraph(graphName, graphsByModule, anonymousGraphs));
				}
			} catch (Throwable t) {
				fail(t, String.format("\t@@@@ Loading graph '%s' failed with %s @@@@", currentGraphPath, t.getClass()
						.getSimpleName()));
			}
		}
	}

	private class MergeThread extends Thread {
		private final int index = THREAD_INDEX++;
		private final MergeTwoGraphs executor = new MergeTwoGraphs(commonOptions);
		private final HashMergeDebugLog debugLog = new HashMergeDebugLog();

		private String currentMergeName;

		@Override
		public void run() {
			try {
				MergeTwoGraphs.IgnoreMergeCompletion completion = new MergeTwoGraphs.IgnoreMergeCompletion();

				while (true) {
					MergePair merge = getNextMergePair();
					if (merge == null)
						break;

					currentMergeName = merge.logFilename.substring(0,
							merge.logFilename.length() - ".merge.log".length());
					Log.sharedLog("Thread %d starting merge %s", index, currentMergeName);

					File logFile = new File(logDir, merge.logFilename);
					Log.clearThreadOutputs();
					Log.addThreadOutput(logFile);

					GraphMergeCandidate leftCandidate = new GraphMergeCandidate.LoadedModules(merge.left.name,
							merge.left.modules, merge.left.anonymousGraphs, debugLog);
					GraphMergeCandidate rightCandidate = new GraphMergeCandidate.LoadedModules(merge.right.name,
							merge.right.modules, merge.right.anonymousGraphs, debugLog);
					executor.merge(leftCandidate, rightCandidate, strategy, logFile, completion);
				}
			} catch (Throwable t) {
				fail(t, String.format("\t@@@@ Merge %s on thread %d failed with %s @@@@", currentMergeName, index, t
						.getClass().getSimpleName()));
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

	private static final String MAIN_LOG_FILENAME = "rr.log";

	private static int THREAD_INDEX = 0;

	private final OptionArgumentMap.StringOption logPathOption = OptionArgumentMap.createStringOption('l',
			OptionArgumentMap.OptionMode.REQUIRED);
	private static final OptionArgumentMap.StringOption strategyOption = OptionArgumentMap.createStringOption('s',
			GraphMergeStrategy.TAG.id);
	private final OptionArgumentMap.StringOption threadCountOption = OptionArgumentMap.createStringOption('t');
	private final OptionArgumentMap.BooleanOption unityOption = OptionArgumentMap.createBooleanOption('u');
	private final OptionArgumentMap.BooleanOption moduleGraphOption = OptionArgumentMap.createBooleanOption('y');

	private File logDir;
	private final ArgumentStack args;
	private final CommonMergeOptions commonOptions;
	private GraphMergeStrategy strategy;

	private final List<String> graphPaths = new ArrayList<String>();
	private final List<MergePair> mergePairs = new ArrayList<MergePair>();

	public RoundRobinMerge(ArgumentStack args) {
		this.args = args;
		commonOptions = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir,
				CommonMergeOptions.restrictedModuleOption, CommonMergeOptions.unitModuleOption,
				CommonMergeOptions.excludeModuleOption, logPathOption, threadCountOption, strategyOption, unityOption);
	}

	void run() {
		boolean parsingArguments = true;

		try {
			commonOptions.parseOptions();

			if (moduleGraphOption.getValue()) {
				throw new UnsupportedOperationException(String.format(
						"%s merge does not yet support module graphs :-(", getClass().getSimpleName()));
			}

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

			parsingArguments = false;

			commonOptions.initializeGraphEnvironment();

			long startTime = System.currentTimeMillis();

			String graphListPath = args.pop();
			graphPaths.addAll(loadGraphList(graphListPath));

			int threadCount = Integer.parseInt(threadCountOption.getValue());
			int partitionSize = graphPaths.size() / threadCount;
			List<ProcessModuleGraph> graphs = new ArrayList<ProcessModuleGraph>();

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
				fail(t, "Round-robin main thread failed.");
			}
		}
	}

	private void expandMergePairs(List<ProcessModuleGraph> graphs) {
		Set<String> logFilenames = new HashSet<String>();
		boolean includeUnityMerges = unityOption.getValue();
		NameDisambiguator disambiguator = new NameDisambiguator();
		for (int i = 0; i < graphs.size(); i++) {
			for (int j = i; j < graphs.size(); j++) {
				if ((i == j) && !includeUnityMerges)
					continue;

				ProcessModuleGraph left = graphs.get(i);
				ProcessModuleGraph right = graphs.get(j);

				String logfileBasename = String.format("%s~%s", left.name, right.name);
				String logFilename = String.format("%s.merge.log", disambiguator.disambiguateName(logfileBasename));
				logFilenames.add(logFilename);

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
						.format("Usage: %s -l <log-path> [ -c <module-name>,... ]\n\t[ -d <crowd-safe-common-dir> ][ -t <thread-count> ]\n\t[ -u (include unity merge) ] <run-list-file>",
								getClass().getSimpleName()));
		System.exit(1);
	}

	public static void main(String[] args) {
		RoundRobinMerge merge = new RoundRobinMerge(new ArgumentStack(args));
		merge.run();
	}
}
