package analysis.graph.debug;

import analysis.graph.GraphMerger;

public class DebugUtils {
	public static final boolean debug = true;

	public static final MatchingTrace debug_matchingTrace = new MatchingTrace();
	public static ContextSimilarityTrace debug_contextSimilarityTrace = new ContextSimilarityTrace(
			GraphMerger.searchDepth);

	public static void stopHere() {
		// Simply do nothing, just for debugging
	}
}
