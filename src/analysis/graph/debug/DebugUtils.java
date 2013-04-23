package analysis.graph.debug;

import analysis.graph.GraphMerger;

public class DebugUtils {

	public static final int DEBUG_ONLY = 0x0;
	public static final int PRINT_MATCHING_HISTORY = 0x1;
	public static final int MAIN_KNOWN = 0x1 << 1;
	public static final int MAIN_KNOWN_ADD_MAIN = 0x1 << 2;
	public static final int MERGE_ERROR = 0x1 << 3;
	public static final int DUMP_GRAPH = 0x1 << 4;
	public static final int TRACE_HEURISTIC = 0x1 << 5;

	public static final String TMP_HASHLOG_DIR = "/home/peizhaoo/hashlog";

	public static final int USEFUL_DEBUG_OPTION0 = debug_option(DEBUG_ONLY);
	public static final int USEFUL_DEBUG_OPTION1 = debug_option(MERGE_ERROR);
	public static final int USEFUL_DEBUG_OPTION2 = debug_option(MERGE_ERROR,
			DUMP_GRAPH);
	public static final int USEFUL_DEBUG_OPTION3 = debug_option(MERGE_ERROR,
			DUMP_GRAPH, PRINT_MATCHING_HISTORY, TRACE_HEURISTIC);
	public static final int USEFUL_DEBUG_OPTION4 = debug_option(MERGE_ERROR,
			DUMP_GRAPH, PRINT_MATCHING_HISTORY, MAIN_KNOWN);
	public static final int USEFUL_DEBUG_OPTION5 = debug_option(MERGE_ERROR,
			DUMP_GRAPH, PRINT_MATCHING_HISTORY, MAIN_KNOWN_ADD_MAIN);
	public static final int USEFUL_DEBUG_OPTION6 = debug_option(MERGE_ERROR,
			DUMP_GRAPH, MAIN_KNOWN);
	public static final int USEFUL_DEBUG_OPTION7 = debug_option(MERGE_ERROR,
			MAIN_KNOWN);

	public static final int DEBUG_OPTION = USEFUL_DEBUG_OPTION3;

	public static final boolean debug = true;

	public static int debug_option(int... options) {
		int opt = 0;
		for (int i = 0; i < options.length; i++) {
			opt |= options[i];
		}
		return opt;
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
	public static int debug_indirectCnt;
	// Times of indirect heuristic used that the two nodes does not match
	public static int debug_indirectUnmatchedCnt;

	public static MatchingTrace debug_matchingTrace;
	public static ContextSimilarityTrace debug_contextSimilarityTrace;

	public static void debug_init() {
		debug_pureHeuristicCnt = 0;
		debug_pureHeuristicNotPresentCnt = 0;
		debug_indirectCnt = 0;
		debug_indirectUnmatchedCnt = 0;
		debug_matchingTrace = new MatchingTrace();
		debug_contextSimilarityTrace = new ContextSimilarityTrace(
				GraphMerger.searchDepth);
	}

	public static void debug_stopHere() {
		// Simply do nothing, just for debugging
	}
}
