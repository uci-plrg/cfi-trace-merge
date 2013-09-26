package edu.uci.eecs.crowdsafe.merge.graph.tag;

import edu.uci.eecs.crowdsafe.common.data.graph.Node;

// TODO: this really only works for ClusterNode graphs on both sides, b/c the hashes will differ with ExecutionNode 
// and the equals() methods do not cross types.
class ClusterTagMergeEngine {

	private final ClusterTagMergeSession session;

	ClusterTagMergeEngine(ClusterTagMergeSession session) {
		this.session = session;
	}

	void mergeGraph() {
		for (Node<?> left : session.left.getAllNodes()) {
			Node<?> right = session.right.getNode(left.getKey());
			
		}
	}
}
