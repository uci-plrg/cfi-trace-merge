package analysis.graph.debug;

import java.util.ArrayList;

import analysis.graph.GraphMerger;

public class ContextSimilarityTrace {
	private ArrayList<MatchingInstance> traces;
	private int index;
	private int traceSize;

	public ContextSimilarityTrace() {
		index = -1;
		traceSize = GraphMerger.searchDepth + 1;
		traces = new ArrayList<MatchingInstance>(traceSize);
		for (int i = 0; i < traceSize; i++) {
			traces.add(null);
		}
	}

	public void addTraceAtDepth(int depth, MatchingInstance inst) {
		index = depth;
		traces.set(depth, inst);
	}

	public void printTrace() {
		for (int i = index; i >=0; i--) {
			MatchingInstance inst = traces.get(i);
			System.out.println(inst.level + ":" + inst.matchingType + ":"
					+ inst.index1 + "<->" + inst.index2);
		}
	}
}
