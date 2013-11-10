package edu.uci.eecs.crowdsafe.merge.graph.anonymous;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.MutableInteger;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeCandidate;
import edu.uci.eecs.crowdsafe.merge.graph.hash.MaximalSubgraphs;

class AnonymousGraphAnalyzer {

	private static class SizeOrder implements Comparator<ModuleGraphCluster<ClusterNode<?>>> {
		@Override
		public int compare(ModuleGraphCluster<ClusterNode<?>> first, ModuleGraphCluster<ClusterNode<?>> second) {
			int comparison = second.getExecutableNodeCount() - first.getExecutableNodeCount();
			if (comparison != 0)
				return comparison;

			return (int) second.hashCode() - (int) first.hashCode();
		}
	}

	private static class ClusterGraphCache {
		final GraphMergeCandidate leftData;
		final GraphMergeCandidate rightData;

		final Map<AutonomousSoftwareDistribution, ModuleGraphCluster<?>> leftGraphs = new HashMap<AutonomousSoftwareDistribution, ModuleGraphCluster<?>>();
		final Map<AutonomousSoftwareDistribution, ModuleGraphCluster<?>> rightGraphs = new HashMap<AutonomousSoftwareDistribution, ModuleGraphCluster<?>>();

		public ClusterGraphCache(GraphMergeCandidate leftData, GraphMergeCandidate rightData) {
			this.leftData = leftData;
			this.rightData = rightData;
		}

		ModuleGraphCluster<?> getLeftGraph(AutonomousSoftwareDistribution cluster) throws IOException {
			ModuleGraphCluster<?> graph = leftGraphs.get(cluster);
			if (graph == null) {
				graph = leftData.getClusterGraph(cluster);
				leftGraphs.put(cluster, graph);
			}
			return graph;
		}

		ModuleGraphCluster<?> getRightGraph(AutonomousSoftwareDistribution cluster) throws IOException {
			ModuleGraphCluster<?> graph = rightGraphs.get(cluster);
			if (graph == null) {
				graph = rightData.getClusterGraph(cluster);
				rightGraphs.put(cluster, graph);
			}
			return graph;
		}
	}

	int totalSize = 0;
	int minSize = Integer.MAX_VALUE;
	int maxSize = 0;
	int averageSize;
	int twiceAverage;
	int thriceAverage;
	int halfAverage;
	int subgraphsOverTwiceAverage;
	int subgraphsOverThriceAverage;
	int subgraphsUnderHalfAverage;

	final ClusterGraphCache graphCache;
	final AnonymousSubgraphFlowAnalysis flowAnalsis = new AnonymousSubgraphFlowAnalysis();

	List<ModuleGraphCluster<ClusterNode<?>>> maximalSubgraphs = new ArrayList<ModuleGraphCluster<ClusterNode<?>>>();

	public AnonymousGraphAnalyzer(GraphMergeCandidate leftData, GraphMergeCandidate rightData) {
		graphCache = new ClusterGraphCache(leftData, rightData);
	}

	List<ModuleGraphCluster<ClusterNode<?>>> getMaximalSubgraphs(List<ModuleGraphCluster<ClusterNode<?>>> dynamicGraphs) {
		for (ModuleGraphCluster<ClusterNode<?>> dynamicGraph : dynamicGraphs) {
			for (ModuleGraphCluster<ClusterNode<?>> maximalSubgraph : MaximalSubgraphs
					.getMaximalSubgraphs(dynamicGraph)) {
				int size = maximalSubgraph.getNodeCount();
				totalSize += size;
				if (size < minSize)
					minSize = size;
				if (size > maxSize)
					maxSize = size;

				maximalSubgraphs.add(maximalSubgraph);
			}
		}

		Collections.sort(maximalSubgraphs, new SizeOrder());

		averageSize = totalSize / maximalSubgraphs.size();
		twiceAverage = averageSize * 2;
		thriceAverage = averageSize * 3;
		halfAverage = averageSize / 2;
		subgraphsOverTwiceAverage = 0;
		subgraphsOverThriceAverage = 0;
		subgraphsUnderHalfAverage = 0;
		for (ModuleGraphCluster<ClusterNode<?>> maximalSubgraph : maximalSubgraphs) {
			int size = maximalSubgraph.getNodeCount();
			if (size > twiceAverage) {
				subgraphsOverTwiceAverage++;
				if (size > thriceAverage)
					subgraphsOverThriceAverage++;
			} else if (size < halfAverage) {
				subgraphsUnderHalfAverage++;
			}
		}

		Log.log("Found %d maximal subgraphs.", maximalSubgraphs.size());
		Log.log("Min size %d, max size %d, average size %d", minSize, maxSize, averageSize);
		Log.log("Over twice average %d, over thrice average %d, under half average %d", subgraphsOverTwiceAverage,
				subgraphsOverThriceAverage, subgraphsUnderHalfAverage);

		return maximalSubgraphs;
	}

	void analyzeSubgraphs() throws IOException {
		for (ModuleGraphCluster<ClusterNode<?>> subgraph : maximalSubgraphs) {
			reportSubgraph("Subgraph", subgraph);

			flowAnalsis.clear();
			flowAnalsis.analyzeFlow(subgraph);
		}
	}

	void reportSubgraph(String name, ModuleGraphCluster<ClusterNode<?>> subgraph) throws IOException {
		Log.log("\n === %s of %d nodes with %d total hashes", name, subgraph.getExecutableNodeCount(),
				subgraph.getGraphData().nodesByHash.keySet().size());

		String clusterName;
		AutonomousSoftwareDistribution cluster;
		if (subgraph.getEntryHashes().isEmpty()) {
			Log.log("     Error: entry point missing!");
		} else {
			for (Long entryHash : subgraph.getEntryHashes()) {
				ClusterNode<?> entryPoint = subgraph.getEntryPoint(entryHash);

				OrdinalEdgeList<?> edges = entryPoint.getOutgoingEdges();
				try {
					int leftCallSiteCount = 0, rightCallSiteCount = 0;
					cluster = ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousEntryHash(entryHash);
					if (cluster == null) {
						clusterName = "?";
					} else {
						clusterName = cluster.name;
						leftCallSiteCount = getExitEdgeCount(entryHash, graphCache.getLeftGraph(cluster));
						rightCallSiteCount = getExitEdgeCount(entryHash, graphCache.getRightGraph(cluster));
					}
					Log.log("     Entry point 0x%x (%s) reaches %d nodes from %d left call sites and %d right call sites",
							entryHash, clusterName, edges.size(), leftCallSiteCount, rightCallSiteCount);
				} finally {
					edges.release();
				}
			}
		}

		Map<EdgeType, MutableInteger> edgeCountsByType = new EnumMap<EdgeType, MutableInteger>(EdgeType.class);
		Map<EdgeType, MutableInteger> edgeOrdinalCountsByType = new EnumMap<EdgeType, MutableInteger>(EdgeType.class);
		for (EdgeType type : EdgeType.values()) {
			edgeCountsByType.put(type, new MutableInteger(0));
			edgeOrdinalCountsByType.put(type, new MutableInteger(0));
		}

		int maxIndirectEdgeCountPerOrdinal = 0;
		int singletonIndirectOrdinalCount = 0;
		int pairIndirectOrdinalCount = 0;
		int totalOrdinals = 0;
		int totalEdges = 0;
		int ordinalCount;
		EdgeType edgeType;
		for (ClusterNode<?> node : subgraph.getAllNodes()) {
			if (node.getType() == MetaNodeType.CLUSTER_EXIT) {
				OrdinalEdgeList<?> edges = node.getIncomingEdges();
				try {
					int leftTargetCount = 0, rightTargetCount = 0;
					cluster = ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousExitHash(
							node.getHash());
					if (cluster == null) {
						clusterName = "?";
					} else {
						clusterName = cluster.name;
						leftTargetCount = getEntryEdgeCount(node.getHash(), graphCache.getLeftGraph(cluster));
						rightTargetCount = getEntryEdgeCount(node.getHash(), graphCache.getRightGraph(cluster));
					}
					Log.log("     Callout 0x%x (%s) from %d nodes to %d left targets and %d right targets",
							node.getHash(), clusterName, edges.size(), leftTargetCount, rightTargetCount);
				} finally {
					edges.release();
				}
			} else if (node.getType() != MetaNodeType.CLUSTER_ENTRY) {
				ordinalCount = node.getOutgoingOrdinalCount();
				totalOrdinals += ordinalCount;
				for (int ordinal = 0; ordinal < ordinalCount; ordinal++) {
					OrdinalEdgeList<ClusterNode<?>> edges = node.getOutgoingEdges(ordinal);
					try {
						if (edges.isEmpty())
							continue;

						edgeType = edges.get(0).getEdgeType();
						edgeCountsByType.get(edgeType).add(edges.size());
						edgeOrdinalCountsByType.get(edgeType).increment();
						totalEdges += edges.size();

						if (edgeType == EdgeType.INDIRECT) {
							if (edges.size() > maxIndirectEdgeCountPerOrdinal)
								maxIndirectEdgeCountPerOrdinal = edges.size();
							if (edges.size() == 1)
								singletonIndirectOrdinalCount++;
							else if (edges.size() == 2)
								pairIndirectOrdinalCount++;
						}
					} finally {
						edges.release();
					}
				}
				if (node.getCallContinuation() != null) {
					totalEdges++;
					edgeCountsByType.get(EdgeType.CALL_CONTINUATION).increment();
					edgeOrdinalCountsByType.get(EdgeType.CALL_CONTINUATION).increment();
				}
			}
		}

		Log.log("     Total edges: %d; Total ordinals: %d", totalEdges, totalOrdinals);

		int instances;
		int ordinals;
		int instancePercentage;
		int ordinalPercentage;
		Set<EdgeType> reportedEdgeTypes = EnumSet.of(EdgeType.DIRECT, EdgeType.CALL_CONTINUATION, EdgeType.INDIRECT,
				EdgeType.UNEXPECTED_RETURN);
		for (EdgeType type : reportedEdgeTypes) {
			instances = edgeCountsByType.get(type).getVal();
			ordinals = edgeOrdinalCountsByType.get(type).getVal();
			instancePercentage = Math.round((instances / (float) totalEdges) * 100f);
			ordinalPercentage = Math.round((ordinals / (float) totalOrdinals) * 100f);
			Log.log("     Edge type %s: %d total edges (%d%%), %d ordinals (%d%%)", type.name(), instances,
					instancePercentage, ordinals, ordinalPercentage);
		}

		int indirectTotal = edgeCountsByType.get(EdgeType.INDIRECT).getVal();
		float averageIndirectEdgeCount = (indirectTotal / (float) edgeOrdinalCountsByType.get(EdgeType.INDIRECT)
				.getVal());
		int singletonIndirectPercentage = Math.round((singletonIndirectOrdinalCount / (float) indirectTotal) * 100f);
		int pairIndirectPercentage = Math.round((pairIndirectOrdinalCount / (float) indirectTotal) * 100f);
		Log.log("     Average indirect edge fanout: %.3f; Max: %d; singletons: %d (%d%%); pairs: %d (%d%%)",
				averageIndirectEdgeCount, maxIndirectEdgeCountPerOrdinal, singletonIndirectOrdinalCount,
				singletonIndirectPercentage, pairIndirectOrdinalCount, pairIndirectPercentage);
	}

	private int getEntryEdgeCount(long entryHash, ModuleGraphCluster<?> targetGraph) {
		if (targetGraph == null)
			return 0;

		Node<?> targetEntry = targetGraph.getEntryPoint(entryHash);
		if (targetEntry != null) {
			OrdinalEdgeList<?> entryEdges = targetEntry.getOutgoingEdges();
			try {
				return entryEdges.size();
			} finally {
				entryEdges.release();
			}
		}
		return 0;
	}

	private int getExitEdgeCount(long exitHash, ModuleGraphCluster<?> targetGraph) {
		if (targetGraph == null)
			return 0;

		Node<?> targetExit = targetGraph.getNode(new ClusterBoundaryNode.Key(exitHash, MetaNodeType.CLUSTER_EXIT));
		if (targetExit != null) {
			OrdinalEdgeList<?> entryEdges = targetExit.getIncomingEdges();
			try {
				return entryEdges.size();
			} finally {
				entryEdges.release();
			}
		}
		return 0;
	}

	void localizedCompatibilityAnalysis(ModuleGraphCluster<ClusterNode<?>> left,
			ModuleGraphCluster<ClusterNode<?>> right) {
		AnonymousSubgraphCompatibilityAnalysis analysis = new AnonymousSubgraphCompatibilityAnalysis(left, right);
		analysis.localCompatibilityPerNode(20);
		analysis.fullCompatibilityPerEntry();
	}

	public static void main(String[] args) {
		long foo = (0L ^ (1L << 0x3fL));
		System.out.println(String.format("foo: 0x%x", foo));
	}
}
