package edu.uci.eecs.crowdsafe.merge.graph.main;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.log.LogFile;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.writer.ClusterGraphWriter;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDataSink;
import edu.uci.eecs.crowdsafe.graph.main.CommonMergeOptions;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeCandidate;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousGraphMergeEngine;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeDebugLog;
import edu.uci.eecs.crowdsafe.merge.graph.report.AnonymousModuleReportGenerator;
import edu.uci.eecs.crowdsafe.merge.graph.report.ExecutionReport;
import edu.uci.eecs.crowdsafe.merge.graph.report.ModuleReportGenerator;

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

	private static final OptionArgumentMap.StringOption executionGraphOption = OptionArgumentMap
			.createStringOption('e');
	private static final OptionArgumentMap.StringOption datasetOption = OptionArgumentMap.createStringOption('d');
	private static final OptionArgumentMap.StringOption reportFilenameOption = OptionArgumentMap.createStringOption(
			'f', "execution.log"); // or the app name?
	private static final OptionArgumentMap.BooleanOption stdoutOption = OptionArgumentMap.createBooleanOption('o',
			false);

	private final CommonMergeOptions options;
	private final ClusterHashMergeDebugLog debugLog = new ClusterHashMergeDebugLog();

	public ExecutionReporter(CommonMergeOptions options) {
		this.options = options;
	}

	void run(ArgumentStack args, int iterationCount) {
		boolean parsingArguments = true;

		try {
			options.parseOptions();

			File reportFile = LogFile.create(reportFilenameOption.getValue(), LogFile.CollisionMode.AVOID,
					LogFile.NoSuchPathMode.ERROR);
			Log.addOutput(reportFile);
			System.out.println("Generating report file " + reportFile.getAbsolutePath());

			if (stdoutOption.getValue())
				Log.addOutput(System.out);

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

			ExecutionReport report = generateReport(leftCandidate, rightCandidate, reportFile);
			report.sort();
			report.print();

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
	ExecutionReport generateReport(GraphMergeCandidate leftData, GraphMergeCandidate rightData, File logFile)
			throws IOException {
		ExecutionReport report = new ExecutionReport();
		List<ModuleGraphCluster<ClusterNode<?>>> leftAnonymousGraphs = new ArrayList<ModuleGraphCluster<ClusterNode<?>>>();
		List<ModuleGraphCluster<ClusterNode<?>>> rightAnonymousGraphs = new ArrayList<ModuleGraphCluster<ClusterNode<?>>>();

		Log.log("Reporting %d represented clusters", leftData.getRepresentedClusters().size());

		for (AutonomousSoftwareDistribution leftCluster : leftData.getRepresentedClusters()) {
			if (leftCluster.isAnonymous()) {
				// cast is ok because tag merge only works on cluster graphs
				leftAnonymousGraphs.add((ModuleGraphCluster<ClusterNode<?>>) leftData.getClusterGraph(leftCluster));
				leftData.summarizeCluster(leftCluster);
				continue;
			}

			if (ConfiguredSoftwareDistributions.getInstance().clusterMode != ConfiguredSoftwareDistributions.ClusterMode.UNIT)
				throw new UnsupportedOperationException(
						"Cluster compatibility has not yet been defined for cluster mode "
								+ ConfiguredSoftwareDistributions.getInstance().clusterMode);

			Log.log("\n > Loading cluster %s < \n", leftCluster.name);

			ClusterGraph leftGraph = new ClusterGraph(
					(ModuleGraphCluster<ClusterNode<?>>) leftData.getClusterGraph(leftCluster));
			ClusterGraph rightGraph = new ClusterGraph(
					(ModuleGraphCluster<ClusterNode<?>>) rightData.getClusterGraph(leftCluster));
			ModuleReportGenerator.addModuleReportEntries(report, leftGraph, rightGraph);
		}

		for (AutonomousSoftwareDistribution rightCluster : rightData.getRepresentedClusters()) {
			if (rightCluster.isAnonymous()) 
				rightAnonymousGraphs.add((ModuleGraphCluster<ClusterNode<?>>) rightData.getClusterGraph(rightCluster));
		}
		AnonymousModuleReportGenerator.addAnonymousReportEntries(report, leftData, rightData, leftAnonymousGraphs, rightAnonymousGraphs);

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
		System.out.println(String.format("%s: -e <execution-graph> -d <dataset> -o <report-file>",
				ExecutionReporter.class.getSimpleName()));
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		ExecutionReporter main = new ExecutionReporter(new CommonMergeOptions(stack,
				CommonMergeOptions.crowdSafeCommonDir, executionGraphOption, datasetOption, reportFilenameOption,
				stdoutOption));
		main.run(stack, 1);
		main.toString();
	}
}