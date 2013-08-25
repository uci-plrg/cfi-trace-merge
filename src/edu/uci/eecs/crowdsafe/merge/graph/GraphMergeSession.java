package edu.uci.eecs.crowdsafe.merge.graph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.config.CrowdSafeConfiguration;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessGraphLoadSession;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDirectory;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.log.LogFile;
import edu.uci.eecs.crowdsafe.merge.graph.debug.DebugUtils;
import edu.uci.eecs.crowdsafe.merge.graph.debug.MatchingInstance;
import edu.uci.eecs.crowdsafe.merge.graph.debug.MatchingType;
import edu.uci.eecs.crowdsafe.merge.util.AnalysisUtil;

public class GraphMergeSession {

	enum State {
		INITIALIZATION,
		ENTRY_POINTS,
		AD_HOC,
		FINALIZE
	}

	State state = State.INITIALIZATION;

	final GraphMergeTarget left;
	final GraphMergeTarget right;

	final GraphMergeStatistics graphMergingStats;

	final MatchedNodes matchedNodes;

	final LinkedList<PairNode> matchedQueue = new LinkedList<PairNode>();
	final LinkedList<PairNode> unmatchedQueue = new LinkedList<PairNode>();
	final LinkedList<PairNodeEdge> indirectChildren = new LinkedList<PairNodeEdge>();

	// The speculativeScoreList, which records the detail of the scoring of
	// all the possible cases
	final SpeculativeScoreList speculativeScoreList = new SpeculativeScoreList(
			this);

	final ContextMatchRecord contextRecord = new ContextMatchRecord();

	private final Map<Node, Integer> scoresByLeftNode = new HashMap<Node, Integer>();

	boolean hasConflict;

	final GraphMergeEngine engine = new GraphMergeEngine(this);

	GraphMergeSession(ModuleGraphCluster left, ModuleGraphCluster right) {
		this.left = new GraphMergeTarget(this, left);
		this.right = new GraphMergeTarget(this, right);
		matchedNodes = new MatchedNodes();
		graphMergingStats = new GraphMergeStatistics(this);
	}

	public void initializeMerge() {
		right.visitedNodes.clear();
		matchedNodes.clear();
		matchedQueue.clear();
		unmatchedQueue.clear();
		indirectChildren.clear();
		speculativeScoreList.clear();
		graphMergingStats.reset();
		hasConflict = false;

		// Initialize debugging info before merging the graph
		if (DebugUtils.debug) {
			DebugUtils.debug_init();
		}

		Map<Long, ExecutionNode> leftEntryPoints = left.cluster
				.getEntryPoints();
		Map<Long, ExecutionNode> rightEntryPoints = right.cluster
				.getEntryPoints();
		for (long sigHash : rightEntryPoints.keySet()) {
			if (leftEntryPoints.containsKey(sigHash)) {
				ExecutionNode leftNode = leftEntryPoints.get(sigHash);
				ExecutionNode rightNode = rightEntryPoints.get(sigHash);

				PairNode pairNode = new PairNode(leftNode, rightNode, 0);
				matchedQueue.add(pairNode);
				matchedNodes.addPair(leftNode, rightNode, 0);

				graphMergingStats.directMatch();

				if (DebugUtils.debug) {
					// AnalysisUtil.outputIndirectNodesInfo(n1, n2);
				}

				if (DebugUtils.debug) {
					DebugUtils.debug_matchingTrace
							.addInstance(new MatchingInstance(0, leftNode
									.getKey(), rightNode.getKey(),
									MatchingType.SignatureNode, null));
				}
			} else {
				// Push new signature node to prioritize the speculation to the
				// beginning of the graph
				ExecutionNode n2 = rightEntryPoints.get(sigHash);
				// TODO: guessing that the third arg "level" should be 0
				unmatchedQueue.add(new PairNode(null, n2, 0));
				engine.addUnmatchedNode2Queue(n2, -1);
			}
		}
	}

	boolean acceptContext(Node candidate) {
		int score = contextRecord.evaluate();
		if (score < 0)
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
			CrowdSafeConfiguration
					.initialize(EnumSet
							.of(CrowdSafeConfiguration.Environment.CROWD_SAFE_COMMON_DIR));

			if (args.length > 2) {
				try {
					File logFile = LogFile.create(args[2],
							LogFile.CollisionMode.AVOID,
							LogFile.NoSuchPathMode.ERROR);
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
				Log.log("Illegal argument '" + args[0]
						+ "'; no such directory.");
				printUsageAndExit();
			}
			if (!(rightRun.exists() && rightRun.isDirectory())) {
				Log.log("Illegal argument '" + args[1]
						+ "'; no such directory.");
				printUsageAndExit();
			}

			ProcessTraceDataSource leftDataSource = new ProcessTraceDirectory(
					leftRun);
			ProcessTraceDataSource rightDataSource = new ProcessTraceDirectory(
					rightRun);
			Log.log("### ------- Merge %s(%d) with %s(%d) -------- ###",
					leftDataSource.getProcessName(),
					leftDataSource.getProcessId(),
					rightDataSource.getProcessName(),
					rightDataSource.getProcessId());

			long start = System.currentTimeMillis();

			ProcessGraphLoadSession leftSession = new ProcessGraphLoadSession(
					leftDataSource);
			ProcessExecutionGraph leftGraph = leftSession.loadGraph();

			ProcessGraphLoadSession rightSession = new ProcessGraphLoadSession(
					rightDataSource);
			ProcessExecutionGraph rightGraph = rightSession.loadGraph();

			long merge = System.currentTimeMillis();
			Log.log("\nGraph loaded in %f seconds.", ((merge - start) / 1000.));

			for (ModuleGraphCluster leftCluster : leftGraph
					.getAutonomousClusters()) {
				Log.log("\n  === Merging cluster %s ===",
						leftCluster.distribution.name);

				ModuleGraphCluster rightCluster = rightGraph
						.getModuleGraphCluster(leftCluster.distribution);

				if (DebugUtils.debug_decision(DebugUtils.FILTER_OUT_IMME_ADDR)) {
					AnalysisUtil.filteroutImmeAddr(leftCluster, rightCluster);
				}

				GraphMergeSession session = new GraphMergeSession(leftCluster,
						rightCluster);
				GraphMergeEngine engine = new GraphMergeEngine(session);

				try {
					engine.mergeGraph();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			Log.log("\nClusters merged in %f seconds.",
					((System.currentTimeMillis() - merge) / 1000.));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
