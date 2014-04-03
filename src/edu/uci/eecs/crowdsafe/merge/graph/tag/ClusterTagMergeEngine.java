package edu.uci.eecs.crowdsafe.merge.graph.tag;

import java.util.HashSet;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBasicBlock;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata.ClusterMetadataSequence;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ModuleEdgeCounter;

// TODO: this really only works for ClusterNode graphs on both sides, b/c the hashes will differ with ExecutionNode 
// and the equals() methods reject other types.
class ClusterTagMergeEngine {

	private final ClusterTagMergeSession session;

	ClusterTagMergeEngine(ClusterTagMergeSession session) {
		this.session = session;
	}

	void mergeGraph() {
		/**
		 * <pre>
		if ((session.right.graph.metadata.getRootSequence() != null)
				&& (session.right.graph.metadata.getRootSequence().executions.size() == 1)) {
			for (Node<?> right : session.right.graph.getAllNodes())
				Log.log("Base node %s", right);
		}
		 */

		addLeftNodes();
		addLeftEdges();
		mergeMetadata();

		// reportUnexpectedCode();
		// reportAddedSubgraphs();
	}

	private void addLeftNodes() {
		session.subgraphAnalysisEnabled = true; // (session.left.getAllNodes().size() < 20000);
		boolean rightAdded;
		for (Node<?> left : session.left.getAllNodes()) {
			rightAdded = false;
			ClusterNode<?> right = getCorrespondingNode(left);
			if (right != null) {
				if (!verifyMatch(left, right))
					right = null;
			}
			if (right == null) {
				right = session.right.addNode(left.getHash(), left.getModule(), left.getRelativeTag(), left.getType());
				session.statistics.nodeAdded();
				session.mergeFragment.nodeAdded(right);
				//Log.log("Merging node %s", right);
				if (session.subgraphAnalysisEnabled) {
					session.subgraphs.nodeAdded(right);
				}
				rightAdded = true;
				// } else {
				// Log.log("Matched node %s", right);
			}
			enqueueLeftEdges(left, right, rightAdded);
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
				session.mergeFragment.edgeAdded(newRightEdge);
				if (session.subgraphAnalysisEnabled)
					session.subgraphs.edgeAdded(newRightEdge);
			} catch (IllegalArgumentException e) {
				Log.log("Error merging edges! %s", e.getMessage());
				Log.log(e);
			}
		}
	}

	private void mergeMetadata() {
		if (session.left.metadata.isEmpty()) {
			if (session.left.metadata.isMain()) {
				Log.log("Left main has no metadata. Cannot merge it.");
			}
			return;
		}

		if (session.left.metadata.isSingletonExecution()) {
			session.left.metadata.retainMergedUIBs(session.mergeFragment.getAddedEdges());
		}

		if (session.right.graph.metadata.isEmpty()) {
			if (session.left.metadata.isMain()) {
				Log.log("Pushing left metadata onto an empty right sequence for the main module %s",
						session.left.cluster.name);
				int i = 0;
				for (ClusterMetadataSequence sequence : session.left.metadata.sequences.values()) {
					Log.log("\tSequence %d has %d executions%s", i++, sequence.executions.size(),
							sequence.isRoot() ? " (root)" : "");
				}
			}
			session.right.graph.metadata.sequences.putAll(session.left.metadata.sequences);
			return;
		}

		if (session.left.metadata.isSingletonExecution()) {
			if (session.left.metadata.isMain()) {
				Log.log("Pushing left singleton metadata onto the right sequence for the main module %s; sequence size %d",
						session.left.cluster.name, session.right.graph.metadata.getRootSequence().executions.size());
			}
			session.right.graph.metadata.getRootSequence().addExecution(session.left.metadata.getSingletonExecution());
			if (session.left.metadata.isMain()) {
				Log.log("Sequence size is now %d", session.right.graph.metadata.getRootSequence().executions.size());
			}
		} else {
			if (session.left.metadata.isMain()) {
				Log.log("Left main is not a singleton");
			}
			for (ClusterMetadataSequence leftSequence : session.left.metadata.sequences.values()) {
				session.right.graph.metadata.mergeSequence(leftSequence);
			}
		}
	}

	private ClusterNode<?> getCorrespondingNode(Node<?> left) {
		ClusterNode<?> right = session.right.graph.getNode(left.getKey());
		if ((right != null) && ((right.getHash() == left.getHash()) || isThatWonkyNode(left, right)))
			return right;

		NodeList<ClusterNode<?>> byHash = session.right.graph.getGraphData().nodesByHash.get(left.getHash());
		if (byHash != null) {
			for (int i = 0; i < byHash.size(); i++) {
				ClusterNode<?> next = byHash.get(i);
				if (left.isModuleRelativeEquivalent(next) || isThatWonkyNode(left, next)) {
					Log.log("Module-relative hash match of 0x%x: 0x%x-v%d <-> 0x%x-v%d in %s", next.getHash(),
							left.getRelativeTag(), left.getInstanceId(), next.getRelativeTag(), next.getInstanceId(),
							next.getModule().unit.filename);
					return next;
				}
			}
		}

		return null;
	}

	private/* hack */boolean isThatWonkyNode(Node<?> left, Node<?> right) {
		if ((left instanceof ClusterBasicBlock) && (right instanceof ClusterBasicBlock)) {
			ClusterBasicBlock bbLeft = (ClusterBasicBlock) left;
			ClusterBasicBlock bbRight = (ClusterBasicBlock) right;

			if (bbLeft.getKey().module.unit.filename.startsWith("ipcsecproc")
					&& bbRight.getKey().module.unit.filename.startsWith("ipcsecproc")) {
				if (((bbLeft.getRelativeTag() == 0x3219bL) && (bbRight.getRelativeTag() == 0x3219bL))
						|| ((bbLeft.getRelativeTag() == 0xc8e5bL) && (bbRight.getRelativeTag() == 0xc8e5bL))) {
					Log.log("Warning: hack match %s with %s", left, right);
					return true;
				}
			}
		}
		return false;
	}

	private void enqueueLeftEdges(Node<?> left, ClusterNode<?> right, boolean rightAdded) {
		OrdinalEdgeList<ClusterNode<?>> rightEdges = right.getOutgoingEdges();
		OrdinalEdgeList<?> leftEdges = left.getOutgoingEdges();
		try {
			for (Edge<?> leftEdge : leftEdges) {
				if (rightEdges.containsModuleRelativeEquivalent(leftEdge)) {
					session.statistics.edgeMatched();
				} else {
					switch (leftEdge.getEdgeType()) {
						case UNEXPECTED_RETURN:
							// reportUnexpectedReturn(leftEdge, rightAdded);
							break;
						case EXCEPTION_CONTINUATION:
							Log.log("Merging exception-continuation from %s tag: %s", rightAdded ? "unknown"
									: "existing", leftEdge);
							break;
						case GENCODE_WRITE:
							Log.log("Merging gencode-write from %s tag: %s", rightAdded ? "unknown" : "existing",
									leftEdge);
							break;
					}

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
		if (isThatWonkyNode(left, right))
			return true;

		if (left.getHash() != right.getHash()) {
			session.statistics.hashMismatch(left, right);
			return false;
		}
		// throw new MergedFailedException(
		// "Left node %s has module relative equivalent %s with a different hashcode!", left, right);
		if (!left.hasCompatibleEdges(right)) {
			session.statistics.edgeMismatch(left, right);

			Log.log("Edge mismatch: left node %s has module relative equivalent %s with incompatible edges!", left,
					right);
			left.getOutgoingEdges().logEdges("\tLeft outgoing: %s");
			right.getOutgoingEdges().logEdges("\tRight outgoing: %s");

			return false;
		}
		return true;
	}

	private void reportUnexpectedReturn(Edge<?> leftEdge, boolean rightAdded) {
		Log.log("Warning: merging an unexpected return: %s. Right node new? %b", leftEdge, rightAdded);

		if (rightAdded) {
			Log.log("\tDataset does not contain 'from' node %s.", leftEdge.getFromNode());
		} else {
			ClusterNode<?> rightFromNode = session.right.graph.getNode(leftEdge.getFromNode().getKey());
			if ((rightFromNode != null) && (rightFromNode.getHash() != leftEdge.getFromNode().getHash()))
				rightFromNode = null;
			if (rightFromNode != null) {
				Log.log("\tDataset contains right 'from' node %s. Edges are:", leftEdge.getFromNode());
				rightFromNode.getIncomingEdges().logEdges("\t\tIncoming: %s");
				rightFromNode.getOutgoingEdges().logEdges("\t\tOutgoing: %s");
			}
		}

		ClusterNode<?> rightToNode = session.right.graph.getNode(leftEdge.getToNode().getKey());
		if ((rightToNode != null) && (rightToNode.getHash() != leftEdge.getToNode().getHash()))
			rightToNode = null;
		if (rightToNode != null) {
			Log.log("\tDataset contains right 'to' node %s. Edges are:", leftEdge.getToNode());
			rightToNode.getIncomingEdges().logEdges("\t\tIncoming: %s");
			rightToNode.getOutgoingEdges().logEdges("\t\tOutgoing: %s");
		}
	}

	private void reportUnexpectedCode() {
		Log.log("Unexpected code summary for %s: %d nodes, %d edges, %d subgraphs",
				session.right.graph.cluster.getUnitFilename(), session.subgraphs.getTotalUnmatchedNodes(),
				session.subgraphs.getTotalUnmatchedEdges(), session.subgraphs.getSubgraphCount());
		Log.log("Unexpected indirect branches: %d T, %d K->K, %d K->U, %d U->K, %d U->U",
				session.subgraphs.unmatchedIndirectCounts.getTotal(),
				session.subgraphs.unmatchedIndirectCounts.getWithinExpected(),
				session.subgraphs.unmatchedIndirectCounts.getToUnexpected(),
				session.subgraphs.unmatchedIndirectCounts.getFromUnexpected(),
				session.subgraphs.unmatchedIndirectCounts.getWithinUnexpected());
	}

	private void reportAddedSubgraphs() {
		ModuleEdgeCounter edgeCounter = new ModuleEdgeCounter();
		Set<Node> bridgeNodes = new HashSet<Node>();
		StringBuilder buffer = new StringBuilder();

		for (ClusterTagMergedSubgraphs.Subgraph subgraph : session.subgraphs.getSubgraphs()) {
			if (subgraph.getNodeCount() > 10) {
				int indirectEntryEdges = 0;
				for (Edge<? extends Node> edge : subgraph.getEntries()) {
					bridgeNodes.add(edge.getToNode());
					if (edge.isClusterEntry()) {
						edgeCounter.tallyInterEdge(edge.getEdgeType());
					} else {
						edgeCounter.tallyIntraEdge(edge.getEdgeType());
						if (edge.getEdgeType() == EdgeType.INDIRECT)
							indirectEntryEdges++;
					}
				}
				int entryNodes = bridgeNodes.size();
				bridgeNodes.clear();

				for (Edge<? extends Node> edge : subgraph.getExits()) {
					Node<?> neighbor = edge.getToNode();
					bridgeNodes.add(neighbor);
					switch (neighbor.getType()) {
						case CLUSTER_EXIT:
							edgeCounter.tallyInterEdge(edge.getEdgeType());
							break;
						default:
							edgeCounter.tallyIntraEdge(edge.getEdgeType());
					}
				}
				int exitNodes = bridgeNodes.size();
				bridgeNodes.clear();

				buffer.setLength(0);
				buffer.append("(");
				buffer.append(subgraph.getEntries().size());
				if (indirectEntryEdges > 0) {
					buffer.append("[");
					buffer.append(indirectEntryEdges);
					buffer.append("I]");
				}
				buffer.append(">");
				buffer.append(entryNodes);
				buffer.append("/");
				buffer.append(exitNodes);
				buffer.append("<");
				buffer.append(subgraph.getExits().size());
				buffer.append(") bridges | Intra (");
				for (EdgeType type : EdgeType.values()) {
					buffer.append(type.code);
					buffer.append(": ");
					buffer.append(edgeCounter.getIntraCount(type));
					buffer.append(" ");
				}
				buffer.setLength(buffer.length() - 1);
				buffer.append(") Inter (");
				for (EdgeType type : EdgeType.values()) {
					buffer.append(type.code);
					buffer.append(": ");
					buffer.append(edgeCounter.getInterCount(type));
					buffer.append(" ");
				}
				buffer.setLength(buffer.length() - 1);
				buffer.append(")");

				String bridgeProfile = buffer.toString();

				edgeCounter.reset();
				int innerEdgeCount = 0;
				for (Edge<? extends Node> edge : subgraph.getEdges()) {
					if (!(subgraph.getEntries().contains(edge) || subgraph.getExits().contains(edge))) {
						innerEdgeCount++;
						if (edge.isClusterEntry() || edge.isClusterExit()) {
							edgeCounter.tallyInterEdge(edge.getEdgeType());
						} else {
							edgeCounter.tallyIntraEdge(edge.getEdgeType());
						}
					}
				}

				buffer.setLength(0);
				buffer.append(innerEdgeCount);
				buffer.append(" inner edges | Intra (");
				for (EdgeType type : EdgeType.values()) {
					buffer.append(type.code);
					buffer.append(": ");
					buffer.append(edgeCounter.getIntraCount(type));
					buffer.append(" ");
				}
				buffer.setLength(buffer.length() - 1);
				buffer.append(") Inter (");
				for (EdgeType type : EdgeType.values()) {
					buffer.append(type.code);
					buffer.append(": ");
					buffer.append(edgeCounter.getInterCount(type));
					buffer.append(" ");
				}
				buffer.setLength(buffer.length() - 1);
				buffer.append(")");

				String edgeProfile = buffer.toString();

				Log.log("Profile of %d node subgraph in %s (max path %d, max indirects %d):\n\t%s\n\t%s",
						subgraph.getNodeCount(), session.right.graph.cluster.getUnitFilename(),
						subgraph.getMaximumPathLength(), subgraph.getMaximumIndirectsInPath(), bridgeProfile,
						edgeProfile);

				if (subgraph.getNodeCount() < 50)
					subgraph.logGraph();
			}
		}
	}
}
