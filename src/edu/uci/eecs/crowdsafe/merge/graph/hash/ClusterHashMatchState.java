package edu.uci.eecs.crowdsafe.merge.graph.hash;

import java.util.LinkedList;

import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.log.Log;

class ClusterHashMatchState {
	private final ClusterHashMergeSession session;

	private final LinkedList<HashNodeMatch> matchedQueue = new LinkedList<HashNodeMatch>();
	private final LinkedList<Node<?>> unmatchedQueue = new LinkedList<Node<?>>();
	private final LinkedList<HashEdgePair> indirectChildren = new LinkedList<HashEdgePair>();

	ClusterHashMatchState(ClusterHashMergeSession session) {
		this.session = session;
	}

	void clear() {
		matchedQueue.clear();
		unmatchedQueue.clear();
		indirectChildren.clear();
	}

	void enqueueMatch(HashNodeMatch match) {
		matchedQueue.add(match);
		session.debugLog.matchEnqueued(match);

		if (!match.isValid()) {
			Log.log("Mismatch: %s to %s on %s!", match.getLeftNode(), match.getRightNode(), match.type);
		}
	}

	HashNodeMatch dequeueMatch() {
		HashNodeMatch unmatched = matchedQueue.remove();
		session.debugLog.matchDequeued(unmatched);
		return unmatched;
	}

	int getMatchCount() {
		return matchedQueue.size();
	}

	int getUnmatchCount() {
		return unmatchedQueue.size();
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

	void enqueueIndirectEdge(HashEdgePair rightEdge) {
		indirectChildren.add(rightEdge);
		session.debugLog.indirectEdgeEnqueued(rightEdge);
	}

	HashEdgePair dequeueIndirectEdge() {
		HashEdgePair rightEdge = indirectChildren.remove();
		session.debugLog.indirectEdgeDequeued(rightEdge);
		return rightEdge;
	}

	boolean hasIndirectEdges() {
		return !indirectChildren.isEmpty();
	}
}
