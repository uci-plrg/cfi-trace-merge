package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.analysis.data.graph.Node;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;
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

	final GraphMergeTarget left;
	final GraphMergeTarget right;

	final GraphMergeStatistics graphMergingInfo;

	final MatchedNodes matchedNodes;

	final LinkedList<PairNode> matchedQueue = new LinkedList<PairNode>();
	final LinkedList<PairNode> unmatchedQueue = new LinkedList<PairNode>();
	final LinkedList<PairNodeEdge> indirectChildren = new LinkedList<PairNodeEdge>();

	// The speculativeScoreList, which records the detail of the scoring of
	// all the possible cases
	final SpeculativeScoreList speculativeScoreList = new SpeculativeScoreList(
			this);

	// In case of recursively compute the similarity of cyclic graph, record
	// the compared nodes every time getContextSimilarity is called
	Set<Node> comparedNodes = new HashSet<Node>();

	private final Map<Node, Integer> scoresByLeftNode = new HashMap<Node, Integer>();

	boolean hasConflict;

	final GraphMergeEngine engine = new GraphMergeEngine(this);

	GraphMergeSession(ModuleGraphCluster left, ModuleGraphCluster right) {
		this.left = new GraphMergeTarget(this, left);
		this.right = new GraphMergeTarget(this, right);
		matchedNodes = new MatchedNodes();
		graphMergingInfo = new GraphMergeStatistics(this);
	}

	public boolean initializeMerge() {
		right.visitedNodes.clear();
		matchedNodes.clear();
		matchedQueue.clear();
		unmatchedQueue.clear();
		indirectChildren.clear();
		speculativeScoreList.clear();
		graphMergingInfo.reset();
		hasConflict = false;

		// Initialize debugging info before merging the graph
		if (DebugUtils.debug) {
			DebugUtils.debug_init();
		}

		Map<Long, ExecutionNode> leftEntryPoints = left.cluster
				.getEntryPoints();
		Map<Long, ExecutionNode> rightEntryPoints = right.cluster
				.getEntryPoints();
		for (long sigHash : rightEntryPoints.keySet()) {
			if (leftEntryPoints.containsKey(sigHash)) {
				ExecutionNode leftNode = leftEntryPoints.get(sigHash);
				ExecutionNode rightNode = rightEntryPoints.get(sigHash);

				PairNode pairNode = new PairNode(leftNode, rightNode, 0);
				matchedQueue.add(pairNode);
				matchedNodes.addPair(leftNode.getKey(), rightNode.getKey(), 0);

				graphMergingInfo.directMatch();

				if (DebugUtils.debug) {
					// AnalysisUtil.outputIndirectNodesInfo(n1, n2);
				}

				if (DebugUtils.debug) {
					DebugUtils.debug_matchingTrace
							.addInstance(new MatchingInstance(0, leftNode
									.getKey(), rightNode.getKey(),
									MatchingType.SignatureNode, null));
				}
			} else {
				// Push new signature node to prioritize the speculation to the
				// beginning of the graph
				ExecutionNode n2 = rightEntryPoints.get(sigHash);
				// TODO: guessing that the third arg "level" should be 0
				unmatchedQueue.add(new PairNode(null, n2, 0));
				engine.addUnmatchedNode2Queue(n2, -1);
			}
		}
		return true;
	}

	void setScore(Node leftNode, int score) {
		scoresByLeftNode.put(leftNode, score);
	}

	int getScore(Node leftNode) {
		Integer score = scoresByLeftNode.get(leftNode);
		if (score != null)
			return score;
		return 0;
	}
}