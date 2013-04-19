package analysis.graph.debug;

import analysis.graph.GraphMerger;

public class DebugUtils {
	public static final boolean debug = false;
	
	public static final int DEBUG_ONLY = 0x0;
	public static final int PRINT_MATCHING_HISTORY = 0x1;
	public static final int MAIN_KNOWN_ONLY = 0x2;
	public static final int MAIN_KNOWN_ADD_MAIN = 0x4;
	public static final int MERGE_ERROR = 0x8;
	
	public static final int DEBUG_OPTION = debugOption(DEBUG_ONLY);
//	public static final int DEBUG_OPTION = debugOption(DEBUG_ONLY, MERGE_ERROR, PRINT_MATCHING_HISTORY);
	
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
