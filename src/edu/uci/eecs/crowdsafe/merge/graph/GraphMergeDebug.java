package edu.uci.eecs.crowdsafe.merge.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessGraphLoadSession;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessGraphLoadSession.LoadTarget;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.graph.debug.DebugUtils;
import edu.uci.eecs.crowdsafe.merge.graph.debug.MatchingInstance;
import edu.uci.eecs.crowdsafe.merge.graph.debug.MatchingType;

public class GraphMergeDebug implements
		ProcessGraphLoadSession.LoadEventListener {

	private static class TrackedNodeKey {
		int pageOffset;
		long hash;

		TrackedNodeKey() {
		}

		TrackedNodeKey(int pageOffset, long hash) {
			this.pageOffset = pageOffset;
			this.hash = hash;
		}

		TrackedNodeKey assign(ExecutionNode node) {
			this.pageOffset = (int) (node.getKey().relativeTag & 0xfff);
			this.hash = node.getHash();
			return this;
		}

		TrackedNodeKey assign(long tag, long hash) {
			this.pageOffset = (int) (tag & 0xfff);
			this.hash = hash;
			return this;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (int) (hash ^ (hash >>> 32));
			result = prime * result + pageOffset;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TrackedNodeKey other = (TrackedNodeKey) obj;
			if (hash != other.hash)
				return false;
			if (pageOffset != other.pageOffset)
				return false;
			return true;
		}
	}

	private GraphMergeSession session;

	private final Set<TrackedNodeKey> trackedNodes = new HashSet<TrackedNodeKey>();
	private final TrackedNodeKey trackedNodeLookupKey = new TrackedNodeKey();

	public GraphMergeDebug() {
		// TODO: hash differs on peer run of ls: omit absolute ops for nodes in the unknown module?

		// __unknown__(0x1c3d1903-v0|0x1fe439402a) has no incoming edges
		// trackedNodes.add(new TrackedNodeKey(0x903, 0x1fe439402aL));

		// __unknown__(0x1c3d21fd-v0|0x1fe3123f2a) has no incoming edges
		// trackedNodes.add(new TrackedNodeKey(0x1fd, 0x1fe3123f2aL));

		// __unknown__(0x1c3d2011-v0|0x1fe4e7c22a) has no incoming edges
		// trackedNodes.add(new TrackedNodeKey(0x011, 0x1fe4e7c22aL));

		// msys-1.0.dll(0x11bd-v0|0x29f156) has no incoming edges
		// trackedNodes.add(new TrackedNodeKey(0x1bd, 0x29f156L));
	}

	void setSession(GraphMergeSession session) {
		this.session = session;
	}

	void initializeMerge(ModuleGraphCluster left, ModuleGraphCluster right) {
		// In the OUTPUT_SCORE debug mode, initialize the PrintWriter for this
		// merging process
		if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
			if (DebugUtils.getScorePW() != null) {
				DebugUtils.getScorePW().flush();
				DebugUtils.getScorePW().close();
			}
			String fileName = left.getGraphData().containingGraph.dataSource
					.getProcessName()
					+ ".score-"
					+ left.getGraphData().containingGraph.dataSource
							.getProcessId()
					+ "-"
					+ right.getGraphData().containingGraph.dataSource
							.getProcessId() + ".txt";
			DebugUtils.setScorePW(fileName);
		}
	}

	void directMatch(PairNode pairNode, Edge<? extends Node> rightEdge,
			Node leftChild) {
		if (DebugUtils.debug) {
			MatchingType matchType = rightEdge.getEdgeType() == EdgeType.DIRECT ? MatchingType.DirectBranch
					: MatchingType.CallingContinuation;
			DebugUtils.debug_matchingTrace.addInstance(new MatchingInstance(
					pairNode.level, leftChild.getKey(), rightEdge.getToNode()
							.getKey(), matchType, rightEdge.getToNode()
							.getKey()));
		}

		if (DebugUtils.debug_decision(DebugUtils.PRINT_MATCHING_HISTORY)) {
			MatchingType matchType = rightEdge.getEdgeType() == EdgeType.DIRECT ? MatchingType.DirectBranch
					: MatchingType.CallingContinuation;
			Log.log(matchType + ": " + leftChild.getKey() + "<->"
					+ rightEdge.getToNode().getKey() + "(by "
					+ pairNode.getLeftNode().getKey() + "<->"
					+ rightEdge.getToNode().getKey() + ")");
		}
	}

	void indirectMatch(PairNodeEdge nodeEdgePair,
			Edge<? extends Node> rightEdge, Node leftChild) {
		if (DebugUtils.debug) {
			DebugUtils.debug_matchingTrace.addInstance(new MatchingInstance(
					nodeEdgePair.level, leftChild.getKey(), rightEdge
							.getToNode().getKey(), MatchingType.IndirectBranch,
					nodeEdgePair.getRightParentNode().getKey()));
		}

		if (DebugUtils.debug_decision(DebugUtils.PRINT_MATCHING_HISTORY)) {
			// Print out indirect nodes that must be decided by
			// heuristic
			System.out.print("Indirect: " + leftChild.getKey() + "<->"
					+ rightEdge.getToNode().getKey() + "(by "
					+ nodeEdgePair.getLeftParentNode().getKey() + "<->"
					+ nodeEdgePair.getRightParentNode().getKey() + ")");
			Log.log();
		}
	}

	void heuristicMatch(PairNode pairNode, Node leftChild) {
		if (DebugUtils.debug) {
			DebugUtils.debug_matchingTrace.addInstance(new MatchingInstance(
					pairNode.level, leftChild.getKey(), pairNode.getRightNode()
							.getKey(), MatchingType.PureHeuristic, null));
		}

		if (DebugUtils.debug_decision(DebugUtils.PRINT_MATCHING_HISTORY)) {
			// Print out indirect nodes that must be decided by
			// heuristic
			Log.log("PureHeuristic: " + leftChild.getKey() + "<->"
					+ pairNode.getRightNode().getKey() + "(by pure heuristic)");
		}
	}

	void mergeCompleted() {
		if (DebugUtils.debug_decision(DebugUtils.TRACE_HEURISTIC)) {
			Log.log("All pure heuristic: " + DebugUtils.debug_pureHeuristicCnt);
			Log.log("Pure heuristic not present: "
					+ DebugUtils.debug_pureHeuristicNotPresentCnt);
			Log.log("All direct unsmatched: "
					+ DebugUtils.debug_directUnmatchedCnt);
			Log.log("All indirect heuristic: "
					+ DebugUtils.debug_indirectHeuristicCnt);
			Log.log("Indirect heuristic unmatched: "
					+ DebugUtils.debug_indirectHeuristicUnmatchedCnt);
		}

		// In the OUTPUT_SCORE debug mode, close the PrintWriter when merging
		// finishes, also print out the score statistics
		if (DebugUtils.debug_decision(DebugUtils.OUTPUT_SCORE)) {
			DebugUtils.getScorePW().flush();
			DebugUtils.getScorePW().close();
		}
	}

	void reportUnmatchedNodes() {
		reportUnmatchedNodes(session.left.cluster, session.right.cluster,
				"left");
		reportUnmatchedNodes(session.right.cluster, session.left.cluster,
				"right");
	}

	private void reportUnmatchedNodes(ModuleGraphCluster cluster,
			ModuleGraphCluster oppositeCluster, String side) {
		Set<Node.Key> unmatchedNodes = new HashSet<Node.Key>(
				cluster.getGraphData().nodesByKey.keySet());
		unmatchedNodes.removeAll(session.matchedNodes.getLeftKeySet());
		int totalUnmatchedCount = unmatchedNodes.size();
		int unreachableUnmatchedCount = 0;
		for (Node unreachable : cluster.getUnreachableNodes()) {
			unmatchedNodes.remove(unreachable.getKey());
			unreachableUnmatchedCount++;
		}
		int hashExclusionCount = 0;
		for (Node.Key unmatchedKey : new ArrayList<Node.Key>(unmatchedNodes)) {
			Node unmatchedNode = cluster.getGraphData().nodesByKey
					.get(unmatchedKey);
			if (!oppositeCluster.getGraphData().nodesByHash.keySet().contains(
					unmatchedNode.getHash())) {
				unmatchedNodes.remove(unmatchedKey);
				hashExclusionCount++;
			}
		}

		Log.log();
		Log.log("%d total unmatched nodes, with %d outstanding.",
				totalUnmatchedCount, unmatchedNodes.size());
		Log.log("\t%d of them were unreachable.", unreachableUnmatchedCount);
		Log.log("\t%d hash-exclusive to the %s graph.", hashExclusionCount,
				side);
		// for (Node.Key unmatched : unmatchedNodes) {
		// Log.log("\tLeft node unmatched: %s",
		// cluster.getGraphData().nodesByKey.get(unmatched));
		// }
	}

	private boolean isTracked(Node node) {
		if (node instanceof ExecutionNode) {
			return trackedNodes.contains(trackedNodeLookupKey
					.assign((ExecutionNode) node));
		}
		return false;
	}

	void nodesMatched(Node left, Node right) {
		if (isTracked(left) || isTracked(right))
			Log.log("Node %s matched with node %s", left.toString(),
					right.toString());
	}

	void matchEnqueued(PairNode match) {
		if (isTracked(match.getLeftNode()) || isTracked(match.getRightNode()))
			Log.log("Enqueue matched pair %s and %s", match.getLeftNode(),
					match.getRightNode());
	}

	void matchDequeued(PairNode match) {
		if (isTracked(match.getLeftNode()) || isTracked(match.getRightNode()))
			Log.log("Dequeue matched pair %s and %s", match.getLeftNode(),
					match.getRightNode());
	}

	void unmatchEnqueued(PairNode unmatch) {
		if (isTracked(unmatch.getLeftNode())
				|| isTracked(unmatch.getRightNode()))
			Log.log("Enqueue unmatched pair %s and %s", unmatch.getLeftNode(),
					unmatch.getRightNode());
	}

	void unmatchDequeued(PairNode unmatch) {
		if (isTracked(unmatch.getLeftNode())
				|| isTracked(unmatch.getRightNode()))
			Log.log("Dequeue unmatched pair %s and %s", unmatch.getLeftNode(),
					unmatch.getRightNode());
	}

	void indirectEdgeEnqueued(PairNodeEdge rightEdge) {
		if (isTracked(rightEdge.getLeftParentNode())
				|| isTracked(rightEdge.getRightParentNode()))
			Log.log("Enqueue unmatched pair %s and %s",
					rightEdge.getLeftParentNode(),
					rightEdge.getRightParentNode());
	}

	void indirectEdgeDequeued(PairNodeEdge rightEdge) {
		if (isTracked(rightEdge.getLeftParentNode())
				|| isTracked(rightEdge.getRightParentNode()))
			Log.log("Dequeue unmatched pair %s and %s",
					rightEdge.getLeftParentNode(),
					rightEdge.getRightParentNode());
	}

	@Override
	public void nodeLoadReference(long tag, long hash, LoadTarget target) {
		if (trackedNodes.contains(trackedNodeLookupKey.assign(tag, hash)))
			Log.log("Node 0x%x(0x%x) referenced during %s load.", tag, hash,
					target.displayName);
	}

	@Override
	public void nodeLoadReference(Node node, LoadTarget target) {
		if (isTracked(node))
			Log.log("Node %s referenced during %s load.", node.toString(),
					target.displayName);
	}

	@Override
	public void nodeCreation(Node node) {
		if (isTracked(node))
			Log.log("Node %s created.", node.toString());
	}

	@Override
	public void edgeCreation(Edge edge) {
		if (isTracked(edge.getFromNode()) || isTracked(edge.getToNode())) {
			Log.log("Edge %s created.", edge.toString());
		}
	}

	@Override
	public void graphAddition(Node node, ModuleGraphCluster cluster) {
		if (isTracked(node)) {
			Log.log("Node %s added to cluster %s.", node.toString(),
					cluster.distribution.name);
		}
	}
}
