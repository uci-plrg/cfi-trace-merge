package analysis.graph;

import java.util.HashMap;
import java.util.LinkedList;

import utils.AnalysisUtil;

import analysis.graph.debug.DebugUtils;
import analysis.graph.debug.MatchingInstance;
import analysis.graph.debug.MatchingType;
import analysis.graph.representation.ExecutionGraph;
import analysis.graph.representation.MatchedNodes;
import analysis.graph.representation.ModuleGraph;
import analysis.graph.representation.Node;
import analysis.graph.representation.PairNode;
import analysis.graph.representation.PairNodeEdge;

public class ModuleGraphMerger extends GraphMerger {

	public ModuleGraphMerger(ModuleGraph graph1, ModuleGraph graph2) {
		this.graph1 = graph1;
		this.graph2 = graph2;

		if (DebugUtils.debug_decision(DebugUtils.DUMP_GRAPH)) {
			GraphMergingInfo.dumpGraph(
					graph1,
					DebugUtils.GRAPH_DIR + graph1.getProgName() + "_"
							+ graph1.getPid() + ".dot");
			GraphMergingInfo.dumpGraph(
					graph2,
					DebugUtils.GRAPH_DIR + graph2.getProgName() + "_"
							+ graph2.getPid() + ".dot");
		}
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

		// Initialize debugging info before merging the graph
		if (DebugUtils.debug) {
			DebugUtils.debug_init();
		}

		HashMap<Long, Node> graph1Sig2Node = mGraph1.getSigature2Node(), graph2Sig2Node = mGraph2
				.getSigature2Node();
		for (long sigHash : graph1Sig2Node.keySet()) {
			if (graph2Sig2Node.containsKey(sigHash)) {
				Node n1 = graph1Sig2Node.get(sigHash);
				Node n2 = graph2Sig2Node.get(sigHash);

				PairNode pairNode = new PairNode(n1, n2, 0);
				matchedQueue.add(pairNode);
				matchedNodes.addPair(n1.getIndex(), n2.getIndex(), 0);

				if (DebugUtils.debug) {
					AnalysisUtil.outputIndirectNodesInfo(n1, n2);
				}

				if (DebugUtils.debug) {
					DebugUtils.debug_matchingTrace
							.addInstance(new MatchingInstance(0, n1.getIndex(),
									n2.getIndex(), MatchingType.SignatureNode,
									-1));
				}
			}
		}
		return true;
	}
}
