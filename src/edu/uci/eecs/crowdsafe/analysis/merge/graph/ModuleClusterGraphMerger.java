package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import java.util.HashMap;
import java.util.LinkedList;

import edu.uci.eecs.crowdsafe.analysis.data.graph.MatchedNodes;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ModuleGraph;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.DebugUtils;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.MatchingInstance;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.MatchingType;

public class ModuleClusterGraphMerger {

	private final ModuleGraphCluster graph1, graph2;

	public ModuleClusterGraphMerger(ModuleGraphCluster graph1,
			ModuleGraphCluster graph2) {
		if (graph1.calculateTotalNodeCount() > graph2.calculateTotalNodeCount()) {
			this.graph1 = graph1;
			this.graph2 = graph2;
		} else {
			this.graph1 = graph2;
			this.graph2 = graph1;
		}

		System.out.println("\n    ==== " + graph1.distribution.name + " & "
				+ graph2.distribution.name + " ====");

		/**
		 * <pre>
		if (DebugUtils.debug_decision(DebugUtils.DUMP_GRAPH)) {
			GraphMergingInfo.dumpGraph(graph1,
					DebugUtils.GRAPH_DIR + graph1.gCG dataSource.getProcessName()
							+ graph1.dataSource.getProcessName() + ".dot");
			GraphMergingInfo.dumpGraph(graph2,
					DebugUtils.GRAPH_DIR + graph2.dataSource.getProcessName()
							+ graph2.dataSource.getProcessName() + ".dot");
		}
		 */
	}

	public boolean preMergeGraph() {
		ModuleGraph mGraph1 = (ModuleGraph) graph1, mGraph2 = (ModuleGraph) graph2;

		if (!mGraph1.equals(mGraph2)) {
			System.out
					.println("Module graph matching requires the same modules!");
			return false;
		}

		// Reset isVisited field
		for (int i = 0; i < graph2.getNodes().size(); i++) {
			graph2.getNodes().get(i).resetVisited();
		}

		// Record matched nodes
		matchedNodes = new MatchedNodes();

		hasConflict = false;

		// BFS on G2
		matchedQueue = new LinkedList<PairNode>();
		unmatchedQueue = new LinkedList<PairNode>();
		indirectChildren = new LinkedList<PairNodeEdge>();

		graphMergingInfo = new GraphMergingInfo(graph1, graph2, matchedNodes);

		// Initialize debugging info before merging the graph
		if (DebugUtils.debug) {
			DebugUtils.debug_init();
		}

		HashMap<Long, ExecutionNode> graph1Sig2Node = mGraph1
				.getSigature2Node(), graph2Sig2Node = mGraph2
				.getSigature2Node();
		for (long sigHash : graph2Sig2Node.keySet()) {
			if (graph1Sig2Node.containsKey(sigHash)) {
				ExecutionNode n1 = graph1Sig2Node.get(sigHash);
				ExecutionNode n2 = graph2Sig2Node.get(sigHash);

				PairNode pairNode = new PairNode(n1, n2, 0);
				matchedQueue.add(pairNode);
				matchedNodes.addPair(n1.getIndex(), n2.getIndex(), 0);

				graphMergingInfo.directMatch();

				if (DebugUtils.debug) {
					// AnalysisUtil.outputIndirectNodesInfo(n1, n2);
				}

				if (DebugUtils.debug) {
					DebugUtils.debug_matchingTrace
							.addInstance(new MatchingInstance(0, n1.getIndex(),
									n2.getIndex(), MatchingType.SignatureNode,
									-1));
				}
			} else {
				// Push new signature node to prioritize the speculation to the
				// beginning of the graph
				ExecutionNode n2 = graph2Sig2Node.get(sigHash);
				addUnmatchedNode2Queue(n2, -1);
			}
		}
		return true;
	}
}
