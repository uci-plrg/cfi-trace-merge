package edu.uci.eecs.crowdsafe.merge.graph.main;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.log.LogFile;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.writer.ClusterGraphWriter;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDataSink;
import edu.uci.eecs.crowdsafe.graph.main.CommonMergeOptions;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeCandidate;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeDebugLog;
import edu.uci.eecs.crowdsafe.merge.graph.report.AnonymousModuleReportGenerator;
import edu.uci.eecs.crowdsafe.merge.graph.report.ExecutionReport;
import edu.uci.eecs.crowdsafe.merge.graph.report.ModuleReportGenerator;
import edu.uci.eecs.crowdsafe.merge.graph.report.ProgramEventFrequencies;

public class ExecutionReporter {

	public interface MergeCompletion {
		void mergeCompleted(ClusterGraph mergedGraph) throws IOException;
	}

	public static class WriteCompletedGraphs implements MergeCompletion {
		private final ClusterTraceDataSink dataSink;
		private final String filenameFormat;

		public WriteCompletedGraphs(ClusterTraceDataSink dataSink, String filenameFormat) {
			this.dataSink = dataSink;
			this.filenameFormat = filenameFormat;
		}

		@Override
		public void mergeCompleted(ClusterGraph mergedGraph) throws IOException {
			dataSink.addCluster(mergedGraph.graph.cluster, filenameFormat);
			ClusterGraphWriter writer = new ClusterGraphWriter(mergedGraph, dataSink);
			writer.writeGraph();
		}
	}

	public static class IgnoreMergeCompletion implements MergeCompletion {
		@Override
		public void mergeCompleted(ClusterGraph mergedGraph) throws IOException {
		}
	}

	private static double elapsedTime(long start) {
		return (System.currentTimeMillis() - start) / 1000.0;
	}

	private static final OptionArgumentMap.StringOption executionGraphOption = OptionArgumentMap
			.createStringOption('e');
	private static final OptionArgumentMap.StringOption datasetOption = OptionArgumentMap.createStringOption('d');
	private static final OptionArgumentMap.StringOption statisticsOption = OptionArgumentMap.createStringOption('s');
	private static final OptionArgumentMap.StringOption logFilenameOption = OptionArgumentMap.createStringOption('l',
			"reporter.log"); // or the app name?
	private static final OptionArgumentMap.StringOption reportFilenameOption = OptionArgumentMap.createStringOption(
			'f', "execution.log"); // or the app name?
	private static final OptionArgumentMap.BooleanOption stdoutOption = OptionArgumentMap.createBooleanOption('o',
			false);

	private final CommonMergeOptions options;
	private final ClusterHashMergeDebugLog debugLog = new ClusterHashMergeDebugLog();

	private ProgramEventFrequencies.ProgramPropertyReader programEventFrequencies;

	private long start;

	public ExecutionReporter(CommonMergeOptions options) {
		this.options = options;
	}

	void run(ArgumentStack args, int iterationCount) {
		boolean parsingArguments = true;

		try {
			options.parseOptions();

			File logFile = LogFile.create(logFilenameOption.getValue(), LogFile.CollisionMode.OVERWRITE,
					LogFile.NoSuchPathMode.SKIP);
			if (logFile != null)
				Log.addOutput(logFile);
			if (stdoutOption.getValue())
				Log.addOutput(System.out);

			File reportFile = LogFile.create(reportFilenameOption.getValue(), LogFile.CollisionMode.AVOID,
					LogFile.NoSuchPathMode.ERROR);
			System.out.println("Generating report file " + reportFile.getAbsolutePath());

			File statisticsFile = new File(statisticsOption.getValue());
			Properties statisticsProperties = new Properties();
			statisticsProperties.load(new FileReader(statisticsFile));
			programEventFrequencies = new ProgramEventFrequencies.ProgramPropertyReader(statisticsProperties);

			String leftPath = executionGraphOption.getValue();
			String rightPath = datasetOption.getValue();

			if (args.size() > 0)
				Log.log("Ignoring %d extraneous command-line arguments", args.size());

			parsingArguments = false;

			Log.log("Execution report for %s, based on dataset %s", leftPath, rightPath);

			options.initializeGraphEnvironment();

			GraphMergeCandidate leftCandidate = loadMergeCandidate(leftPath);
			GraphMergeCandidate rightCandidate = (leftPath.equals(rightPath) ? leftCandidate
					: loadMergeCandidate(rightPath));

			start = System.currentTimeMillis();

			ExecutionReport report = generateReport(leftCandidate, rightCandidate);
			Log.log("\n > Sorting report entries at %.3f < \n", elapsedTime(start));
			report.sort();
			Log.log("\n > Printing report entries at %.3f < \n", elapsedTime(start));
			report.print(reportFile);

			// Log.log("The whole program has %d indirects with %d distinct targets",
			// programEventFrequencies.getTotalIndirectCount(),
			// programEventFrequencies.getUniqueIndirectTargetCount());
			Log.log("\n > Report complete at %.3f < \n", elapsedTime(start));
		} catch (Log.OutputException e) {
			e.printStackTrace();
		} catch (Throwable t) {
			if (parsingArguments) {
				t.printStackTrace();
				printUsageAndExit();
			} else {
				Log.log("\t@@@@ Execution report failed with %s @@@@", t.getClass().getSimpleName());
				Log.log(t);
				System.err
						.println(String.format("!! Execution report failed with %s !!", t.getClass().getSimpleName()));
			}
		}
	}

	@SuppressWarnings("unchecked")
	ExecutionReport generateReport(GraphMergeCandidate leftData, GraphMergeCandidate rightData) throws IOException {
		ExecutionReport report = new ExecutionReport(programEventFrequencies);
		List<ModuleGraphCluster<ClusterNode<?>>> leftAnonymousGraphs = new ArrayList<ModuleGraphCluster<ClusterNode<?>>>();
		List<ModuleGraphCluster<ClusterNode<?>>> rightAnonymousGraphs = new ArrayList<ModuleGraphCluster<ClusterNode<?>>>();

		Log.log("Reporting %d represented clusters", leftData.getRepresentedClusters().size());

		// compile program events from the execution
		for (AutonomousSoftwareDistribution leftCluster : leftData.getRepresentedClusters()) {
			if (leftCluster.isAnonymous()) {
				Log.log("\n > Loading left anonymous cluster %s at %.3f < \n", leftCluster.name, elapsedTime(start));
				leftAnonymousGraphs.add((ModuleGraphCluster<ClusterNode<?>>) leftData.getClusterGraph(leftCluster));
				continue;
			}

			Log.log("\n > Loading left static cluster %s at %.3f < \n", leftCluster.name, elapsedTime(start));

			ClusterGraph leftGraph = new ClusterGraph(
					(ModuleGraphCluster<ClusterNode<?>>) leftData.getClusterGraph(leftCluster));
			ClusterGraph rightGraph = null;
			if (rightData.getRepresentedClusters().contains(leftCluster)) {
				Log.log("\n > Loading right static cluster %s at %.3f < \n", leftCluster.name, elapsedTime(start));
				rightGraph = new ClusterGraph(
						(ModuleGraphCluster<ClusterNode<?>>) rightData.getClusterGraph(leftCluster));
				Log.log("\n > Counting metadata for right cluster %s at %.3f < \n", leftCluster.name,
						elapsedTime(start));
			} else {
				Log.log("\n > Skipping right static cluster %s because it is not represented among %d dataset modules < \n",
						leftCluster.name, rightData.getRepresentedClusters().size());
			}
			Log.log("\n > Generating report for static cluster %s at %.3f < \n", leftCluster.name, elapsedTime(start));
			ModuleReportGenerator.addModuleReportEntries(report, leftGraph, rightGraph);
			System.gc();
		}

		for (AutonomousSoftwareDistribution rightCluster : rightData.getRepresentedClusters()) {
			if (rightCluster.isAnonymous()) {
				Log.log("\n > Loading right anonymous cluster %s at %.3f < \n", rightCluster.name, elapsedTime(start));
				rightAnonymousGraphs.add((ModuleGraphCluster<ClusterNode<?>>) rightData.getClusterGraph(rightCluster));
			}
		}

		Log.log("\n > Generating report for dynamic code at %.3f < \n", elapsedTime(start));
		AnonymousModuleReportGenerator.addAnonymousReportEntries(report, leftData, rightData, leftAnonymousGraphs,
				rightAnonymousGraphs);

		Log.log("\n > All report entries generated at %.3f < \n", elapsedTime(start));
		return report;
	}

	private GraphMergeCandidate loadMergeCandidate(String path) throws IOException {
		File directory = new File(path);
		if (!(directory.exists() && directory.isDirectory())) {
			Log.log("Illegal argument '" + directory + "'; no such directory.");
			printUsageAndExit();
		}

		GraphMergeCandidate candidate = new GraphMergeCandidate.Cluster(directory, debugLog);
		candidate.loadData();
		return candidate;
	}

	private void printUsageAndExit() {
		System.out.println("Usage:");
		System.out.println(String.format("%s: -e <execution-graph> -d <dataset> -f <report-file>",
				ExecutionReporter.class.getSimpleName()));
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		ExecutionReporter main = new ExecutionReporter(new CommonMergeOptions(stack,
				CommonMergeOptions.crowdSafeCommonDir, executionGraphOption, datasetOption, statisticsOption,
				logFilenameOption, reportFilenameOption, stdoutOption));
		main.run(stack, 1);
		main.toString();
	}
}
