package edu.uci.plrg.cfi.x86.merge.graph.hash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.common.util.MutableInteger;
import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModule;
import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.EdgeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.MetaNodeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.ModuleGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.Node;
import edu.uci.plrg.cfi.x86.graph.data.graph.OrdinalEdgeList;
import edu.uci.plrg.cfi.x86.graph.data.results.Graph;
import edu.uci.plrg.cfi.x86.graph.util.EdgeCounter;
import edu.uci.plrg.cfi.x86.merge.graph.GraphMergeStrategy;
import edu.uci.plrg.cfi.x86.merge.graph.results.HashMerge;
import edu.uci.plrg.cfi.x86.merge.util.AnalysisUtil;

public class HashMergeAnalysis implements HashMergeResults {

	private static class Builder {
		final HashMerge.HashMergeResults.Builder results = HashMerge.HashMergeResults.newBuilder();
		final HashMerge.ModuleHashMerge.Builder merge = HashMerge.ModuleHashMerge.newBuilder();
		final HashMerge.UnmatchedNodeSummary.Builder unmatchedNodeSummary = HashMerge.UnmatchedNodeSummary.newBuilder();
		final HashMerge.TraceCompilationProfile.Builder traceProfile = HashMerge.TraceCompilationProfile.newBuilder();
		final HashMerge.HashMergeSummary.Builder summary = HashMerge.HashMergeSummary.newBuilder();
	}

	private class ClusterResults {
		private HashMergeSession session;

		private int hashUnionSize = 0;
		private int hashIntersectionSize = 0;
		private int hashIntersectionBlockCount = 0;
		private int hashIntersectionLeftBlockCount = 0;
		private int hashIntersectionRightBlockCount = 0;
		private int mergedGraphNodeCount = 0;

		private final Set<Node.Key> HACK_moduleRelativeTagMisses = new HashSet<Node.Key>();
		private final Map<Set<Node.Key>, Set<Node.Key>> HACK_missedSubgraphs = new IdentityHashMap<Set<Node.Key>, Set<Node.Key>>();
		private int averageMissedSubgraphSize = 0;

		ClusterResults(HashMergeSession session) {
			this.session = session;

			builder.merge.clear().setDistributionName(session.left.module.module.name);
		}

		void mergeCompleted() {
			computeResults();
			reportUnmatchedNodes();
			computeMissedSubgraphs();
			outputMergedGraphInfo();

			builder.results.addMerge(builder.merge.build());
		}

		private void computeResults() {
			Set<Long> hashIntersection = AnalysisUtil.intersection(
					session.left.module.getGraphData().nodesByHash.keySet(),
					session.right.module.getGraphData().nodesByHash.keySet());
			Set<Long> hashUnion = AnalysisUtil.union(session.left.module.getGraphData().nodesByHash.keySet(),
					session.right.module.getGraphData().nodesByHash.keySet());
			hashIntersectionSize = hashIntersection.size();
			hashUnionSize = hashUnion.size();

			for (Long hash : hashIntersection) {
				hashIntersectionBlockCount += session.mergedGraphBuilder.graph.getGraphData().nodesByHash.get(hash)
						.size();
				hashIntersectionLeftBlockCount += session.left.module.getGraphData().nodesByHash.get(hash).size();
				hashIntersectionRightBlockCount += session.right.module.getGraphData().nodesByHash.get(hash).size();
			}

			mergedGraphNodeCount = session.mergedGraphBuilder.graph.getGraphData().nodesByHash.getNodeCount();
		}

		private void reportUnmatchedNodes() {
			reportUnmatchedNodes(session.left.module, session.right.module, "left");
			builder.merge.setLeftUnmatched(builder.unmatchedNodeSummary.build());

			reportUnmatchedNodes(session.right.module, session.left.module, "right");
			builder.merge.setRightUnmatched(builder.unmatchedNodeSummary.build());
		}

		private void reportUnmatchedNodes(ModuleGraph<?> cluster, ModuleGraph<?> oppositeCluster, String side) {
			Set<Node.Key> unmatchedNodes = new HashSet<Node.Key>(cluster.getAllKeys());
			unmatchedNodes.removeAll(session.matchedNodes.getLeftKeySet());
			unmatchedNodes.removeAll(session.matchedNodes.getRightKeySet());
			int totalUnmatchedCount = unmatchedNodes.size();
			int unreachableUnmatchedCount = 0;
			for (Node<?> unreachable : cluster.getUnreachableNodes()) {
				unmatchedNodes.remove(unreachable.getKey());
				unreachableUnmatchedCount++;
			}
			int hashExclusionCount = 0;
			for (Node.Key unmatchedKey : new ArrayList<Node.Key>(unmatchedNodes)) {
				Node<?> unmatchedNode = cluster.getNode(unmatchedKey);
				if (oppositeCluster.getGraphData().HACK_containsEquivalent(unmatchedNode)) {
					HACK_moduleRelativeTagMisses.add(unmatchedKey);
				}
				if (!oppositeCluster.getGraphData().nodesByHash.keySet().contains(unmatchedNode.getHash())) {
					unmatchedNodes.remove(unmatchedKey);
					hashExclusionCount++;
				}
			}

			builder.unmatchedNodeSummary.clear().setNodeCount(totalUnmatchedCount);
			builder.unmatchedNodeSummary.setEligibleNodeCount(unmatchedNodes.size());
			builder.unmatchedNodeSummary.setUnreachableNodeCount(unreachableUnmatchedCount);
			builder.unmatchedNodeSummary.setHashExclusiveNodeCount(hashExclusionCount);
		}

		private void computeMissedSubgraphs() {
			Set<Node.Key> leftMissed = new HashSet<Node.Key>(session.matchedNodes.HACK_leftMismatchedNodes);
			leftMissed.addAll(HACK_moduleRelativeTagMisses);

			Map<Node.Key, Set<Node.Key>> pendingSubgraphs = new HashMap<Node.Key, Set<Node.Key>>();
			for (Node.Key missed : new ArrayList<Node.Key>(leftMissed)) {
				Set<Node.Key> currentSubgraph = pendingSubgraphs.remove(missed);
				if (currentSubgraph == null) {
					currentSubgraph = new HashSet<Node.Key>();
					currentSubgraph.add(missed);
					HACK_missedSubgraphs.put(currentSubgraph, currentSubgraph);
				}
				boolean joined = false;
				Node<?> missedNode = session.left.module.getNode(missed);
				if (missedNode == null) {
					missedNode = session.right.module.getNode(missed);
				}
				OrdinalEdgeList<?> edgeList = missedNode.getOutgoingEdges();
				try {
					for (Edge<?> out : edgeList) {
						if (!leftMissed.contains(out.getToNode().getKey()))
							continue;
						for (Set<Node.Key> subgraph : new ArrayList<Set<Node.Key>>(HACK_missedSubgraphs.keySet())) {
							if (subgraph == currentSubgraph)
								continue;
							if (subgraph.contains(out.getToNode().getKey())) {
								subgraph.addAll(currentSubgraph);
								HACK_missedSubgraphs.remove(currentSubgraph);
								for (Node.Key member : currentSubgraph) {
									if (pendingSubgraphs.remove(member) != null)
										pendingSubgraphs.put(member, subgraph);
								}
								currentSubgraph = subgraph;
								joined = true;
								break;
							}
						}
						if (!joined) {
							currentSubgraph.add(out.getToNode().getKey());
							pendingSubgraphs.put(out.getToNode().getKey(), currentSubgraph);
						}
					}
				} finally {
					edgeList.release();
				}
			}

			for (Set<Node.Key> subgraph : HACK_missedSubgraphs.keySet()) {
				if (subgraph.size() < 2)
					continue;
				for (Node.Key key : subgraph) {
					boolean connected = false;
					Node<?> missedNode = session.left.module.getNode(key);
					for (Edge<?> out : missedNode.getOutgoingEdges()) {
						if (subgraph.contains(out.getToNode().getKey())) {
							connected = true;
							break;
						}
					}
					if (connected)
						continue;
					OrdinalEdgeList<?> edgeList = missedNode.getIncomingEdges();
					try {
						for (Edge<?> in : edgeList) {
							if (subgraph.contains(in.getFromNode().getKey())) {
								connected = true;
								break;
							}
						}
					} finally {
						edgeList.release();
					}
					if (connected)
						continue;
					Log.log("Error! Found a disconnected node in a subgraph of size %d!", subgraph.size());
				}

				if (subgraph.size() > 4) {
					Map<MetaNodeType, MutableInteger> nodeTypeCounts = new EnumMap<MetaNodeType, MutableInteger>(
							MetaNodeType.class);
					for (MetaNodeType type : MetaNodeType.values())
						nodeTypeCounts.put(type, new MutableInteger(0));
					EdgeCounter edgeTypeCounts = new EdgeCounter();
					List<Node<?>> entryPoints = new ArrayList<Node<?>>();
					List<Node<?>> nodes = new ArrayList<Node<?>>();
					for (Node.Key key : subgraph) {
						Node<?> node = session.left.module.getNode(key);
						nodes.add(node);
						nodeTypeCounts.get(node.getType()).increment();

						boolean subgraphContainsEntry = false;
						OrdinalEdgeList<?> edgeList = node.getIncomingEdges();
						try {
							for (Edge<?> edge : edgeList) {
								if (subgraph.contains(edge.getFromNode().getKey())) {
									subgraphContainsEntry = true;
									edgeTypeCounts.tally(edge.getEdgeType());
								}
							}
						} finally {
							edgeList.release();
						}
						if (!subgraphContainsEntry)
							entryPoints.add(node);
					}
					Log.log("Mismatched subgraph of %d nodes", subgraph.size());
					for (MetaNodeType type : MetaNodeType.values())
						if (nodeTypeCounts.get(type).getVal() > 0)
							Log.log("\tNode type %s: %d", type, nodeTypeCounts.get(type).getVal());
					for (EdgeType type : EdgeType.values())
						if (edgeTypeCounts.getCount(type) > 0)
							Log.log("\tEdge type %s: %d", type, edgeTypeCounts.getCount(type));
					Log.log("\tEntry points:");
					for (Node<?> entryPoint : entryPoints)
						Log.log("\t\t%s (%s)", entryPoint, session.matchedNodes.HACK_leftMismatchedNodes
								.contains(entryPoint.getKey()) ? "mismatched" : "missed");
					// System.out.println("check this"); // nodes.get(4).getIncomingEdges().size()
					// nodes.get(4).getOutgoingEdges().size()
				} // subgraph.contains(((Edge)nodes.get(4).getIncomingEdges().get(0)).getFromNode().getKey())
			} // subgraph.contains(((Edge)nodes.get(3).getOutgoingEdges().get(0)).getToNode().getKey())

			averageMissedSubgraphSize = (int) Math.floor(leftMissed.size() / (double) HACK_missedSubgraphs.size());
		}

		private void outputMergedGraphInfo() {
			builder.traceProfile.clear().setUnion(hashUnionSize);
			builder.traceProfile.setIntersection(hashIntersectionSize);
			builder.traceProfile.setLeft(session.left.module.getGraphData().nodesByHash.keySet().size());
			builder.traceProfile.setRight(session.right.module.getGraphData().nodesByHash.keySet().size());
			builder.merge.setHashProfile(builder.traceProfile.build());

			builder.traceProfile.clear().setUnion(mergedGraphNodeCount); // simple node count of the entire merged graph
			builder.traceProfile.setIntersection(session.matchedNodes.size());
			builder.traceProfile.setLeft(session.left.module.getNodeCount());
			builder.traceProfile.setRight(session.right.module.getNodeCount());
			builder.merge.setGraphProfile(builder.traceProfile.build());

			builder.traceProfile.clear().setUnion(mergedGraphNodeCount); // simple node count of the entire merged graph
			builder.traceProfile.setIntersection(hashIntersectionBlockCount);
			builder.traceProfile.setLeft(hashIntersectionLeftBlockCount);
			builder.traceProfile.setRight(hashIntersectionRightBlockCount);
			builder.merge.setGraphWithinHashIntersection(builder.traceProfile.build());

			builder.summary.clear().setIndirectEdgesMatched(session.statistics.getIndirectEdgeMatchCount());
			builder.summary.setPureHeuristicMatches(session.statistics.getPureHeuristicMatchCount());
			builder.summary.setCallContinuationEdgesMatched(session.statistics.getCallContinuationMatchCount());
			builder.summary.setExceptionContinuationEdgesMatched(session.statistics
					.getExceptionContinuationMatchCount());
			builder.summary.setPossiblyRewrittenBlocks(session.statistics.getPossibleRewrites());
			builder.summary.setModuleRelativeTagMismatches(session.matchedNodes.HACK_leftMismatchedNodes.size()
					+ HACK_moduleRelativeTagMisses.size());
			builder.summary.setMismatchedSubgraphCount(HACK_missedSubgraphs.size());

			List<Integer> missedSubgraphSizes = new ArrayList<Integer>();
			for (Set<Node.Key> missedSubgraph : HACK_missedSubgraphs.keySet()) {
				missedSubgraphSizes.add(missedSubgraph.size());
			}
			Collections.sort(missedSubgraphSizes, new Comparator<Integer>() {
				@Override
				public int compare(Integer first, Integer second) {
					return second - first;
				}
			});
			Integer lastSize = null;
			for (Integer missedSubgraphSize : missedSubgraphSizes) {
				if (missedSubgraphSize <= averageMissedSubgraphSize) {
					if (lastSize == null)
						builder.summary.addLargestMismatchedSubgraphsSize(missedSubgraphSize);
					break;
				}
				if (lastSize == missedSubgraphSize)
					continue;
				lastSize = missedSubgraphSize;
				builder.summary.addLargestMismatchedSubgraphsSize(missedSubgraphSize);
			}

			builder.merge.setMergeSummary(builder.summary.build());
		}
	}

	private final Builder builder = new Builder();
	private final Map<ApplicationModule, ClusterResults> resultsByCluster = new HashMap<ApplicationModule, ClusterResults>();

	private ClusterResults currentCluster = null;

	@Override
	public void setGraphSummaries(Graph.Process leftGraphSummary, Graph.Process rightGraphSummary) {
		builder.results.setLeft(leftGraphSummary);
		builder.results.setRight(rightGraphSummary);
	}

	@Override
	public HashMerge.HashMergeResults getResults() {
		return builder.results.build();
	}

	@Override
	public GraphMergeStrategy getStrategy() {
		return GraphMergeStrategy.HASH;
	}

	@Override
	public void beginCluster(HashMergeSession session) {
		currentCluster = new ClusterResults(session);
		resultsByCluster.put(session.left.module.module, currentCluster);

		Log.log("\n  === Merging cluster %s ===\n", session.left.module.module.name);
	}

	@Override
	public void clusterMergeCompleted() {
		currentCluster.mergeCompleted();
		currentCluster = null;
	}
}
