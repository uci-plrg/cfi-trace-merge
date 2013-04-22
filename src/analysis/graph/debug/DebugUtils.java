package analysis.graph.debug;

import analysis.graph.GraphMerger;

public class DebugUtils {
	
	public static final int DEBUG_ONLY = 0x0;
	public static final int PRINT_MATCHING_HISTORY = 0x1;
	public static final int MAIN_KNOWN = 0x1 << 1;
	public static final int MAIN_KNOWN_ADD_MAIN = 0x1 << 2;
	public static final int MERGE_ERROR = 0x1 << 3;
	public static final int DUMP_GRAPH = 0x1 << 4;
	
	public static final String TMP_HASHLOG_DIR = "/home/peizhaoo/hashlog";
	
	public static final int USEFUL_DEBUG_OPTION0 = debugOption(DEBUG_ONLY);
	public static final int USEFUL_DEBUG_OPTION1 = debugOption(MERGE_ERROR);
	public static final int USEFUL_DEBUG_OPTION2 = debugOption(MERGE_ERROR, DUMP_GRAPH);
	public static final int USEFUL_DEBUG_OPTION3 = debugOption(MERGE_ERROR, DUMP_GRAPH, PRINT_MATCHING_HISTORY);
	public static final int USEFUL_DEBUG_OPTION4 = debugOption(MERGE_ERROR, DUMP_GRAPH, PRINT_MATCHING_HISTORY, MAIN_KNOWN);
	public static final int USEFUL_DEBUG_OPTION5 = debugOption(MERGE_ERROR, DUMP_GRAPH, PRINT_MATCHING_HISTORY, MAIN_KNOWN_ADD_MAIN);
	public static final int USEFUL_DEBUG_OPTION6 = debugOption(MERGE_ERROR, DUMP_GRAPH, MAIN_KNOWN);
	public static final int USEFUL_DEBUG_OPTION7 = debugOption(MERGE_ERROR, MAIN_KNOWN);
	
	public static final int DEBUG_OPTION = USEFUL_DEBUG_OPTION0;
	
	
	public static final boolean debug = true;
	
	public static int debugOption(int...options) {
		int opt = 0;
		for (int i = 0; i < options.length; i++) {
			opt |= options[i];
		}
		return opt;
	}
	
	public static boolean debugDecision(int...options) {
		if (!debug)
			return false;
		int opt = debugOption(options);
		// Check all the debug options
		for (int i = 0; i < options.length; i++) {
			if ((options[i] & DEBUG_OPTION) == 0)
				return false;
		}
		return true;
	}
	

	public static final MatchingTrace debug_matchingTrace = new MatchingTrace();
	public static ContextSimilarityTrace debug_contextSimilarityTrace = new ContextSimilarityTrace(
			GraphMerger.searchDepth);

	public static void stopHere() {
		// Simply do nothing, just for debugging
	}
}
