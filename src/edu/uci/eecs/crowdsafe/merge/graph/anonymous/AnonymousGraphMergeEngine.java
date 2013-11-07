package edu.uci.eecs.crowdsafe.merge.graph.anonymous;

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
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeDebugLog;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeResults;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeSession;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ContextMatchState;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashMatchedNodes;

public class AnonymousGraphMergeEngine {

	private static class DynamicHashMatchEvaluator implements ClusterHashMergeSession.MergeEvaluator {
		private int reportCount = 0;

		boolean mergeMode;
		boolean exactMatch;
		boolean isFailed;
		int greaterMatchPercentage;

		@Override
		public void reset() {
			mergeMode = false;
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

			if (mergeMode)
				return true;

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
				if ((left.getExecutableNodeCount() < 20) && (right.getExecutableNodeCount() < 20)) {
					Log.log("\nEvaluate subgraphs of %d and %d nodes: %d%% | exact? %b | failed? %b",
							left.getExecutableNodeCount(), right.getExecutableNodeCount(), greaterMatchPercentage,
							exactMatch, isFailed);
					left.logGraph();
					right.logGraph();
					reportCount++;
					if (reportCount > 50)
						System.exit(0);
				}
			}
		}
	}

	private static class SubgraphCluster {
		List<ModuleGraphCluster<ClusterNode<?>>> graphs = new ArrayList<ModuleGraphCluster<ClusterNode<?>>>();

		public SubgraphCluster(ModuleGraphCluster<ClusterNode<?>> graph) {
			graphs.add(graph);
		}
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

	private final AnonymousGraphAnalyzer analyzer = new AnonymousGraphAnalyzer();;

	private final ClusterHashMergeDebugLog debugLog;

	public AnonymousGraphMergeEngine(ClusterHashMergeDebugLog debugLog) {
		this.debugLog = debugLog;
	}

	public ClusterGraph createAnonymousGraph(List<ModuleGraphCluster<ClusterNode<?>>> dynamicGraphs) {
		// TODO: this will be faster if any existing anonymous graph is used as the initial comparison set for any
		// dynamic and static graphs

		List<ModuleGraphCluster<ClusterNode<?>>> maximalSubgraphs = analyzer.getMaximalSubgraphs(dynamicGraphs);
		analyzer.analyzeSubgraphs();

		// List<ModuleGraphCluster<ClusterNode<?>>> largeSubgraphs = new
		// ArrayList<ModuleGraphCluster<ClusterNode<?>>>();

		DynamicHashMatchEvaluator dynamicEvaluator = new DynamicHashMatchEvaluator();
		List<SubgraphCluster> subgraphClusters = new ArrayList<SubgraphCluster>();
		boolean match, fail;
		for (ModuleGraphCluster<ClusterNode<?>> maximalSubgraph : maximalSubgraphs) {
			// if (maximalSubgraph.getNodeCount() > twiceAverage) {
			// Log.log("Postponing subgraph of size %d", maximalSubgraph.getNodeCount());
			// largeSubgraphs.add(maximalSubgraph);
			// continue;
			// }

			match = false;
			fail = false;
			for (SubgraphCluster subgraphCluster : subgraphClusters) {
				int score = 0;
				for (int i = 0; i < subgraphCluster.graphs.size(); i++) {
					ClusterHashMergeSession.evaluateTwoGraphs(maximalSubgraph, subgraphCluster.graphs.get(i),
							dynamicEvaluator, debugLog);

					if (dynamicEvaluator.exactMatch) {
						match = true; // skip this graph because it's already in a cluster
						break;
					} else if (dynamicEvaluator.isFailed) {
						fail = true;
						break;
					} else if (dynamicEvaluator.greaterMatchPercentage > 80) {
						ClusterGraph mergedGraph = ClusterHashMergeSession.mergeTwoGraphs(maximalSubgraph,
								subgraphCluster.graphs.get(i), ClusterHashMergeResults.Empty.INSTANCE,
								dynamicEvaluator, debugLog);
						subgraphCluster.graphs.set(i, mergedGraph.graph);
						match = true;
						break;
					}
					score += dynamicEvaluator.greaterMatchPercentage;
				}

				if (fail)
					continue;

				if (match)
					break;

				int clusterPercentage = (score / subgraphCluster.graphs.size());
				if (clusterPercentage > 50) {
					subgraphCluster.graphs.add(maximalSubgraph);
					match = true;
					break;
				}
			}

			if (!match) {
				subgraphClusters.add(new SubgraphCluster(maximalSubgraph));
			}
		}

		dynamicEvaluator.mergeMode = true;

		// TODO: evaluate each cluster -> merge all its graphs together if valid

		Map<ClusterNode<?>, ClusterNode<?>> copyMap = new HashMap<ClusterNode<?>, ClusterNode<?>>();
		ClusterGraph anonymousGraph = new ClusterGraph(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER);
		Log.log("\nReduced anonymous graph to %d subgraph clusters:", subgraphClusters.size());
		Collections.sort(subgraphClusters, new SizeOrder());
		for (SubgraphCluster subgraphCluster : subgraphClusters) {
			Log.log("\nCluster of %d subgraphs:", subgraphCluster.graphs.size());

			ModuleGraphCluster<ClusterNode<?>> graph;
			if (subgraphCluster.graphs.size() == 1) {
				graph = subgraphCluster.graphs.get(0);
			} else {
				ClusterGraph mergedGraph = ClusterHashMergeSession.mergeTwoGraphs(subgraphCluster.graphs.get(0),
						subgraphCluster.graphs.get(1), ClusterHashMergeResults.Empty.INSTANCE, dynamicEvaluator,
						debugLog);
				if (mergedGraph == null) {
					Log.log("Error! Failed to merge two subgraphs that were assigned to a cluster. Skipping the second.");
					graph = subgraphCluster.graphs.get(0);
				} else {
					graph = mergedGraph.graph;
				}

				for (int i = 2; i < subgraphCluster.graphs.size(); i++) {
					mergedGraph = ClusterHashMergeSession.mergeTwoGraphs(graph, subgraphCluster.graphs.get(i),
							ClusterHashMergeResults.Empty.INSTANCE, dynamicEvaluator, debugLog);
					if (mergedGraph == null) {
						Log.log("Error! Failed to merge two subgraphs that were assigned to a cluster. Skipping the second.");
					} else {
						graph = mergedGraph.graph;
					}
				}
			}

			for (ClusterNode<?> node : graph.getAllNodes()) {
				ClusterNode<?> copy = anonymousGraph.addNode(node.getHash(), SoftwareModule.ANONYMOUS_MODULE,
						node.getRelativeTag(), node.getType());
				copyMap.put(node, copy);
			}

			for (ClusterNode<?> node : graph.getAllNodes()) {
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
