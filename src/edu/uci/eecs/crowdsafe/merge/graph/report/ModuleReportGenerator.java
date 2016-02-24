package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.RiskySystemCall;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ApplicationGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleSGE;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleSSC;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleUIB;

public class ModuleReportGenerator {

	class PendingEdgeQueue {
		final List<ModuleNode<?>> rightFromNodes = new ArrayList<ModuleNode<?>>();
		final List<Edge<ModuleNode<?>>> leftEdges = new ArrayList<Edge<ModuleNode<?>>>();

		int size() {
			return leftEdges.size();
		}
	}

	public static void addModuleReportEntries(ExecutionReport report, ApplicationGraph execution, ApplicationGraph dataset) {
		ModuleReportGenerator generator = new ModuleReportGenerator(report, execution, dataset);
		generator.addReportEntries();
	}

	final ExecutionReport report;

	final ApplicationGraph execution;
	final ApplicationGraph dataset;

	// after addMetadataEntries, contains no SUIB
	List<Edge<ModuleNode<?>>> mergedIndirects = new ArrayList<Edge<ModuleNode<?>>>();

	final PendingEdgeQueue edgeQueue = new PendingEdgeQueue();

	public ModuleReportGenerator(ExecutionReport report, ApplicationGraph execution, ApplicationGraph dataset) {

		report.setCurrentModule(execution.graph.module.name);

		this.report = report;
		this.execution = execution;
		this.dataset = dataset;

		// Log.log("Module %s has %d indirects with %d distinct targets", execution.graph.cluster.name,
		// eventFrequencies.getTotalIndirectCount(), eventFrequencies.getUniqueIndirectTargetCount());
	}

	void addReportEntries() {
		if (dataset == null) {
			report.addEntry(new NewModuleReport(execution.graph.module));
		} else {
			addLeftNodeEntries();
			addLeftEdgeEntries();
			addMetadataEntries();
		}
	}

	private void addLeftNodeEntries() {
		boolean rightAdded;
		for (ModuleNode<?> left : new ArrayList<ModuleNode<?>>(execution.graph.getAllNodes())) {
			rightAdded = false;
			ModuleNode<?> right = getDatasetNode(left);
			if (right != null) {
				if (!verifyMatch(left, right))
					right = null;
			}
			if (right == null) {
				// Do I need to add it? May need to see it later.
				if (dataset == null)
					right = execution.addNode(left.getHash(), left.getModule(), left.getRelativeTag(), left.getType());
				else
					right = dataset.addNode(left.getHash(), left.getModule(), left.getRelativeTag(), left.getType());
				switch (right.getType()) {
					case RETURN:
						OrdinalEdgeList<?> outgoing = right.getOutgoingEdges();
						try {
							if (outgoing.size() > 0)
								report.addEntry(new NewNodeReport(NewNodeReport.Type.ABNORMAL_RETURN, right));
						} finally {
							outgoing.release();
						}
						break;
					case SINGLETON:
						if (right.isJITSingleton())
							report.addEntry(new NewNodeReport(NewNodeReport.Type.JIT_SINGLETON, right));
						break;
				}
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
			ModuleNode<?> rightFromNode = edgeQueue.rightFromNodes.get(i);
			Edge<ModuleNode<?>> leftEdge = edgeQueue.leftEdges.get(i);
			ModuleNode<?> rightToNode = getDatasetNode(leftEdge.getToNode());
			if (rightToNode == null)
				rightToNode = leftEdge.getToNode();
			Edge<ModuleNode<?>> newRightEdge = new Edge<ModuleNode<?>>(rightFromNode, rightToNode,
					leftEdge.getEdgeType(), leftEdge.getOrdinal());

			try {
				rightFromNode.addOutgoingEdge(newRightEdge);
				rightToNode.addIncomingEdge(newRightEdge);
				if (ExecutionReport.isReportedEdgeType(newRightEdge.getEdgeType()))
					report.addEntry(new NewEdgeReport(newRightEdge));
				if (newRightEdge.getEdgeType() == EdgeType.INDIRECT)
					mergedIndirects.add(newRightEdge);
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
		ModuleMetadataExecution metadata = execution.graph.metadata.getSingletonExecution();
		metadata.retainMergedUIBs(mergedIndirects, true);

		for (ModuleUIB uib : metadata.uibs) {
			if (uib.isAdmitted) {
				report.addEntry(new IndirectEdgeReport(uib));
			} else {
				report.addEntry(new SuspiciousIndirectEdgeReport(uib));
				// report.filterEdgeReport(uib.edge);
			}
		}
		for (ModuleSSC ssc : metadata.sscs) {
			if (RiskySystemCall.sysnumMap.containsKey(ssc.sysnum))
				report.addEntry(new SuspiciousSyscallReport(ssc));
		}
		for (ModuleSGE sge : metadata.sges)
			report.addEntry(new SuspiciousGencodeReport(sge));
	}

	private ModuleNode<?> getDatasetNode(Node<?> left) {
		if (dataset == null)
			return null;

		ModuleNode<?> right = dataset.graph.getNode(left.getKey());
		if (right != null && right.getHash() == left.getHash())
			return right;

		NodeList<ModuleNode<?>> byHash = dataset.graph.getGraphData().nodesByHash.get(left.getHash());
		if (byHash != null) {
			for (int i = 0; i < byHash.size(); i++) {
				ModuleNode<?> next = byHash.get(i);
				if (left.isModuleRelativeEquivalent(next)) {
					// report a hash match?
					return next;
				}
			}
		}

		return null;
	}

	private void enqueueLeftEdges(ModuleNode<?> left, ModuleNode<?> right, boolean rightAdded) {
		OrdinalEdgeList<ModuleNode<?>> rightEdges = right.getOutgoingEdges();
		OrdinalEdgeList<ModuleNode<?>> leftEdges = left.getOutgoingEdges();
		try {
			for (Edge<ModuleNode<?>> leftEdge : leftEdges) {
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
