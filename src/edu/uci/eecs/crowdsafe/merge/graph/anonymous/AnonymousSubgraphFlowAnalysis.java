package edu.uci.eecs.crowdsafe.merge.graph.anonymous;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.log.Log;

public class AnonymousSubgraphFlowAnalysis {

	private static class FlowRecord {
		final int id = ID_INDEX++;
		final ClusterBoundaryNode entryPoint;
		final ClusterNode<?> entryNode;
		final Set<ClusterNode<?>> coverage = new HashSet<ClusterNode<?>>();
		final Set<ClusterNode<?>> exits = new HashSet<ClusterNode<?>>();
		int backEdgeCount = 0;

		public FlowRecord(ClusterBoundaryNode entryPoint, ClusterNode<?> entryNode) {
			this.entryPoint = entryPoint;
			this.entryNode = entryNode;
		}
	}

	private static int ID_INDEX = 0;

	private FlowRecord flowRecord;
	private final Map<ClusterNode<?>, FlowRecord> flowPerEntryNode = new LinkedHashMap<ClusterNode<?>, FlowRecord>();

	private final LinkedList<ClusterNode<?>> queue = new LinkedList<ClusterNode<?>>();

	void clear() {
		ID_INDEX = 0;
		flowRecord = null;
		flowPerEntryNode.clear();
	}

	void analyzeFlow(ModuleGraphCluster<ClusterNode<?>> graph) {
		int returnOnlyCount = 0;
		int singletonExitCount = 0;
		int totalBackEdgeCount = 0;
		int maxBackEdgeCount = 0;
		for (long entryHash : graph.getEntryHashes()) {
			ClusterBoundaryNode entryPoint = (ClusterBoundaryNode) graph.getEntryPoint(entryHash);

			OrdinalEdgeList<ClusterNode<?>> edges = entryPoint.getOutgoingEdges();
			try {
				for (Edge<ClusterNode<?>> edge : edges) {
					ClusterNode<?> entryNode = edge.getToNode();
					flowRecord = new FlowRecord(entryPoint, entryNode);
					flowPerEntryNode.put(entryNode, flowRecord);

					queue.clear();
					queue.push(entryNode);
					flowRecord.coverage.add(entryNode);
					followFlow();

					if (flowRecord.exits.isEmpty())
						returnOnlyCount++;
					else if (flowRecord.exits.size() == 1)
						singletonExitCount++;

					totalBackEdgeCount += flowRecord.backEdgeCount;
					if (flowRecord.backEdgeCount > maxBackEdgeCount)
						maxBackEdgeCount = flowRecord.backEdgeCount;
				}
			} finally {
				edges.release();
			}
		}

		float averageBackEdgeCount = (totalBackEdgeCount / (float) flowPerEntryNode.size());
		Log.log("\tReturn only: %d; singleton exit: %d", returnOnlyCount, singletonExitCount);
		Log.log("\tAverage back edge count: %.3f; max: %d", averageBackEdgeCount, maxBackEdgeCount);

		for (FlowRecord flowRecord : flowPerEntryNode.values()) {
			if (flowRecord.exits.size() > 1) {
				int coveragePercent = Math
						.round((flowRecord.coverage.size() / (float) graph.getExecutableNodeCount()) * 100f);
				if (coveragePercent > 100)
					toString();
				Log.log("\tEntry #%d to %d exits covering %d%% of the subgraph", flowRecord.id,
						flowRecord.exits.size(), coveragePercent);
			}
		}
	}

	private void followFlow() {
		while (!queue.isEmpty()) {
			ClusterNode<?> node = queue.pop();

			OrdinalEdgeList<ClusterNode<?>> edges = node.getOutgoingEdges();
			try {
				for (Edge<ClusterNode<?>> edge : edges) {
					ClusterNode<?> toNode = edge.getToNode();

					if (toNode.getType() == MetaNodeType.CLUSTER_EXIT) {
						flowRecord.exits.add(node);
						continue;
					}

					if (flowRecord.coverage.contains(toNode)) {
						flowRecord.backEdgeCount++;
					} else {
						flowRecord.coverage.add(toNode);
						queue.push(toNode);
					}
				}
			} finally {
				edges.release();
			}
		}
	}
}
