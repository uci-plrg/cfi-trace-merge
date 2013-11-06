package edu.uci.eecs.crowdsafe.merge.graph.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
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
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeAnalysis;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeDebugLog;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeResults;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeSession;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ContextMatchState;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashMatchedNodes;
import edu.uci.eecs.crowdsafe.merge.graph.hash.MaximalSubgraphs;
import edu.uci.eecs.crowdsafe.merge.graph.tag.ClusterTagMergeResults;
import edu.uci.eecs.crowdsafe.merge.graph.tag.ClusterTagMergeSession;

public class MergeTwoGraphs {

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

	private static class DynamicHashMatchEvaluator implements ClusterHashMergeSession.MergeEvaluator {
		boolean mergeMode = false;
		boolean exactMatch;
		int greaterMatchPercentage;

		@Override
		public boolean attemptMerge(ClusterHashMergeSession session) {
			ModuleGraphCluster<?> left = session.getLeft();
			ModuleGraphCluster<?> right = session.getRight();

			int hashOverlapPerNode = left.getGraphData().nodesByHash
					.getHashOverlapPerNode(right.getGraphData().nodesByHash);
			if ((hashOverlapPerNode > (left.getExecutableNodeCount() / 2))
					|| (hashOverlapPerNode > (right.getExecutableNodeCount() / 2)))
				return true;

			exactMatch = false;
			greaterMatchPercentage = 0;
			return false;
		}

		@Override
		public int evaluateMatch(ContextMatchState state) {
			if (state.isComplete() && (state.getMatchedNodeCount() > 0))
				return 1000;

			return -1;
		}

		@Override
		public boolean acceptGraphs(ClusterHashMergeSession session) {
			if (mergeMode)
				return true;

			ModuleGraphCluster<?> left = session.getLeft();
			ModuleGraphCluster<?> right = session.getRight();
			HashMatchedNodes matchedNodes = session.getMatchedNodes();

			if (matchedNodes.size() == Math.min(left.getNodeCount(), right.getNodeCount())) {
				greaterMatchPercentage = 100;
				exactMatch = true;
				return true;
			}

			// TODO: may want to tag subgraphs with an id representing the original anonymous module, to use as a hint

			exactMatch = false;
			int leftMatchPercentage = Math.round((matchedNodes.size() / (float) left.getNodeCount()) * 100f);
			int rightMatchPercentage = Math.round((matchedNodes.size() / (float) right.getNodeCount()) * 100f);
			greaterMatchPercentage = Math.max(leftMatchPercentage, rightMatchPercentage);

			if (greaterMatchPercentage > 50)
				return true;

			// Log.log("Rejecting match of %d nodes for graphs of size %d (%d%%) and %d (%d%%)", matchedNodes.size(),
			// left.getNodeCount(), leftMatchPercentage, right.getNodeCount(), rightMatchPercentage);
			return false;
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

	private static final OptionArgumentMap.StringOption logFilenameOption = OptionArgumentMap.createStringOption('l',
			"merge.log");
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
			GraphMergeCandidate rightCandidate = (leftPath.equals(rightPath) ? leftCandidate
					: loadMergeCandidate(rightPath));

			if (iterationCount > 1)
				System.err
						.println(String.format(" *** Warning: entering loop of %d merge iterations!", iterationCount));

			MergeCompletion completion = new IgnoreMergeCompletion();
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
				completion = new WriteCompletedGraphs(dataSink, filenameFormat);
			}

			for (int i = 0; i < iterationCount; i++) {
				merge(leftCandidate, rightCandidate, strategy, logFile, completion);
			}

		} catch (Log.OutputException e) {
			e.printStackTrace();
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
	void merge(GraphMergeCandidate leftData, GraphMergeCandidate rightData, GraphMergeStrategy strategy, File logFile,
			MergeCompletion completion) throws IOException {
		long mergeStart = System.currentTimeMillis();

		MergeResults results;
		switch (strategy) {
			case HASH:
				results = new ClusterHashMergeAnalysis();
				break;
			case TAG:
				results = new ClusterTagMergeResults();
				break;
			default:
				throw new IllegalArgumentException("Unknown merge strategy " + strategy);
		}

		List<ModuleGraphCluster<ClusterNode<?>>> anonymousGraphs = new ArrayList<ModuleGraphCluster<ClusterNode<?>>>();

		// cs-todo: this can be multi-threaded for large training datasets
		for (AutonomousSoftwareDistribution leftCluster : leftData.getRepresentedClusters()) {
			if (!options.includeCluster(leftCluster))
				continue;

			if ((strategy == GraphMergeStrategy.TAG) && leftCluster.isAnonymous()) {
				// if (leftCluster != ConfiguredSoftwareDistributions.DYNAMORIO_CLUSTER)
				// cast is ok because tag merge only works on cluster graphs
				anonymousGraphs.add((ModuleGraphCluster<ClusterNode<?>>) leftData.getClusterGraph(leftCluster));
				continue;
			}

			if (ConfiguredSoftwareDistributions.getInstance().clusterMode != ConfiguredSoftwareDistributions.ClusterMode.UNIT)
				throw new UnsupportedOperationException(
						"Cluster compatibility has not yet been defined for cluster mode "
								+ ConfiguredSoftwareDistributions.getInstance().clusterMode);

			Log.log("\n > Loading cluster %s < \n", leftCluster.name);

			ClusterGraph mergedGraph = null;
			ModuleGraphCluster<?> leftGraph = leftData.getClusterGraph(leftCluster);
			ModuleGraphCluster<?> rightGraph = rightData.getClusterGraph(leftCluster);
			if (rightGraph == null) {
				if (strategy == GraphMergeStrategy.TAG) {
					Log.log("Copying left cluster %s because it does not appear in the right side.", leftCluster.name);
					mergedGraph = new ClusterGraph((ModuleGraphCluster<ClusterNode<?>>) leftGraph);
				} else {
					Log.log("Skipping left cluster %s because it does not appear in the right side and has incompatible format with the merge data.",
							leftCluster.name);
					continue;
				}
			} else {
				if (!rightGraph.isCompatible(leftGraph)) {
					Log.log("Skipping cluster %s because its modules versions are not compatible with the right side",
							leftCluster.name);
					continue;
				}

				switch (strategy) {
					case HASH:
						mergedGraph = ClusterHashMergeSession.mergeTwoGraphs(leftGraph, rightGraph,
								(ClusterHashMergeAnalysis) results, new ClusterHashMergeSession.DefaultEvaluator(),
								debugLog);
						break;
					case TAG:
						mergedGraph = ClusterTagMergeSession.mergeTwoGraphs(leftGraph,
								(ModuleGraphCluster<ClusterNode<?>>) rightGraph, (ClusterTagMergeResults) results);
						break;
					default:
						throw new IllegalArgumentException("Unknown merge strategy " + strategy);
				}

				// Log.log("Checking reachability on the merged graph.");
				// mergedGraph.graph.analyzeGraph();
			}

			completion.mergeCompleted(mergedGraph);
			System.gc();
		}

		if ((strategy == GraphMergeStrategy.TAG) && (rightData != leftData)) {
			for (AutonomousSoftwareDistribution rightCluster : rightData.getRepresentedClusters()) {
				if (options.includeCluster(rightCluster) && !leftData.getRepresentedClusters().contains(rightCluster)) {
					Log.log("Copying right cluster %s because it does not appear in the left side.", rightCluster.name);

					completion.mergeCompleted(new ClusterGraph((ModuleGraphCluster<ClusterNode<?>>) rightData
							.getClusterGraph(rightCluster)));
				}
			}
		}

		if (strategy == GraphMergeStrategy.TAG) {
			ClusterHashMergeAnalysis anonymousResults = new ClusterHashMergeAnalysis();

			if (rightData != leftData) {
				for (AutonomousSoftwareDistribution rightCluster : rightData.getRepresentedClusters()) {
					if (!options.includeCluster(rightCluster))
						continue;

					if (rightCluster.isAnonymous())
						// cast is ok because tag merge only works on cluster graphs
						anonymousGraphs.add((ModuleGraphCluster<ClusterNode<?>>) rightData
								.getClusterGraph(rightCluster));
				}
			}
			ClusterGraph anonymousGraph = createAnonymousGraph(anonymousGraphs);
			completion.mergeCompleted(anonymousGraph);

			if (logFile != null)
				writeResults(anonymousResults, getCorrespondingDynamicResultsFilename(logFile), logFile);
		}

		results.setGraphSummaries(leftData.summarizeGraph(), rightData.summarizeGraph());

		Log.log("\nClusters merged in %f seconds.", ((System.currentTimeMillis() - mergeStart) / 1000.));

		if (logFile != null) {
			writeResults(results, getCorrespondingResultsFilename(logFile), logFile);
		} else {
			Log.log("Results logging skipped.");
		}
	}

	private ClusterGraph createAnonymousGraph(List<ModuleGraphCluster<ClusterNode<?>>> dynamicGraphs) {
		// TODO: this will be faster if any existing anonymous graph is used as the initial comparison set for any
		// dynamic and static graphs

		int totalSize = 0;
		int minSize = Integer.MAX_VALUE;
		int maxSize = 0;
		List<ModuleGraphCluster<ClusterNode<?>>> maximalSubgraphs = new ArrayList<ModuleGraphCluster<ClusterNode<?>>>();
		for (ModuleGraphCluster<ClusterNode<?>> dynamicGraph : dynamicGraphs) {
			for (ModuleGraphCluster<ClusterNode<?>> maximalSubgraph : MaximalSubgraphs
					.getMaximalSubgraphs(dynamicGraph)) {
				int size = maximalSubgraph.getNodeCount();
				totalSize += size;
				if (size < minSize)
					minSize = size;
				if (size > maxSize)
					maxSize = size;

				maximalSubgraphs.add(maximalSubgraph);
			}
		}

		int averageSize = totalSize / maximalSubgraphs.size();
		int twiceAverage = averageSize * 2;
		int thriceAverage = averageSize * 3;
		int halfAverage = averageSize / 2;
		int subgraphsOverTwiceAverage = 0;
		int subgraphsOverThriceAverage = 0;
		int subgraphsUnderHalfAverage = 0;
		for (ModuleGraphCluster<ClusterNode<?>> maximalSubgraph : maximalSubgraphs) {
			int size = maximalSubgraph.getNodeCount();
			if (size > twiceAverage) {
				subgraphsOverTwiceAverage++;
				if (size > thriceAverage)
					subgraphsOverThriceAverage++;
			} else if (size < halfAverage) {
				subgraphsUnderHalfAverage++;
			}
		}

		Log.log("Found %d maximal subgraphs.", maximalSubgraphs.size());
		Log.log("Min size %d, max size %d, average size %d", minSize, maxSize, averageSize);
		Log.log("Over twice average %d, over thrice average %d, under half average %d", subgraphsOverTwiceAverage,
				subgraphsOverThriceAverage, subgraphsUnderHalfAverage);

		List<ModuleGraphCluster<ClusterNode<?>>> largeSubgraphs = new ArrayList<ModuleGraphCluster<ClusterNode<?>>>();

		DynamicHashMatchEvaluator dynamicEvaluator = new DynamicHashMatchEvaluator();
		List<SubgraphCluster> subgraphClusters = new ArrayList<SubgraphCluster>();
		boolean match;
		for (ModuleGraphCluster<ClusterNode<?>> maximalSubgraph : maximalSubgraphs) {
			// if (maximalSubgraph.getNodeCount() > twiceAverage) {
			// Log.log("Postponing subgraph of size %d", maximalSubgraph.getNodeCount());
			// largeSubgraphs.add(maximalSubgraph);
			// continue;
			// }

			match = false;
			for (SubgraphCluster subgraphCluster : subgraphClusters) {
				int score = 0;
				for (int i = 0; i < subgraphCluster.graphs.size(); i++) {
					ClusterHashMergeSession.evaluateTwoGraphs(maximalSubgraph, subgraphCluster.graphs.get(i),
							dynamicEvaluator, debugLog);
					if (dynamicEvaluator.exactMatch) {
						match = true;
						break;
					} else if (dynamicEvaluator.greaterMatchPercentage > 80) {
						ClusterGraph mergedGraph = ClusterHashMergeSession.mergeTwoGraphs(maximalSubgraph,
								subgraphCluster.graphs.get(i), ClusterHashMergeResults.Empty.INSTANCE,
								dynamicEvaluator, debugLog);
						subgraphCluster.graphs.set(i, mergedGraph.graph);
						match = true;
						break;
					}
					score += dynamicEvaluator.greaterMatchPercentage;
				}

				if (match)
					break;

				int clusterPercentage = (score / subgraphCluster.graphs.size());
				if (clusterPercentage > 50) {
					subgraphCluster.graphs.add(maximalSubgraph);
					match = true;
					break;
				}
			}

			if (!match) {
				subgraphClusters.add(new SubgraphCluster(maximalSubgraph));
			}
		}

		dynamicEvaluator.mergeMode = true;

		// TODO: evaluate each cluster -> merge all its graphs together if valid

		Map<ClusterNode<?>, ClusterNode<?>> copyMap = new HashMap<ClusterNode<?>, ClusterNode<?>>();
		ClusterGraph anonymousGraph = new ClusterGraph(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER);
		Log.log("\nReduced anonymous graph to %d subgraph clusters:", subgraphClusters.size());
		for (SubgraphCluster subgraphCluster : subgraphClusters) {
			Log.log("\nCluster of %d subgraphs:", subgraphCluster.graphs.size());

			ModuleGraphCluster<ClusterNode<?>> graph;
			if (subgraphCluster.graphs.size() == 1) {
				graph = subgraphCluster.graphs.get(0);
			} else {
				ClusterGraph mergedGraph = ClusterHashMergeSession.mergeTwoGraphs(subgraphCluster.graphs.get(0),
						subgraphCluster.graphs.get(1), ClusterHashMergeResults.Empty.INSTANCE, dynamicEvaluator,
						debugLog);
				if (mergedGraph == null) {
					Log.log("Error! Failed to merge two subgraphs that were assigned to a cluster. Skipping the second.");
					graph = subgraphCluster.graphs.get(0);
				} else {
					graph = mergedGraph.graph;
				}

				for (int i = 2; i < subgraphCluster.graphs.size(); i++) {
					mergedGraph = ClusterHashMergeSession.mergeTwoGraphs(graph, subgraphCluster.graphs.get(i),
							ClusterHashMergeResults.Empty.INSTANCE, dynamicEvaluator, debugLog);
					if (mergedGraph == null) {
						Log.log("Error! Failed to merge two subgraphs that were assigned to a cluster. Skipping the second.");
					} else {
						graph = mergedGraph.graph;
					}
				}
			}

			// for (ModuleGraphCluster<ClusterNode<?>> subgraph : subgraphCluster.graphs) {
			// subgraph.logGraph();

			for (ClusterNode<?> node : graph.getAllNodes()) {
				ClusterNode<?> copy = anonymousGraph.addNode(node.getHash(), SoftwareModule.ANONYMOUS_MODULE,
						node.getRelativeTag(), node.getType());
				copyMap.put(node, copy);
			}

			for (ClusterNode<?> node : graph.getAllNodes()) {
				OrdinalEdgeList<?> edges = node.getOutgoingEdges();
				try {
					for (Edge<? extends Node<?>> edge : edges) {
						ClusterNode<?> fromNode = copyMap.get(edge.getFromNode());
						ClusterNode<?> toNode = copyMap.get(edge.getToNode());
						Edge<ClusterNode<?>> mergedEdge = new Edge<ClusterNode<?>>(fromNode, toNode,
								edge.getEdgeType(), edge.getOrdinal());
						fromNode.addOutgoingEdge(mergedEdge);
						toNode.addIncomingEdge(mergedEdge);
					}
				} finally {
					edges.release();
				}
			}

			copyMap.clear();
			// }
		}

		return anonymousGraph;
	}

	private static class SubgraphCluster {
		List<ModuleGraphCluster<ClusterNode<?>>> graphs = new ArrayList<ModuleGraphCluster<ClusterNode<?>>>();

		public SubgraphCluster(ModuleGraphCluster<ClusterNode<?>> graph) {
			graphs.add(graph);
		}
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
