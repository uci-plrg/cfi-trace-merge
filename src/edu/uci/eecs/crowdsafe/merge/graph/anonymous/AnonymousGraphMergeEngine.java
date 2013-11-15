package edu.uci.eecs.crowdsafe.merge.graph.anonymous;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeCandidate;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeDebugLog;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeResults;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeSession;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ContextMatchState;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashMatchedNodes;

public class AnonymousGraphMergeEngine {

	private static class DynamicHashMatchEvaluator implements ClusterHashMergeSession.MergeEvaluator {
		private int reportCount = 0;

		boolean mergeMode = false;
		boolean exactMatch;
		boolean isFailed;
		int greaterMatchPercentage;

		@Override
		public void reset() {
			exactMatch = false;
			isFailed = false;
			greaterMatchPercentage = 0;
		}

		@Override
		public boolean attemptMerge(ClusterHashMergeSession session) {
			ModuleGraphCluster<?> left = session.getLeft();
			ModuleGraphCluster<?> right = session.getRight();

			int hashOverlapPerNode = left.getGraphData().nodesByHash
					.getHashOverlapPerNode(right.getGraphData().nodesByHash);
			if ((hashOverlapPerNode > (left.getExecutableNodeCount() / 2))
					|| (hashOverlapPerNode > (right.getExecutableNodeCount() / 2))) {
				return true;
			}

			isFailed = true;
			return false;
		}

		@Override
		public int evaluateMatch(ContextMatchState state) {
			if (state.isComplete() && (state.getMatchedNodeCount() > 0))
				return 1000;

			return -1;
		}

		@Override
		public boolean acceptGraphs(ClusterHashMergeSession session) {
			if (isFailed)
				throw new IllegalStateException("Cannot evaluate a merge with a failed evaluator!");

			if (mergeMode) {
				if (session.isFailed())
					return false;

				return true;
			}

			ModuleGraphCluster<?> left = session.getLeft();
			ModuleGraphCluster<?> right = session.getRight();
			HashMatchedNodes matchedNodes = session.getMatchedNodes();

			try {
				if (session.isFailed()) {
					greaterMatchPercentage = -1;
					isFailed = true;
					exactMatch = false;
					return false;
				}

				if (matchedNodes.size() == Math.min(left.getNodeCount(), right.getNodeCount())) {
					greaterMatchPercentage = 100;
					exactMatch = true;
					return true;
				}

				// TODO: may want to tag subgraphs with an id representing the original anonymous module, to use as a
				// hint

				exactMatch = false;
				int leftMatchPercentage = Math.round((matchedNodes.size() / (float) left.getNodeCount()) * 100f);
				int rightMatchPercentage = Math.round((matchedNodes.size() / (float) right.getNodeCount()) * 100f);
				greaterMatchPercentage = Math.max(leftMatchPercentage, rightMatchPercentage);

				if (greaterMatchPercentage > 50)
					return true;

				// Log.log("Rejecting match of %d nodes for graphs of size %d (%d%%) and %d (%d%%)",
				// matchedNodes.size(),
				// left.getNodeCount(), leftMatchPercentage, right.getNodeCount(), rightMatchPercentage);
				return false;
			} finally {
				if ((left.getExecutableNodeCount() < 35) && (right.getExecutableNodeCount() < 35)) {
					if (reportCount < 50) {
						Log.log("\nEvaluate subgraphs of %d and %d nodes: %d%% | exact? %b | failed? %b",
								left.getExecutableNodeCount(), right.getExecutableNodeCount(), greaterMatchPercentage,
								exactMatch, isFailed);
						// left.logGraph();
						// right.logGraph();
						reportCount++;
					}
				} else {
					Log.log("%d%% | exact? %b | failed? %b", greaterMatchPercentage, exactMatch, isFailed);
				}

				if ((greaterMatchPercentage > 50) && (greaterMatchPercentage < 80))
					toString();
				// left.logGraph();
				// right.logGraph();
			}
		}
	}

	private static class SubgraphCluster {
		final int id = SUBGRAPH_ID_INDEX++;
		List<ModuleGraphCluster<ClusterNode<?>>> graphs = new ArrayList<ModuleGraphCluster<ClusterNode<?>>>();
		List<ClusterCompatibilityRecord> compatibilityRecords = new ArrayList<ClusterCompatibilityRecord>();

		public SubgraphCluster(ModuleGraphCluster<ClusterNode<?>> graph) {
			graphs.add(graph);
		}

		void add(ModuleGraphCluster<ClusterNode<?>> subgraph, ClusterCompatibilityRecord compatibility) {
			graphs.add(subgraph);
			compatibilityRecords.add(compatibility);
		}

		void reportCompatibility() {
			StringBuilder buffer = new StringBuilder(String.format("\tOriginal graph has %d nodes\n", graphs.get(0)
					.getExecutableNodeCount()));
			for (ClusterCompatibilityRecord compatibility : compatibilityRecords) {
				buffer.append(String.format("\tCompatibility of %d node graph: ", compatibility.subgraphSize));
				for (int i = 0; i < compatibility.comparisonSubgraphSizes.size(); i++) {
					buffer.append(String.format("(%d @ %d%%%%)", compatibility.comparisonSubgraphSizes.get(i),
							compatibility.mergeScores.get(i)));
				}
				buffer.append("\n");
			}
			Log.log(buffer.toString());
		}

		ClusterCompatibilityRecord getCompatibilityRecord(int subgraphIndex) {
			return compatibilityRecords.get(subgraphIndex - 1);
		}
	}

	private static class ClusterCompatibilityRecord {
		int subgraphSize;
		final List<Integer> comparisonSubgraphSizes = new ArrayList<Integer>();
		final List<Integer> mergeScores = new ArrayList<Integer>();

		void initialize(int subgraphSize) {
			this.subgraphSize = subgraphSize;
			comparisonSubgraphSizes.clear();
			mergeScores.clear();
		}

		void add(int subgraphSize, int mergeScore) {
			comparisonSubgraphSizes.add(subgraphSize);
			mergeScores.add(mergeScore);
		}
	}

	private static class SubgraphAdditionRecord {

	}

	private static class SizeOrder implements Comparator<SubgraphCluster> {
		@Override
		public int compare(SubgraphCluster first, SubgraphCluster second) {
			int comparison = second.graphs.size() - first.graphs.size();
			if (comparison != 0)
				return comparison;

			return (int) second.hashCode() - (int) first.hashCode();
		}
	}

	private static int SUBGRAPH_ID_INDEX = 0;

	private final AnonymousGraphAnalyzer analyzer;

	private final ClusterHashMergeDebugLog debugLog;

	public AnonymousGraphMergeEngine(GraphMergeCandidate leftData, GraphMergeCandidate rightData,
			ClusterHashMergeDebugLog debugLog) {
		analyzer = new AnonymousGraphAnalyzer(leftData, rightData);
		this.debugLog = debugLog;

		AnonymousModule.initialize();
	}

	public ClusterGraph createAnonymousGraph(List<ModuleGraphCluster<ClusterNode<?>>> anonymousGraphs)
			throws IOException {
		// TODO: this will be faster if any existing anonymous graph is used as the initial comparison set for any
		// dynamic and static graphs

		analyzer.installSubgraphs(anonymousGraphs);
		analyzer.analyzeModules();

		// List<ModuleGraphCluster<ClusterNode<?>>> largeSubgraphs = new
		// ArrayList<ModuleGraphCluster<ClusterNode<?>>>();

		DynamicHashMatchEvaluator dynamicEvaluator = new DynamicHashMatchEvaluator();

		// Log.log();
		// analyzer.reportSubgraph("Large subgraph", maximalSubgraphs.get(0));
		// maximalSubgraphs.get(0).logGraph(20);
		// Log.log();
		// analyzer.reportSubgraph("Large subgraph", maximalSubgraphs.get(1));
		// maximalSubgraphs.get(1).logGraph(20);
		// System.exit(0);

		//analyzer.localizedCompatibilityAnalysis(analyzer.maximalSubgraphs.get(0), analyzer.maximalSubgraphs.get(1));
		// analyzer.fullCompatibilityAnalysis(analyzer.maximalSubgraphs.get(0), analyzer.maximalSubgraphs.get(1));

		// ClusterHashMergeSession.evaluateTwoGraphs(analyzer.maximalSubgraphs.get(0), analyzer.maximalSubgraphs.get(1),
		// dynamicEvaluator, debugLog);

		System.exit(0);

		List<SubgraphCluster> subgraphClusters = new ArrayList<SubgraphCluster>();
		boolean match = false, fail;
		ClusterCompatibilityRecord clusterCompatibilityRecord = new ClusterCompatibilityRecord();
		for (ModuleGraphCluster<ClusterNode<?>> maximalSubgraph : analyzer.maximalSubgraphs) {
			if ((maximalSubgraph.getNodeCount() > analyzer.twiceAverage) || (maximalSubgraph.getNodeCount() < 7))
				continue;
			// Log.log("Postponing subgraph of size %d", maximalSubgraph.getNodeCount());
			// largeSubgraphs.add(maximalSubgraph);
			// continue;
			// }

			for (SubgraphCluster subgraphCluster : subgraphClusters) {
				match = false;
				fail = false;
				clusterCompatibilityRecord.initialize(maximalSubgraph.getExecutableNodeCount());
				int score = 0;
				for (int i = 0; i < subgraphCluster.graphs.size(); i++) {
					ClusterHashMergeSession.evaluateTwoGraphs(maximalSubgraph, subgraphCluster.graphs.get(i),
							dynamicEvaluator, debugLog);

					if (dynamicEvaluator.exactMatch) {
						match = true; // skip this graph because an identical graph is already in this cluster
						break;
					} else if (dynamicEvaluator.isFailed) {
						fail = true;
						break;
					}

					if ((dynamicEvaluator.greaterMatchPercentage > 50)
							&& (dynamicEvaluator.greaterMatchPercentage < 80))
						ClusterHashMergeSession.evaluateTwoGraphs(maximalSubgraph, subgraphCluster.graphs.get(i),
								dynamicEvaluator, debugLog);
					// maximalSubgraph.logGraph();
					// subgraphCluster.graphs.get(i).logGraph()
					/**
					 * <pre> else if (dynamicEvaluator.greaterMatchPercentage > 80) {
						ClusterGraph mergedGraph = ClusterHashMergeSession.mergeTwoGraphs(maximalSubgraph,
								subgraphCluster.graphs.get(i), ClusterHashMergeResults.Empty.INSTANCE,
								dynamicEvaluator, debugLog);
						subgraphCluster.graphs.set(i, mergedGraph.graph);
						match = true;
						break;
					}
					 */
					score += dynamicEvaluator.greaterMatchPercentage;
					clusterCompatibilityRecord.add(subgraphCluster.graphs.get(i).getExecutableNodeCount(),
							dynamicEvaluator.greaterMatchPercentage);
				}

				if (fail)
					continue;

				if (match)
					break;

				int clusterPercentage = (score / subgraphCluster.graphs.size());
				if (clusterPercentage > 50) {
					// Log.log("\nAdding subgraph of %d nodes to cluster %d", maximalSubgraph.getExecutableNodeCount(),
					// subgraphCluster.id);
					// maximalSubgraph.logGraph();

					subgraphCluster.add(maximalSubgraph, clusterCompatibilityRecord);
					clusterCompatibilityRecord = new ClusterCompatibilityRecord();
					match = true;
					break;
				}
			}

			if (!match) {
				SubgraphCluster cluster = new SubgraphCluster(maximalSubgraph);
				// Log.log("\nCreated cluster %d:", cluster.id);
				// maximalSubgraph.logGraph();

				subgraphClusters.add(cluster);
			}
		}

		dynamicEvaluator.mergeMode = true;

		// TODO: evaluate each cluster -> merge all its graphs together if valid

		Map<ClusterNode<?>, ClusterNode<?>> copyMap = new HashMap<ClusterNode<?>, ClusterNode<?>>();
		ClusterGraph anonymousGraph = new ClusterGraph(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER);
		Log.log("\nReduced anonymous graph to %d subgraph clusters:", subgraphClusters.size());
		Collections.sort(subgraphClusters, new SizeOrder());
		SubgraphCluster spillCluster;
		for (int s = 0; s < subgraphClusters.size(); s++) {
			SubgraphCluster subgraphCluster = subgraphClusters.get(s);
			Log.log("\nCluster of %d subgraphs:", subgraphCluster.graphs.size());
			subgraphCluster.reportCompatibility();

			spillCluster = null;

			ModuleGraphCluster<ClusterNode<?>> mergedClusterGraph;
			if (subgraphCluster.graphs.size() == 1) {
				mergedClusterGraph = subgraphCluster.graphs.get(0);
			} else {
				ClusterGraph mergedPairGraph = ClusterHashMergeSession.mergeTwoGraphs(subgraphCluster.graphs.get(0),
						subgraphCluster.graphs.get(1), ClusterHashMergeResults.Empty.INSTANCE, dynamicEvaluator,
						debugLog);
				if (mergedPairGraph == null) {
					Log.log("Failed to merge subgraph #1. Spilling it.");

					mergedClusterGraph = subgraphCluster.graphs.get(0);
					spillCluster = new SubgraphCluster(subgraphCluster.graphs.get(1));
					subgraphClusters.add(spillCluster);
				} else {
					mergedClusterGraph = mergedPairGraph.graph;
				}

				for (int i = 2; i < subgraphCluster.graphs.size(); i++) {
					mergedPairGraph = ClusterHashMergeSession.mergeTwoGraphs(mergedClusterGraph,
							subgraphCluster.graphs.get(i), ClusterHashMergeResults.Empty.INSTANCE, dynamicEvaluator,
							debugLog);
					if (mergedPairGraph == null) {
						Log.log("Failed to merge subgraph #%d. Spilling it.", i);
						Log.log("Current cluster graph:");
						mergedClusterGraph.logGraph();
						Log.log("\nFailed member:");
						subgraphCluster.graphs.get(i).logGraph();

						if (spillCluster == null) {
							spillCluster = new SubgraphCluster(subgraphCluster.graphs.get(i));
							subgraphClusters.add(spillCluster);
						} else {
							spillCluster.add(subgraphCluster.graphs.get(i), subgraphCluster.getCompatibilityRecord(i));
						}
					} else {
						mergedClusterGraph = mergedPairGraph.graph;
					}
				}
			}

			// analyzer.reportSubgraph("Merged cluster subgraph", mergedClusterGraph);

			for (ClusterNode<?> node : mergedClusterGraph.getAllNodes()) {
				ClusterNode<?> copy = anonymousGraph.addNode(node.getHash(), SoftwareModule.ANONYMOUS_MODULE,
						node.getRelativeTag(), node.getType());
				copyMap.put(node, copy);
			}

			for (ClusterNode<?> node : mergedClusterGraph.getAllNodes()) {
				OrdinalEdgeList<?> edges = node.getOutgoingEdges();
				try {
					for (Edge<? extends Node<?>> edge : edges) {
						ClusterNode<?> fromNode = copyMap.get(edge.getFromNode());
						ClusterNode<?> toNode = copyMap.get(edge.getToNode());
						Edge<ClusterNode<?>> mergedEdge = new Edge<ClusterNode<?>>(fromNode, toNode,
								edge.getEdgeType(), edge.getOrdinal());
						fromNode.addOutgoingEdge(mergedEdge);
						toNode.addIncomingEdge(mergedEdge);
					}
				} finally {
					edges.release();
				}
			}

			copyMap.clear();
		}

		return anonymousGraph;
	}
}
