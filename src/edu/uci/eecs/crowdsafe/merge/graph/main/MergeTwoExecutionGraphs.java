package edu.uci.eecs.crowdsafe.merge.graph.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumSet;

import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.loader.ProcessGraphLoadSession;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDirectory;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceStreamType;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.log.LogFile;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.merge.graph.ClusterMergeSession;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeDebug;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeResults;

public class MergeTwoExecutionGraphs {

	private static void printUsageAndExit() {
		System.out
				.println(String
						.format("Usage: %s [ -c <cluster-name>,... ] [ -d <crowd-safe-common-dir> ] <left-trace-dir> <right-trace-dir> [<log-output>]",
								MergeTwoExecutionGraphs.class.getSimpleName()));
		System.exit(1);
	}

	static void run(ArgumentStack args, int iterationCount) {
		boolean parsingArguments = true;

		try {
			CommonMergeOptions commonOptions = new CommonMergeOptions(args, logFilename);
			commonOptions.parseOptions();

			String leftPath = args.pop();
			String rightPath = args.pop();

			File logFile = null;
			if (logFilename.getValue() != null) {
				logFile = LogFile.create(logFilename.getValue(), LogFile.CollisionMode.AVOID,
						LogFile.NoSuchPathMode.ERROR);
				Log.addOutput(logFile);
				System.out.println("Logging to " + logFile.getAbsolutePath());
			} else {
				System.out.println("Logging to system out");
				Log.addOutput(System.out);
			}

			parsingArguments = false;

			commonOptions.initializeMerge();

			File leftRun = new File(leftPath);
			File rightRun = new File(rightPath);

			if (!(leftRun.exists() && leftRun.isDirectory())) {
				Log.log("Illegal argument '" + leftPath + "'; no such directory.");
				printUsageAndExit();
			}
			if (!(rightRun.exists() && rightRun.isDirectory())) {
				Log.log("Illegal argument '" + rightPath + "'; no such directory.");
				printUsageAndExit();
			}

			ProcessTraceDataSource leftDataSource = new ProcessTraceDirectory(leftRun,
					ProcessExecutionGraph.EXECUTION_GRAPH_FILE_TYPES);
			ProcessTraceDataSource rightDataSource = new ProcessTraceDirectory(rightRun,
					ProcessExecutionGraph.EXECUTION_GRAPH_FILE_TYPES);

			long start = System.currentTimeMillis();
			GraphMergeDebug debugLog = new GraphMergeDebug();

			ProcessGraphLoadSession loadSession = new ProcessGraphLoadSession();
			Log.log("Loading graph %s", leftRun.getAbsolutePath());
			ProcessExecutionGraph leftGraph = loadSession.loadGraph(leftDataSource, debugLog);
			Log.log("Loading graph %s", rightRun.getAbsolutePath());
			ProcessExecutionGraph rightGraph = loadSession.loadGraph(rightDataSource, debugLog);

			Log.log("\nGraph loaded in %f seconds.", ((System.currentTimeMillis() - start) / 1000.));

			if (iterationCount > 1)
				System.err
						.println(String.format(" *** Warning: entering loop of %d merge iterations!", iterationCount));

			for (int i = 0; i < iterationCount; i++) {
				MergeTwoExecutionGraphs main = new MergeTwoExecutionGraphs(commonOptions, debugLog);
				main.merge(leftGraph, rightGraph, logFile);
			}
		} catch (Throwable t) {
			if (parsingArguments) {
				t.printStackTrace();
				printUsageAndExit();
			} else {
				Log.log("\t@@@@ Merge failed with %s @@@@", t.getClass().getSimpleName());
				Log.log(t);
				System.err.println(String.format("!! Merge failed with %s !!", t.getClass().getSimpleName()));
			}
		}
	}

	private static final OptionArgumentMap.StringOption logFilename = OptionArgumentMap.createStringOption('l');

	private final CommonMergeOptions options;
	private final GraphMergeDebug debugLog;

	MergeTwoExecutionGraphs(CommonMergeOptions options, GraphMergeDebug debugLog) {
		this.options = options;
		this.debugLog = debugLog;
	}

	void merge(ProcessExecutionGraph leftGraph, ProcessExecutionGraph rightGraph, File logFile) throws IOException {
		GraphMergeResults results = new GraphMergeResults(leftGraph, rightGraph);

		long mergeStart = System.currentTimeMillis();

		try {
			for (ModuleGraphCluster leftCluster : leftGraph.getAutonomousClusters()) {
				if (!options.includeCluster(leftCluster.distribution))
					continue;

				ModuleGraphCluster rightCluster = rightGraph.getModuleGraphCluster(leftCluster.distribution);
				if (rightCluster == null) {
					Log.log("Skipping cluster %s because it does not appear in the right side.",
							leftCluster.distribution.name);
					continue;
				}

				ClusterMergeSession.mergeTwoGraphs(leftCluster, rightCluster, results, debugLog);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}

		Log.log("\nClusters merged in %f seconds.", ((System.currentTimeMillis() - mergeStart) / 1000.));

		if (logFile != null) {
			String resultsFilename = logFile.getName().substring(0, logFile.getName().lastIndexOf('.'));
			resultsFilename = String.format("%s.results.log", resultsFilename);
			String resultsPath = new File(logFile.getParentFile(), resultsFilename).getPath();
			File resultsFile = LogFile.create(resultsPath, LogFile.CollisionMode.ERROR, LogFile.NoSuchPathMode.ERROR);
			FileOutputStream out = new FileOutputStream(resultsFile);
			results.getResults().writeTo(out);
			out.flush();
			out.close();
		} else {
			Log.log("Results logging skipped.");
		}
	}

	public static void main(String[] args) {
		run(new ArgumentStack(args), 1);
	}
}
