package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import java.util.HashMap;
import java.util.LinkedList;

import edu.uci.eecs.crowdsafe.analysis.data.graph.MatchedNodes;
import edu.uci.eecs.crowdsafe.analysis.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ModuleGraph;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.DebugUtils;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.MatchingInstance;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.MatchingType;

public class GraphMergeSession {

	enum State {
		INITIALIZATION,
		ENTRY_POINTS,
		AD_HOC,
		FINALIZE
	}

	State state = State.INITIALIZATION;

	final GraphMergeTarget left = new GraphMergeTarget(this);
	final GraphMergeTarget right = new GraphMergeTarget(this);
	
	

	void merge() {

	}

	public boolean preMergeGraph() {

		// Reset isVisited field
		for (int i = 0; i < right.getNodes().size(); i++) {
			right.getNodes().get(i).resetVisited();
		}

		// Record matched nodes
		matchedNodes = new MatchedNodes();

		hasConflict = false;

		// BFS on G2
		matchedQueue = new LinkedList<PairNode>();
		unmatchedQueue = new LinkedList<PairNode>();
		indirectChildren = new LinkedList<PairNodeEdge>();

		graphMergingInfo = new GraphMergingInfo(left, right, matchedNodes);

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
