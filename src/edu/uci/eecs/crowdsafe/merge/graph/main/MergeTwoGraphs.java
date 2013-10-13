package edu.uci.eecs.crowdsafe.merge.graph.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.writer.ClusterGraphWriter;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceDataSink;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceDirectory;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.log.LogFile;
import edu.uci.eecs.crowdsafe.common.main.CommonMergeOptions;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeStrategy;
import edu.uci.eecs.crowdsafe.merge.graph.MergeResults;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeDebugLog;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeResults;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeSession;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ContextMatchState;
import edu.uci.eecs.crowdsafe.merge.graph.tag.ClusterTagMergeResults;
import edu.uci.eecs.crowdsafe.merge.graph.tag.ClusterTagMergeSession;

public class MergeTwoGraphs {

	private static class DynamicHashMatchEvaluator implements ContextMatchState.Evaluator {
		@Override
		public int evaluateMatch(ContextMatchState state) {
			if (state.isComplete() && (state.getMatchedNodeCount() > 0))
				return 1000;
			else
				return -1;
		}
	}

	static String getCorrespondingResultsFilename(File logFile) {
		String resultsFilename = logFile.getName().substring(0, logFile.getName().lastIndexOf('.'));
		return String.format("%s.results.log", resultsFilename);
	}

	static String getCorrespondingDynamicResultsFilename(File logFile) {
		String resultsFilename = logFile.getName().substring(0, logFile.getName().lastIndexOf('.'));
		return String.format("%s.dynamic.log", resultsFilename);
	}

	private static final OptionArgumentMap.StringOption logFilenameOption = OptionArgumentMap.createStringOption('l');
	private static final OptionArgumentMap.StringOption strategyOption = OptionArgumentMap.createStringOption('s',
			GraphMergeStrategy.TAG.id);
	private static final OptionArgumentMap.StringOption nameOption = OptionArgumentMap.createStringOption('n');
	private static final OptionArgumentMap.StringOption outputOption = OptionArgumentMap.createStringOption('o');
	private static final OptionArgumentMap.StringOption inPlaceOption = OptionArgumentMap.createStringOption('i');
	private static final OptionArgumentMap.BooleanOption verboseOption = OptionArgumentMap.createBooleanOption('v');

	private final CommonMergeOptions options;
	private final ClusterHashMergeDebugLog debugLog = new ClusterHashMergeDebugLog();

	public MergeTwoGraphs(CommonMergeOptions options) {
		this.options = options;
	}

	void run(ArgumentStack args, int iterationCount) {
		boolean parsingArguments = true;

		try {
			options.parseOptions();

			if (verboseOption.getValue())
				Log.addOutput(System.out);

			File logFile = null;
			if (logFilenameOption.getValue() != null) {
				logFile = LogFile.create(logFilenameOption.getValue(), LogFile.CollisionMode.AVOID,
						LogFile.NoSuchPathMode.ERROR);
				Log.addOutput(logFile);
				System.out.println("Logging to " + logFile.getAbsolutePath());
			} else if (!verboseOption.hasValue()) {
				System.out.println("Logging to system out");
				Log.addOutput(System.out);
			}

			if (outputOption.hasValue() != nameOption.hasValue()) {
				Log.log("Options 'name' (-n) and 'output' (-o) may only be used together. Exiting now.");
				System.exit(1);
			}

			String leftPath = args.pop();
			String rightPath;

			if (inPlaceOption.hasValue()) {
				if (outputOption.hasValue()) {
					Log.log("Options 'in-place' (-i) and 'output' (-o) cannot be used together. Exiting now.");
					System.exit(1);
				}
				if (nameOption.hasValue()) {
					Log.log("Option 'name' (-n) cannot be used with 'in-place' (-i). Exiting now.");
					System.exit(1);
				}
				if (inPlaceOption.getValue().startsWith("c:") || inPlaceOption.getValue().startsWith("e:")) {
					Log.log("Option 'in-place' (-i) does not accept a file format specifier. It is only compatible with the cluster file format. Exiting now.");
					System.exit(1);
				}

				rightPath = "c:" + inPlaceOption.getValue();
			} else {
				rightPath = args.pop();
			}

			GraphMergeStrategy strategy = GraphMergeStrategy.forId(strategyOption.getValue());
			if (strategy == null)
				throw new IllegalArgumentException("Unknown merge strategy " + strategyOption.getValue());

			if (args.size() > 0)
				Log.log("Ignoring %d extraneous command-line arguments", args.size());

			parsingArguments = false;

			Log.log("Merge two graphs:\n\tLeft: %s\n\tRight: %s", leftPath, rightPath);

			options.initializeGraphEnvironment();

			GraphMergeCandidate leftCandidate = loadMergeCandidate(leftPath);
			GraphMergeCandidate rightCandidate = loadMergeCandidate(rightPath);

			if (iterationCount > 1)
				System.err
						.println(String.format(" *** Warning: entering loop of %d merge iterations!", iterationCount));

			List<ClusterGraph> mergedGraphs = null;
			for (int i = 0; i < iterationCount; i++) {
				mergedGraphs = merge(leftCandidate, rightCandidate, strategy, logFile);
			}

			File outputDir = null;

			if (outputOption.hasValue())
				outputDir = new File(outputOption.getValue());
			else if (inPlaceOption.hasValue())
				outputDir = new File(inPlaceOption.getValue());
			if (outputDir != null) {
				outputDir.mkdir();

				ClusterTraceDataSink dataSink = new ClusterTraceDirectory(outputDir);
				String filenameFormat = "%s.%%s.%%s.%%s";
				if (nameOption.hasValue()) {
					filenameFormat = String.format(filenameFormat, nameOption.getValue());
				} else {
					filenameFormat = String.format(filenameFormat, rightCandidate.parseTraceName());
				}
				for (ClusterGraph graph : mergedGraphs) {
					dataSink.addCluster(graph.graph.cluster, filenameFormat);
					ClusterGraphWriter writer = new ClusterGraphWriter(graph, dataSink);
					writer.writeGraph();
				}
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

	@SuppressWarnings("unchecked")
	List<ClusterGraph> merge(GraphMergeCandidate leftData, GraphMergeCandidate rightData, GraphMergeStrategy strategy,
			File logFile) throws IOException {
		long mergeStart = System.currentTimeMillis();

		MergeResults results;
		switch (strategy) {
			case HASH:
				results = new ClusterHashMergeResults();
				break;
			case TAG:
				results = new ClusterTagMergeResults();
				break;
			default:
				throw new IllegalArgumentException("Unknown merge strategy " + strategy);
		}

		List<ClusterGraph> mergedGraphs = new ArrayList<ClusterGraph>();
		List<ModuleGraphCluster<?>> dynamicGraphs = new ArrayList<ModuleGraphCluster<?>>();

		for (AutonomousSoftwareDistribution leftCluster : leftData.getRepresentedClusters()) {
			if (!options.includeCluster(leftCluster))
				continue;

			if ((strategy == GraphMergeStrategy.TAG) && leftCluster.isDynamic()) {
				if (leftCluster != ConfiguredSoftwareDistributions.DYNAMORIO_CLUSTER)
					dynamicGraphs.add(leftData.getClusterGraph(leftCluster));
				continue;
			}

			Log.log("\n > Loading cluster %s < \n", leftCluster.name);

			ModuleGraphCluster<?> rightGraph = rightData.getClusterGraph(leftCluster);
			if (rightGraph == null) {
				// TODO: should I just copy it in?
				Log.log("Skipping cluster %s because it does not appear in the right side.", leftCluster.name);
				continue;
			}

			if (ConfiguredSoftwareDistributions.getInstance().clusterMode != ConfiguredSoftwareDistributions.ClusterMode.UNIT)
				throw new UnsupportedOperationException(
						"Cluster compatibility has not yet been defined for cluster mode "
								+ ConfiguredSoftwareDistributions.getInstance().clusterMode);

			ModuleGraphCluster<?> leftGraph = leftData.getClusterGraph(leftCluster);
			if (!rightGraph.isCompatible(leftGraph)) {
				Log.log("Skipping cluster %s because its modules versions are not compatible with the right side",
						leftCluster.name);
				continue;
			}

			ClusterGraph mergedGraph = null;
			switch (strategy) {
				case HASH:
					mergedGraph = ClusterHashMergeSession.mergeTwoGraphs(leftGraph, rightGraph,
							(ClusterHashMergeResults) results, debugLog);
					break;
				case TAG:
					mergedGraph = ClusterTagMergeSession.mergeTwoGraphs(leftGraph,
							(ModuleGraphCluster<ClusterNode<?>>) rightGraph, (ClusterTagMergeResults) results);
					break;
				default:
					throw new IllegalArgumentException("Unknown merge strategy " + strategy);
			}

			Log.log("Checking reachability on the merged graph.");
			mergedGraph.graph.analyzeGraph();

			mergedGraphs.add(mergedGraph);
		}

		if (strategy == GraphMergeStrategy.TAG) {
			DynamicHashMatchEvaluator dynamicEvaluator = new DynamicHashMatchEvaluator();
			ClusterHashMergeResults dynamicResults = new ClusterHashMergeResults();

			ModuleGraphCluster<?> leftDynamorioGraph = leftData
					.getClusterGraph(ConfiguredSoftwareDistributions.DYNAMORIO_CLUSTER);
			ModuleGraphCluster<?> rightDynamorioGraph = rightData
					.getClusterGraph(ConfiguredSoftwareDistributions.DYNAMORIO_CLUSTER);

			ClusterGraph mergedDynamoGraph = ClusterHashMergeSession.mergeTwoGraphs(leftDynamorioGraph,
					rightDynamorioGraph, dynamicEvaluator, dynamicResults, debugLog);
			mergedDynamoGraph.graph.analyzeGraph();
			mergedGraphs.add(mergedDynamoGraph);

			for (AutonomousSoftwareDistribution rightCluster : rightData.getRepresentedClusters()) {
				if (rightCluster.isDynamic())
					dynamicGraphs.add(rightData.getClusterGraph(rightCluster));
			}
			mergedGraphs.addAll(mergeDynamicClusters(dynamicGraphs));

			if (logFile != null)
				writeResults(dynamicResults, getCorrespondingDynamicResultsFilename(logFile), logFile);
		}

		results.setGraphSummaries(leftData.summarizeGraph(), rightData.summarizeGraph());

		Log.log("\nClusters merged in %f seconds.", ((System.currentTimeMillis() - mergeStart) / 1000.));

		if (logFile != null) {
			writeResults(results, getCorrespondingResultsFilename(logFile), logFile);
		} else {
			Log.log("Results logging skipped.");
		}

		return mergedGraphs;
	}

	private List<ClusterGraph> mergeDynamicClusters(List<ModuleGraphCluster<?>> dynamicGraphs) {
		List<ClusterGraph> mergedGraphs = new ArrayList<ClusterGraph>();
		for (ModuleGraphCluster<?> dynamicGraph : dynamicGraphs) {
			// split into subgraphs and add each to a new list of ModuleGraphCluster<?>
		}
		// now isolate the unique subgraphs
		return mergedGraphs;
	}

	private GraphMergeCandidate loadMergeCandidate(String path) throws IOException {
		File directory = new File(path.substring(path.indexOf(':') + 1));
		if (!(directory.exists() && directory.isDirectory())) {
			Log.log("Illegal argument '" + directory + "'; no such directory.");
			printUsageAndExit();
		}

		GraphMergeCandidate candidate = null;
		switch (path.charAt(0)) {
			case 'c':
				candidate = new GraphMergeCandidate.Cluster(directory, debugLog);
				break;
			case 'e':
				candidate = new GraphMergeCandidate.Execution(directory, debugLog);
				break;
			default:
				throw new IllegalArgumentException(
						String.format(
								"Run directory is missing the graph type specifier:\n\t%s\nIt must be preceded by \"c:\" (for a cluster graph) or \"e:\" (for an execution graph).",
								path));
		}

		candidate.loadData();
		return candidate;
	}

	private void writeResults(MergeResults results, String resultsFilename, File logFile) throws IOException {
		String resultsPath = new File(logFile.getParentFile(), resultsFilename).getPath();
		File resultsFile = LogFile.create(resultsPath, LogFile.CollisionMode.ERROR, LogFile.NoSuchPathMode.ERROR);
		FileOutputStream out = new FileOutputStream(resultsFile);
		out.write(results.getStrategy().getMessageType().id);
		results.getResults().writeTo(out);
		out.flush();
		out.close();
	}

	private void printUsageAndExit() {
		System.out.println("Usage:");
		System.out
				.println(String
						.format("Merge: %s [ -c <cluster-name>,... ] [ -d <crowd-safe-common-dir> ] [ -l <log-dir> ]\n\t{ c: | e: }<left-trace-dir> { c: | e: }<right-trace-dir>",
								MergeTwoGraphs.class.getSimpleName()));
		System.out
				.println(String
						.format("with output: %s [  ] [ -l <log-dir> ]\n\t-n <output-name> -o <output-dir> { c: | e: }<left-trace-dir> { c: | e: }<right-trace-dir>",
								MergeTwoGraphs.class.getSimpleName()));
		System.out
				.println(String
						.format("in-place: %s [ -c <cluster-name>,... ] [ -d <crowd-safe-common-dir> ] [ -l <log-dir> ]\n\t-i { c: | e: }<in-place-trace-dir> { c: | e: }<other-trace-dir>",
								MergeTwoGraphs.class.getSimpleName()));
		System.out.println("-s { hash | tag } (merge strategy)");
		System.out.println("-c <cluster-name>,... (include only these clusters)");
		System.out.println("-x <cluster-name>,... (exclude these clusters)");
		System.out.println("-d <crowd-safe-common-dir>");
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		MergeTwoGraphs main = new MergeTwoGraphs(new CommonMergeOptions(stack, CommonMergeOptions.crowdSafeCommonDir,
				CommonMergeOptions.restrictedClusterOption, CommonMergeOptions.unitClusterOption,
				CommonMergeOptions.excludeClusterOption, logFilenameOption, nameOption, strategyOption, outputOption,
				inPlaceOption, verboseOption));
		main.run(stack, 1);

		main.toString();
	}
}
