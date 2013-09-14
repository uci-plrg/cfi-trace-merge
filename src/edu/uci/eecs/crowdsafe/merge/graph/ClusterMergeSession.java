package edu.uci.eecs.crowdsafe.merge.graph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.config.CrowdSafeConfiguration;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.loader.ProcessGraphLoadSession;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDirectory;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceStreamType;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.log.LogFile;
import edu.uci.eecs.crowdsafe.merge.graph.PairNode.MatchType;
import edu.uci.eecs.crowdsafe.merge.graph.data.MergedClusterGraph;
import edu.uci.eecs.crowdsafe.merge.graph.debug.DebugUtils;
import edu.uci.eecs.crowdsafe.merge.graph.debug.MatchingInstance;
import edu.uci.eecs.crowdsafe.merge.graph.debug.MatchingType;
import edu.uci.eecs.crowdsafe.merge.util.AnalysisUtil;

public class ClusterMergeSession {

	enum State {
		INITIALIZATION,
		ENTRY_POINTS,
		AD_HOC,
		FINALIZE
	}

	private static final EnumSet<ProcessTraceStreamType> MERGE_FILE_TYPES = EnumSet.of(ProcessTraceStreamType.MODULE,
			ProcessTraceStreamType.GRAPH_HASH, ProcessTraceStreamType.MODULE_GRAPH,
			ProcessTraceStreamType.CROSS_MODULE_GRAPH);

	State state = State.INITIALIZATION;

	final GraphMergeTarget left;
	final GraphMergeTarget right;
	final MergedClusterGraph mergedGraph;

	final GraphMergeStatistics statistics;
	final GraphMergeResults results;
	final GraphMergeDebug debugLog;

	final MatchedNodes matchedNodes;
	final GraphMatchState matchState;

	// The speculativeScoreList, which records the detail of the scoring of
	// all the possible cases
	final SpeculativeScoreList speculativeScoreList = new SpeculativeScoreList(this);

	final ContextMatchRecord contextRecord = new ContextMatchRecord();

	private final Map<Node, Integer> scoresByLeftNode = new HashMap<Node, Integer>();

	boolean hasConflict;

	final GraphMergeEngine engine = new GraphMergeEngine(this);

	ClusterMergeSession(ModuleGraphCluster left, ModuleGraphCluster right, GraphMergeResults results,
			GraphMergeDebug debugLog) {
		this.left = new GraphMergeTarget(this, left);
		this.right = new GraphMergeTarget(this, right);
		this.results = results;
		results.beginCluster(this);
		this.debugLog = debugLog;
		debugLog.setSession(this);

		matchedNodes = new MatchedNodes(this);
		matchState = new GraphMatchState(this);
		statistics = new GraphMergeStatistics(this);
		mergedGraph = new MergedClusterGraph();
	}

	public void initializeMerge() {
		right.visitedEdges.clear();
		right.visitedAsUnmatched.clear();
		matchedNodes.clear();
		matchState.clear();
		speculativeScoreList.clear();
		statistics.reset();
		hasConflict = false;

		// Initialize debugging info before merging the graph
		if (DebugUtils.debug) {
			DebugUtils.debug_init();
		}

		Map<Long, ExecutionNode> leftEntryPoints = left.cluster.getEntryPoints();
		Map<Long, ExecutionNode> rightEntryPoints = right.cluster.getEntryPoints();
		for (long sigHash : rightEntryPoints.keySet()) {
			if (leftEntryPoints.containsKey(sigHash)) {
				ExecutionNode leftNode = leftEntryPoints.get(sigHash);
				ExecutionNode rightNode = rightEntryPoints.get(sigHash);

				debugLog.debugCheck(leftNode);
				debugLog.debugCheck(rightNode);

				if (leftNode.hasCompatibleEdges(rightNode)) {
					matchState.enqueueMatch(new PairNode(leftNode, rightNode, MatchType.ENTRY_POINT));
					matchedNodes.addPair(leftNode, rightNode, 0);

					statistics.directMatch();

					if (DebugUtils.debug) {
						// AnalysisUtil.outputIndirectNodesInfo(n1, n2);
					}

					if (DebugUtils.debug) {
						DebugUtils.debug_matchingTrace.addInstance(new MatchingInstance(leftNode.getKey(), rightNode
								.getKey(), MatchingType.SignatureNode, null));
					}
					continue;
				}
			}

			// Push new signature node to prioritize the speculation to the
			// beginning of the graph
			ExecutionNode rightEntryPoint = rightEntryPoints.get(sigHash);
			// TODO: guessing that the third arg "level" should be 0
			matchState.enqueueUnmatch(rightEntryPoint);
			engine.addUnmatchedNode2Queue(rightEntryPoint);
		}

		debugLog.initializeMerge(left.cluster, right.cluster);
	}

	boolean acceptContext(Node candidate) {
		int score = contextRecord.evaluate();
		if (score < 7)
			return false;
		setScore(candidate, score);
		return true;
	}

	void setScore(Node leftNode, int score) {
		scoresByLeftNode.put(leftNode, score);
	}

	int getScore(Node leftNode) {
		Integer score = scoresByLeftNode.get(leftNode);
		if (score != null)
			return score;
		return 0;
	}

	private static void printUsageAndExit() {
		Log.log("Arguments: <left-trace-dir> <right-trace-dir> [<log-output>]");
		System.exit(1);
	}

	public static void main(String[] args) {
		try {
			CrowdSafeConfiguration.initialize(EnumSet.of(CrowdSafeConfiguration.Environment.CROWD_SAFE_COMMON_DIR));

			File logFile = null;
			if (args.length > 2) {
				try {
					logFile = LogFile.create(args[2], LogFile.CollisionMode.AVOID, LogFile.NoSuchPathMode.ERROR);
					Log.addOutput(logFile);
					System.out.println("Logging to " + logFile.getName());
				} catch (LogFile.Exception e) {
					e.printStackTrace(System.err);
				}
			} else {
				Log.addOutput(System.out);
			}

			if (args.length < 2) {
				Log.log("Illegal arguments: please specify the two run directories as relative or absolute paths.");
				printUsageAndExit();
			}

			File leftRun = new File(args[0]);
			File rightRun = new File(args[1]);

			if (!(leftRun.exists() && leftRun.isDirectory())) {
				Log.log("Illegal argument '" + args[0] + "'; no such directory.");
				printUsageAndExit();
			}
			if (!(rightRun.exists() && rightRun.isDirectory())) {
				Log.log("Illegal argument '" + args[1] + "'; no such directory.");
				printUsageAndExit();
			}

			ProcessTraceDataSource leftDataSource = new ProcessTraceDirectory(leftRun, MERGE_FILE_TYPES);
			ProcessTraceDataSource rightDataSource = new ProcessTraceDirectory(rightRun, MERGE_FILE_TYPES);

			long start = System.currentTimeMillis();
			GraphMergeDebug debugLog = new GraphMergeDebug();

			ProcessGraphLoadSession leftSession = new ProcessGraphLoadSession(leftDataSource);
			ProcessExecutionGraph leftGraph = leftSession.loadGraph(debugLog);

			ProcessGraphLoadSession rightSession = new ProcessGraphLoadSession(rightDataSource);
			ProcessExecutionGraph rightGraph = rightSession.loadGraph(debugLog);

			GraphMergeResults results = new GraphMergeResults(leftGraph, rightGraph);

			long merge = System.currentTimeMillis();
			Log.log("\nGraph loaded in %f seconds.", ((merge - start) / 1000.));

			for (ModuleGraphCluster leftCluster : leftGraph.getAutonomousClusters()) {
				ModuleGraphCluster rightCluster = rightGraph.getModuleGraphCluster(leftCluster.distribution);
				if (rightCluster == null) {
					Log.log("Skipping cluster %s because it does not appear in the right side.",
							leftCluster.distribution.name);
					continue;
				}

				if (DebugUtils.debug_decision(DebugUtils.FILTER_OUT_IMME_ADDR)) {
					AnalysisUtil.filteroutImmeAddr(leftCluster, rightCluster);
				}

				ClusterMergeSession session = new ClusterMergeSession(leftCluster, rightCluster, results, debugLog);
				GraphMergeEngine engine = new GraphMergeEngine(session);

				engine.mergeGraph();
				session.results.clusterMergeCompleted();
			}
			Log.log("\nClusters merged in %f seconds.", ((System.currentTimeMillis() - merge) / 1000.));

			if (logFile != null) {
				String resultsFilename = logFile.getName().substring(0, logFile.getName().lastIndexOf('.'));
				resultsFilename = String.format("%s.results.log", resultsFilename);
				String resultsPath = new File(logFile.getParentFile(), resultsFilename).getPath();
				File resultsFile = LogFile.create(resultsPath, LogFile.CollisionMode.ERROR,
						LogFile.NoSuchPathMode.ERROR);
				FileOutputStream out = new FileOutputStream(resultsFile);
				results.getResults().writeTo(out);
				out.flush();
				out.close();
			} else {
				Log.log("Results logging skipped.");
			}
		} catch (Throwable t) {
			Log.log(t);
			t.printStackTrace();
		}
	}
}
