package edu.uci.eecs.crowdsafe.merge.graph;

import java.util.LinkedList;

import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.log.Log;

class GraphMatchState {
	private final ClusterMergeSession session;

	private final LinkedList<PairNode> matchedQueue = new LinkedList<PairNode>();
	private final LinkedList<Node<?>> unmatchedQueue = new LinkedList<Node<?>>();
	private final LinkedList<PairNodeEdge> indirectChildren = new LinkedList<PairNodeEdge>();

	GraphMatchState(ClusterMergeSession session) {
		this.session = session;
	}

	void clear() {
		matchedQueue.clear();
		unmatchedQueue.clear();
		indirectChildren.clear();
	}

	void enqueueMatch(PairNode match) {
		matchedQueue.add(match);
		session.debugLog.matchEnqueued(match);

		if (!match.isValid()) {
			Log.log("Mismatch: %s to %s on %s!", match.getLeftNode(), match.getRightNode(), match.type);
		}
	}

	PairNode dequeueMatch() {
		PairNode unmatched = matchedQueue.remove();
		session.debugLog.matchDequeued(unmatched);
		return unmatched;
	}

	boolean hasMatches() {
		return !matchedQueue.isEmpty();
	}

	void enqueueUnmatch(Node<?> unmatch) {
		unmatchedQueue.add(unmatch);
		session.debugLog.unmatchEnqueued(unmatch);
	}

	Node<?> dequeueUnmatch() {
		Node<?> unmatch = unmatchedQueue.remove();
		session.debugLog.unmatchDequeued(unmatch);
		return unmatch;
	}

	boolean hasUnmatches() {
		return !unmatchedQueue.isEmpty();
	}

	void enqueueIndirectEdge(PairNodeEdge rightEdge) {
		indirectChildren.add(rightEdge);
		session.debugLog.indirectEdgeEnqueued(rightEdge);
	}

	PairNodeEdge dequeueIndirectEdge() {
		PairNodeEdge rightEdge = indirectChildren.remove();
		session.debugLog.indirectEdgeDequeued(rightEdge);
		return rightEdge;
	}

	boolean hasIndirectEdges() {
		return !indirectChildren.isEmpty();
	}
}
