package edu.uci.eecs.crowdsafe.merge.graph.tag;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.log.Log;

// TODO: this really only works for ClusterNode graphs on both sides, b/c the hashes will differ with ExecutionNode 
// and the equals() methods reject other types.
class ClusterTagMergeEngine {

	private final ClusterTagMergeSession session;

	ClusterTagMergeEngine(ClusterTagMergeSession session) {
		this.session = session;
	}

	void mergeGraph() {
		addLeftNodes();
		addLeftEdges();
	}

	private void addLeftNodes() {
		session.subgraphAnalysisEnabled = (session.left.getAllNodes().size() < 20000);
		for (Node<?> left : session.left.getAllNodes()) {
			ClusterNode<?> right = getCorrespondingNode(left);
			if (right != null) {
				if (!verifyMatch(left, right))
					right = null;
			}
			if (right == null) {
				right = session.right.addNode(left.getHash(), left.getModule(), left.getRelativeTag(), left.getType());
				session.statistics.nodeAdded();
				if (session.subgraphAnalysisEnabled)
					session.subgraphs.nodeAdded(right);
			}
			enqueueLeftEdges(left, right);
		}

		session.subgraphAnalysisEnabled &= (session.edgeQueue.size() < 30000);
	}

	private void addLeftEdges() {
		for (int i = 0; i < session.edgeQueue.size(); i++) {
			ClusterNode<?> rightFromNode = session.edgeQueue.rightFromNodes.get(i);
			Edge<?> leftEdge = session.edgeQueue.leftEdges.get(i);
			ClusterNode<?> rightToNode = getCorrespondingNode(leftEdge.getToNode());
			Edge<ClusterNode<?>> newRightEdge = new Edge<ClusterNode<?>>(rightFromNode, rightToNode,
					leftEdge.getEdgeType(), leftEdge.getOrdinal());

			try {
				rightFromNode.addOutgoingEdge(newRightEdge);
				rightToNode.addIncomingEdge(newRightEdge);
				session.statistics.edgeAdded();
				if (session.subgraphAnalysisEnabled)
					session.subgraphs.edgeAdded(newRightEdge);
			} catch (IllegalArgumentException e) {
				Log.log("Error merging edges! %s", e.getMessage());
				Log.log(e);
			}
		}
	}

	private ClusterNode<?> getCorrespondingNode(Node<?> left) {
		ClusterNode<?> right = session.right.graph.getNode(left.getKey());
		if ((right != null) && (right.getHash() == left.getHash()))
			return right;

		NodeList<ClusterNode<?>> byHash = session.right.graph.getGraphData().nodesByHash.get(left.getHash());
		if (byHash != null) {
			for (int i = 0; i < byHash.size(); i++) {
				ClusterNode<?> next = byHash.get(i);
				if (left.isModuleRelativeEquivalent(next)) {
					Log.log("Module-relative hash match of 0x%x: 0x%x-v%d <-> 0x%x-v%d in %s", next.getHash(),
							left.getRelativeTag(), left.getInstanceId(), next.getRelativeTag(), next.getInstanceId(),
							next.getModule().unit.filename);
					return next;
				}
			}
		}

		return null;
	}

	private void enqueueLeftEdges(Node<?> left, ClusterNode<?> right) {
		OrdinalEdgeList<ClusterNode<?>> rightEdges = right.getOutgoingEdges();
		OrdinalEdgeList<?> leftEdges = left.getOutgoingEdges();
		try {
			for (Edge<?> leftEdge : leftEdges) {
				if (rightEdges.containsModuleRelativeEquivalent(leftEdge)) {
					session.statistics.edgeMatched();
				} else {
					session.edgeQueue.leftEdges.add(leftEdge);
					session.edgeQueue.rightFromNodes.add(right);
				}
			}
		} finally {
			leftEdges.release();
			rightEdges.release();
		}
	}

	private boolean verifyMatch(Node<?> left, Node<?> right) {
		if (left.getHash() != right.getHash()) {
			session.statistics.hashMismatch(left, right);
			return false;
		}
		// throw new MergedFailedException(
		// "Left node %s has module relative equivalent %s with a different hashcode!", left, right);
		if (!left.hasCompatibleEdges(right)) {
			session.statistics.edgeMismatch(left, right);
			return false;
		}
		// throw new MergedFailedException("Left node %s has module relative equivalent %s with incompatible edges!",
		// left, right);
		return true;
	}
}
