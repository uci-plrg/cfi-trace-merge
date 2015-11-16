package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSGE;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSSC;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterUIB;

public class ModuleReportGenerator {

	class PendingEdgeQueue {
		final List<ClusterNode<?>> rightFromNodes = new ArrayList<ClusterNode<?>>();
		final List<Edge<?>> leftEdges = new ArrayList<Edge<?>>();

		int size() {
			return leftEdges.size();
		}
	}

	public static void addModuleReportEntries(ExecutionReport report, ClusterGraph execution, ClusterGraph dataset) {
		ModuleReportGenerator generator = new ModuleReportGenerator(report, execution, dataset);
		generator.addReportEntries();
	}

	final ExecutionReport report;

	final ClusterGraph execution;
	final ClusterGraph dataset;

	List<Edge<ClusterNode<?>>> mergedEdges = new ArrayList<Edge<ClusterNode<?>>>();

	final PendingEdgeQueue edgeQueue = new PendingEdgeQueue();

	public ModuleReportGenerator(ExecutionReport report, ClusterGraph execution, ClusterGraph dataset) {
		this.report = report;
		this.execution = execution;
		this.dataset = dataset;
	}

	void addReportEntries() {
		addLeftNodeEntries();
		addLeftEdgeEntries();
		addMetadataEntries();
	}

	private void addLeftNodeEntries() {
		boolean rightAdded;
		for (Node<?> left : execution.graph.getAllNodes()) {
			rightAdded = false;
			ClusterNode<?> right = getCorrespondingNode(left);
			if (right != null) {
				if (!verifyMatch(left, right))
					right = null;
			}
			if (right == null) {
				// Do I need to add it? May need to see it later.
				right = dataset.addNode(left.getHash(), left.getModule(), left.getRelativeTag(), left.getType());
				report.addEntry(right);
				// if (session.subgraphAnalysisEnabled) {
				// session.subgraphs.nodeAdded(right);
				// }
				rightAdded = true;
			}
			enqueueLeftEdges(left, right, rightAdded);
		}
	}

	private void addLeftEdgeEntries() {
		for (int i = 0; i < edgeQueue.size(); i++) {
			ClusterNode<?> rightFromNode = edgeQueue.rightFromNodes.get(i);
			Edge<?> leftEdge = edgeQueue.leftEdges.get(i);
			ClusterNode<?> rightToNode = getCorrespondingNode(leftEdge.getToNode());
			Edge<ClusterNode<?>> newRightEdge = new Edge<ClusterNode<?>>(rightFromNode, rightToNode,
					leftEdge.getEdgeType(), leftEdge.getOrdinal());

			try {
				rightFromNode.addOutgoingEdge(newRightEdge);
				rightToNode.addIncomingEdge(newRightEdge);
				report.addEntry(newRightEdge);
				mergedEdges.add(newRightEdge);
				// if (session.subgraphAnalysisEnabled)
				// session.subgraphs.edgeAdded(newRightEdge);
			} catch (IllegalArgumentException e) {
				Log.log("Error merging edges! %s", e.getMessage());
				Log.log(e);
			}
		}
	}

	private void addMetadataEntries() {
		if (!execution.graph.metadata.isSingletonExecution()) {
			Log.log("Module %s metadata is not a singleton execution (%d executions)", execution.graph.name,
					execution.graph.metadata.sequences.size());
			return;
		}
		ClusterMetadataExecution metadata = execution.graph.metadata.getSingletonExecution();
		metadata.retainMergedUIBs(mergedEdges);

		for (ClusterUIB uib : metadata.uibs)
			report.addEntry(uib);
		for (ClusterSSC ssc : metadata.sscs)
			report.addEntry(ssc);
		for (ClusterSGE sge : metadata.sges)
			report.addEntry(sge);
	}

	private ClusterNode<?> getCorrespondingNode(Node<?> left) {
		ClusterNode<?> right = dataset.graph.getNode(left.getKey());
		if ((right != null) && ((right.getHash() == left.getHash())))
			return right;

		NodeList<ClusterNode<?>> byHash = dataset.graph.getGraphData().nodesByHash.get(left.getHash());
		if (byHash != null) {
			for (int i = 0; i < byHash.size(); i++) {
				ClusterNode<?> next = byHash.get(i);
				if (left.isModuleRelativeEquivalent(next)) {
					// report a hash match?
					return next;
				}
			}
		}

		return null;
	}

	private void enqueueLeftEdges(Node<?> left, ClusterNode<?> right, boolean rightAdded) {
		OrdinalEdgeList<ClusterNode<?>> rightEdges = right.getOutgoingEdges();
		OrdinalEdgeList<?> leftEdges = left.getOutgoingEdges();
		try {
			for (Edge<?> leftEdge : leftEdges) {
				if (!rightEdges.containsModuleRelativeEquivalent(leftEdge)) {
					edgeQueue.leftEdges.add(leftEdge);
					edgeQueue.rightFromNodes.add(right);
				}
			}
		} finally {
			leftEdges.release();
			rightEdges.release();
		}
	}

	private boolean verifyMatch(Node<?> left, Node<?> right) {
		// if (isThatWonkyNode(left, right)) // ipsecproc issue (fixed now?)
		// return true;

		if (left.getHash() != right.getHash()) {
			// report hash mismatch?
			return false;
		}
		if (!left.hasCompatibleEdges(right)) {
			// report collision?
			// left.getOutgoingEdges().logEdges("\tLeft outgoing: %s");
			// right.getOutgoingEdges().logEdges("\tRight outgoing: %s");
			return false;
		}
		return true;
	}

	// private void reportAddedSubgraphs() // not now...
}
