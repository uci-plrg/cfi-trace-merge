package edu.uci.eecs.crowdsafe.merge.graph.debug;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMatchEngine;

public class DebugUtils {
	// Some options about whether to throw graph exception or tolerate minor
	// graph errors
	public static final boolean ThrowTagNotFound = false;
	public static final boolean ThrowDuplicateTag = true;
	public static final boolean ThrowInvalidTag = true;
	public static final boolean ThrowMultipleEdge = false;

	public static final boolean ThrowWrongEdgeType = false;

	// Some debugging options
	public static final int DEBUG_ONLY = 0x0;
	public static final int PRINT_MATCHING_HISTORY = 0x1;
	public static final int MAIN_KNOWN = 0x1 << 1;
	public static final int MAIN_KNOWN_ADD_MAIN = 0x1 << 2;
	public static final int MERGE_ERROR = 0x1 << 3;
	public static final int DUMP_GRAPH = 0x1 << 4;
	public static final int TRACE_HEURISTIC = 0x1 << 5;
	public static final int IGNORE_CONFLICT = 0x1 << 6;
	public static final int OUTPUT_SCORE = 0x1 << 7;
	public static final int DUMP_MODIFIED_HASH = 0x1 << 8;
	public static final int FILTER_OUT_IMME_ADDR = 0x1 << 9;

	public static final String SCORE_FILE_DIR = "/scratch/cs-analysis-output/scores/";
	private static PrintWriter scorePW = null;

	public static final String MODIFIED_HASH_DIR = "./imme-addr/";

	public static PrintWriter getScorePW() {
		return scorePW;
	}

	public static int chageHashLimit = 138 * 3;
	public static int chageHashCnt = 0;
	public static final int commonBitNum = 4;

	public static final String TMP_HASHLOG_DIR = "/scratch/hashlogs/versioned-tag-sality";
	public static final String GRAPH_DIR = "/scratch/cs-analysis-output/graph-files/";

	public static final int USEFUL_DEBUG_OPTION0 = debug_option(DEBUG_ONLY);
	public static final int USEFUL_DEBUG_OPTION1 = debug_option(MERGE_ERROR);
	public static final int USEFUL_DEBUG_OPTION2 = debug_option(MERGE_ERROR, DUMP_GRAPH);
	public static final int USEFUL_DEBUG_OPTION3 = debug_option(MERGE_ERROR, DUMP_GRAPH, PRINT_MATCHING_HISTORY,
			TRACE_HEURISTIC);
	public static final int USEFUL_DEBUG_OPTION4 = debug_option(MERGE_ERROR, DUMP_GRAPH, PRINT_MATCHING_HISTORY,
			MAIN_KNOWN);
	public static final int USEFUL_DEBUG_OPTION5 = debug_option(MERGE_ERROR, DUMP_GRAPH, PRINT_MATCHING_HISTORY,
			MAIN_KNOWN_ADD_MAIN);
	public static final int USEFUL_DEBUG_OPTION6 = debug_option(MERGE_ERROR, DUMP_GRAPH, MAIN_KNOWN);
	public static final int USEFUL_DEBUG_OPTION7 = debug_option(MERGE_ERROR, MAIN_KNOWN);

	public static int DEBUG_OPTION = USEFUL_DEBUG_OPTION1 | FILTER_OUT_IMME_ADDR;
	// public static int DEBUG_OPTION = USEFUL_DEBUG_OPTION0;

	public static boolean debug = false;

	public static int debug_option(int... options) {
		int opt = 0;
		for (int i = 0; i < options.length; i++) {
			opt |= options[i];
		}
		return opt;
	}

	public static int commonBitsCnt(long hash1, long hash2) {
		String s1 = Long.toHexString(hash1), s2 = Long.toHexString(hash2);
		int cnt = 0, idx1 = s1.length() - 1, idx2 = s2.length() - 1;
		while (idx1 > -1 && idx2 > -1) {
			if (s1.charAt(idx1--) == s2.charAt(idx2--)) {
				cnt++;
			}
		}
		if (cnt < commonBitNum) {
			Log.log(s1 + "<->" + s2);
		}
		return cnt;
	}

	public static boolean debug_decision(int... options) {
		if (!debug)
			return false;
		int opt = debug_option(options);
		// Check all the debug options
		for (int i = 0; i < options.length; i++) {
			if ((options[i] & DEBUG_OPTION) == 0)
				return false;
		}
		return true;
	}

	// Times of pure heuristic used to decide if there is any corresponding node
	public static int debug_pureHeuristicCnt;
	// Times of heuristic used that the hash is not present in graph1
	public static int debug_pureHeuristicNotPresentCnt;
	// Times of indirect heuristic used to decide if two nodes match
	public static int debug_indirectHeuristicCnt;
	// Times of indirect heuristic used that the two nodes does not match
	public static int debug_indirectHeuristicUnmatchedCnt;
	// Times of direct nodes does not match
	public static int debug_directUnmatchedCnt;

	// Nodes that have been visited during merging
	public static int debug_visitedCnt;

	public static MatchingTrace debug_matchingTrace;
	public static ContextSimilarityTrace debug_contextSimilarityTrace;

	public static int searchDepth = GraphMatchEngine.PURE_SEARCH_DEPTH;

	public static void debug_init() {
		debug_pureHeuristicCnt = 0;
		debug_pureHeuristicNotPresentCnt = 0;
		debug_indirectHeuristicCnt = 0;
		debug_indirectHeuristicUnmatchedCnt = 0;
		debug_directUnmatchedCnt = 0;

		debug_visitedCnt = 0;

		debug_matchingTrace = new MatchingTrace();
		debug_contextSimilarityTrace = new ContextSimilarityTrace(searchDepth);
	}

	public static void debug_stopHere() {
		// Simply do nothing, just for debugging
	}

	/**
	 * Debugging use only!! This is only used to find out the true match of a given node2 in a comparison. argvs[0]
	 * should be like "run2". argvs[1] should be like "run13". argvs[2] is the index of nodes. It prints out the index
	 * of the true match, which is node1
	 * 
	 * @param argvs
	 */
	/**
	 * <pre> saving it for later
	public static void main(String[] argvs) {
		DEBUG_OPTION = USEFUL_DEBUG_OPTION2 | FILTER_OUT_IMME_ADDR;
		String run1, run2;
		if (argvs.length == 3) {
			// Example: run1 run2 2351
			// runDirectory1, runDirectory2, node2Index
			run1 = argvs[0];
			run2 = argvs[1];
			ArrayList<String> runDirs = AnalysisUtil
					.getAllRunDirs(DebugUtils.TMP_HASHLOG_DIR);
			String graphDir1 = runDirs.get(runDirs
					.indexOf(DebugUtils.TMP_HASHLOG_DIR + "/" + argvs[0])), graphDir2 = runDirs
					.get(runDirs.indexOf(DebugUtils.TMP_HASHLOG_DIR + "/"
							+ argvs[1]));
			ArrayList<ExecutionGraph> graphs1 = ExecutionGraph
					.buildGraphsFromRunDir(graphDir1), graphs2 = ExecutionGraph
					.buildGraphsFromRunDir(graphDir2);
			ExecutionGraph graph1 = graphs1.get(0), graph2 = graphs2.get(0);
			GraphMerger graphMerger = new GraphMerger(graph1, graph2);

			int node2Idx = Integer.parseInt(argvs[2]);
			Node node2 = graph2.getNodes().get(node2Idx), node1 = AnalysisUtil
					.getTrueMatch(graph1, graph2, node2);
			if (node1 == null) {
				Log.log("null");
			} else {
				Log.log(node1.getIndex());
			}
		} else if (argvs.length == 5) {
			// Example: run1 run2 233 233 15
			// runDirectory1, runDirectory2, node1Index, node2Index, searchDepth
			run1 = argvs[0];
			run2 = argvs[1];
			ArrayList<String> runDirs = AnalysisUtil
					.getAllRunDirs(DebugUtils.TMP_HASHLOG_DIR);
			String graphDir1 = runDirs.get(runDirs
					.indexOf(DebugUtils.TMP_HASHLOG_DIR + "/" + argvs[0])), graphDir2 = runDirs
					.get(runDirs.indexOf(DebugUtils.TMP_HASHLOG_DIR + "/"
							+ argvs[1]));
			ArrayList<ExecutionGraph> graphs1 = ExecutionGraph
					.buildGraphFromRunDir(graphDir1), graphs2 = ExecutionGraph
					.buildGraphFromRunDir(graphDir2);
			ExecutionGraph graph1 = graphs1.get(0), graph2 = graphs2.get(0);

			GraphMergerThread graphMerger = new GraphMergerThread(graph1, graph2);
			Thread graphMergerThread = new Thread(graphMerger);
			graphMergerThread.start();

			int node1Idx = Integer.parseInt(argvs[2]), node2Idx = Integer
					.parseInt(argvs[3]), searchDepth = Integer
					.parseInt(argvs[4]);
			Node node1 = graph1.getNodes().get(node1Idx), node2 = graph2
					.getNodes().get(node2Idx);
			try {
				graphMergerThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			int score = graphMerger.debug_getContextSimilarity(node1, node2,
					searchDepth);
			Log.log(score);
		}

	}
	 */
}
