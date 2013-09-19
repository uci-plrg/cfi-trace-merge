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
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.loader.ProcessGraphLoadSession;
import edu.uci.eecs.crowdsafe.common.datasource.execution.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.execution.ExecutionTraceDirectory;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.log.LogFile;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.merge.graph.MergeDebugLog;

public class RoundRobinMerge {

	private static class MergePair {
		final ProcessExecutionGraph left;
		final ProcessExecutionGraph right;
		final String logFilename;

		MergePair(ProcessExecutionGraph left, ProcessExecutionGraph right, String logFilename) {
			this.left = left;
			this.right = right;
			this.logFilename = logFilename;
		}
	}

	private class GraphLoadThread extends Thread {
		private final ProcessGraphLoadSession session = new ProcessGraphLoadSession();
		private final MergeDebugLog debugLog = new MergeDebugLog();
		private final List<String> workList;
		private final List<ProcessExecutionGraph> graphs = new ArrayList<ProcessExecutionGraph>();

		GraphLoadThread(List<String> workList) {
			this.workList = workList;
		}

		@Override
		public void run() {
			try {
				for (String graphPath : workList) {
					ExecutionTraceDataSource dataSource = new ExecutionTraceDirectory(new File(graphPath),
							ProcessExecutionGraph.EXECUTION_GRAPH_FILE_TYPES);
					graphs.add(session.loadGraph(dataSource, debugLog));
				}
			} catch (Throwable t) {
				fail(t);
			}
		}
	}

	private class MergeThread extends Thread {
		private final MergeTwoGraphs executor;
		private final MergeDebugLog debugLog = new MergeDebugLog();
		private final List<MergePair> workList;

		MergeThread(List<MergePair> workList) {
			this.workList = workList;

			executor = new MergeTwoGraphs(commonOptions);
		}

		@Override
		public void run() {
			try {
				for (MergePair merge : workList) {
					File logFile = new File(logDir, merge.logFilename);
					Log.clearThreadOutputs();
					Log.addThreadOutput(logFile);

					GraphMergeCandidate leftCandidate = new GraphMergeCandidate.Execution(merge.left, debugLog);
					GraphMergeCandidate rightCandidate = new GraphMergeCandidate.Execution(merge.right, debugLog);
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

	private final OptionArgumentMap.StringOption logPathOption = OptionArgumentMap.createStringOption('l', true);
	private final OptionArgumentMap.StringOption threadCountOption = OptionArgumentMap.createStringOption('t');
	private final OptionArgumentMap.BooleanOption unityOption = OptionArgumentMap.createBooleanOption('u');
	private final OptionArgumentMap.BooleanOption clusterGraphOption = OptionArgumentMap.createBooleanOption('y');

	private File logDir;
	private final ArgumentStack args;
	private final CommonMergeOptions commonOptions;

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
			MergeDebugLog debugLog = new MergeDebugLog();

			String graphListPath = args.pop();
			List<String> graphPaths = new ArrayList<String>(loadGraphList(graphListPath));

			int threadCount = Integer.parseInt(threadCountOption.getValue());
			int partitionSize = graphPaths.size() / threadCount;
			List<ProcessExecutionGraph> graphs = new ArrayList<ProcessExecutionGraph>();

			{
				Log.log("Starting %d threads to load ~%d graphs each.", threadCount, partitionSize);

				List<GraphLoadThread> threads = new ArrayList<GraphLoadThread>();
				Collections.shuffle(graphPaths);
				ProcessGraphLoadSession loadSession = new ProcessGraphLoadSession();
				for (int i = 0; i < threadCount; i++) {
					int start = i * partitionSize;
					int end = (i + 1) * partitionSize;
					if (end > graphPaths.size())
						end = graphPaths.size();

					List<String> threadWorkList = graphPaths.subList(start, end);
					GraphLoadThread thread = new GraphLoadThread(threadWorkList);
					thread.start();
					threads.add(thread);
				}

				for (GraphLoadThread thread : threads) {
					thread.join();
					graphs.addAll(thread.graphs);
				}
			}

			long mergeStart = System.currentTimeMillis();
			Log.log("Loaded %d graphs in %f seconds.", graphs.size(), ((mergeStart - startTime) / 1000.));

			{
				List<MergePair> workList = expandWorkList(graphs);
				List<MergeThread> threads = new ArrayList<MergeThread>();

				Log.log("Starting %d threads to process ~%d merges each", threadCount, partitionSize);

				for (int i = 0; i < threadCount; i++) {
					int start = i * partitionSize;
					int end = (i + 1) * partitionSize;
					if (end > workList.size())
						end = workList.size();

					List<MergePair> threadWorkList = workList.subList(start, end);
					MergeThread thread = new MergeThread(threadWorkList);
					thread.start();
					threads.add(thread);
				}

				for (MergeThread thread : threads) {
					thread.join();
				}
			}

			Log.log("\nRound-robin merge of %d graphs (%d merges) on %d threads in %f seconds.", graphs.size(),
					graphPaths.size(), threadCount, ((System.currentTimeMillis() - mergeStart) / 1000.));

		} catch (Throwable t) {
			if (parsingArguments) {
				t.printStackTrace();
				printUsageAndExit();
			} else {
				fail(t);
			}
		}
	}

	private List<MergePair> expandWorkList(List<ProcessExecutionGraph> graphs) {
		List<MergePair> workList = new ArrayList<MergePair>();
		Collections.shuffle(graphs);

		Set<String> logFilenames = new HashSet<String>();
		boolean includeUnityMerges = unityOption.getValue();
		String disambiguator = "";
		int disambiguatorIndex = 0;
		for (int i = 0; i < graphs.size(); i++) {
			for (int j = i; j < graphs.size(); j++) {
				if ((i == j) && !includeUnityMerges)
					continue;

				ProcessExecutionGraph left = graphs.get(i);
				ProcessExecutionGraph right = graphs.get(j);

				String logFilename = null;
				do {
					disambiguatorIndex++;
					logFilename = String.format("%s.%d~%s.%d%s.merge.log", left.dataSource.getProcessName(),
							left.dataSource.getProcessId(), right.dataSource.getProcessName(),
							right.dataSource.getProcessId(), disambiguator);
					disambiguator = "_" + disambiguatorIndex;
				} while (logFilenames.contains(logFilename));
				disambiguator = "";
				disambiguatorIndex = 0;

				workList.add(new MergePair(graphs.get(i), graphs.get(j), logFilename));
			}
		}

		return workList;
	}

	private Collection<String> loadGraphList(String listPath) throws IOException {
		File listFile = new File(listPath);
		if (!listFile.exists())
			throw new IllegalStateException(String.format("The graph list file %s does not exist!",
					listFile.getAbsolutePath()));

		BufferedReader in = new BufferedReader(new FileReader(listFile));
		Set<String> graphPaths = new HashSet<String>();
		while (in.ready()) {
			String graphPath = in.readLine();
			if (graphPath.length() > 0) {
				if (graphPaths.contains(graphPath))
					throw new IllegalStateException(String.format("Found multiple runs in directory %s. Exiting now.",
							graphPath));
				graphPaths.add(graphPath);
			}
		}

		return graphPaths;
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
