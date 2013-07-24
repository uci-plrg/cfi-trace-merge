package analysis.graph;

import utils.AnalysisUtil;
import analysis.exception.graph.WrongEdgeTypeException;
import analysis.graph.debug.DebugUtils;
import analysis.graph.representation.ExecutionGraph;

/**
 * <p>
 * This class abstracts a Java worker thread that basically matches two graphs.
 * To initialize these two graphs, pass two well-constructed ExecutionGraph to
 * it and start the thread. When it runs, it will merge the two graphs and
 * construct a new merged graph, which you can get it by calling the
 * getMergedGraph() method.
 * </p>
 */
public class GraphMergerThread extends GraphMerger implements Runnable {
	
	public GraphMergerThread(ExecutionGraph graph1, ExecutionGraph graph2) {
		super(graph1, graph2);
	}
	
	public void run() {
		try {
			// Before merging, cheat to filter out all the immediate addresses
			if (DebugUtils.debug_decision(DebugUtils.FILTER_OUT_IMME_ADDR)) {
				AnalysisUtil.filteroutImmeAddr(graph1, graph2);
			}

			if (DebugUtils.debug) {
				// AnalysisUtil.outputTagComparisonInfo(graph1, graph2);
				// return;
			}

			mergedGraph = mergeGraph();
			mergedModules = mergeModules();
			
			if (hasConflict) {
				if (graph1.getProgName().equals(graph2.getProgName())) {
					System.out.println("Wrong match!");
				}
			} else {
				if (!graph1.getProgName().equals(graph2.getProgName())) {
					System.out.println("Unable to tell difference!");
				}
			}
		} catch (WrongEdgeTypeException e) {
			e.printStackTrace();
		}

	}
}