package edu.uci.eecs.crowdsafe.merge.graph;

import java.util.LinkedList;

class GraphMatchState {
	private final ClusterMergeSession session;

	private final LinkedList<PairNode> matchedQueue = new LinkedList<PairNode>();
	private final LinkedList<PairNode> unmatchedQueue = new LinkedList<PairNode>();
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
	}

	PairNode dequeueMatch() {
		PairNode unmatched = matchedQueue.remove();
		session.debugLog.matchDequeued(unmatched);
		return unmatched;
	}

	boolean hasMatches() {
		return !matchedQueue.isEmpty();
	}

	void enqueueUnmatch(PairNode unmatch) {
		unmatchedQueue.add(unmatch);
		session.debugLog.unmatchEnqueued(unmatch);
	}

	PairNode dequeueUnmatch() {
		PairNode unmatch = unmatchedQueue.remove();
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
