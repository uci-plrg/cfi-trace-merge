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

	private static class CompatibilityAnalysis {
		final ModuleGraphCluster<ClusterNode<?>> left;
		final ModuleGraphCluster<ClusterNode<?>> right;

		private Set<ClusterNode<?>> leftCoverageSet = null;
		private Set<ClusterNode<?>> rightCoverageSet = null;

		private MutableInteger maxExploredDepth = null;
		private MutableInteger maxCompatibleDepth = null;
		// private MutableInteger failedIndirectsHaving

		private boolean followIndirectBranches = false;

		CompatibilityAnalysis(ModuleGraphCluster<ClusterNode<?>> left, ModuleGraphCluster<ClusterNode<?>> right) {
			// put the larger graph on the left
			if (right.getNodeCount() > left.getNodeCount()) {
				ModuleGraphCluster<ClusterNode<?>> swap = right;
				right = left;
				left = swap;
			}

			this.left = left;
			this.right = right;
		}

		void localCompatibilityPerNode(int depth) {
			Log.log("\nExploring localized compatibility of two graphs (left: %d nodes, right: %d nodes, depth: %d)",
					left.getNodeCount(), right.getNodeCount(), depth);

			maxExploredDepth = null;
			maxCompatibleDepth = null;
			followIndirectBranches = false;

			Set<ClusterNode<?>> unmatchedRightNodes = new HashSet<ClusterNode<?>>(right.getAllNodes());

			int compatibleCount = 0;
			int incompatibleCount = 0;
			boolean compatible;
			for (ClusterNode<?> leftNode : left.getAllNodes()) {
				compatible = false;
				NodeList<ClusterNode<?>> hashMatches = right.getGraphData().nodesByHash.get(leftNode.getHash());
				if (hashMatches != null) {
					for (int i = 0; i < hashMatches.size(); i++) {
						ClusterNode<?> rightNode = hashMatches.get(i);
						if (isCompatible(leftNode, rightNode, depth)) {
							unmatchedRightNodes.remove(rightNode);
							compatible = true;
							break;
						}
					}
				}
				if (compatible)
					compatibleCount++;
				else
					incompatibleCount++;
			}
			int rightGraphCoverage = (right.getNodeCount() - unmatchedRightNodes.size());
			Log.log("\tPer left node: %d compatible and %d incompatible (%d right coverage)", compatibleCount,
					incompatibleCount, rightGraphCoverage);

			compatibleCount = rightGraphCoverage;
			incompatibleCount = 0;
			for (ClusterNode<?> rightNode : unmatchedRightNodes) {
				compatible = false;
				NodeList<ClusterNode<?>> hashMatches = left.getGraphData().nodesByHash.get(rightNode.getHash());
				if (hashMatches != null) {
					for (int i = 0; i < hashMatches.size(); i++) {
						ClusterNode<?> leftNode = hashMatches.get(i);
						if (isCompatible(leftNode, rightNode, depth)) {
							compatible = true;
							break;
						}
					}
				}
				if (compatible)
					compatibleCount++;
				else
					incompatibleCount++;
			}

			Log.log("\tPer right node: %d compatible and %d incompatible", compatibleCount, incompatibleCount);
		}

		void fullCompatibilityPerEntry() {
			Log.log("\nExploring entry point compatibility of two graphs (left: %d nodes, right: %d nodes)",
					left.getNodeCount(), right.getNodeCount());

			maxExploredDepth = new MutableInteger(0);
			maxCompatibleDepth = new MutableInteger(0);
			followIndirectBranches = true;

			leftCoverageSet = new HashSet<ClusterNode<?>>();
			rightCoverageSet = new HashSet<ClusterNode<?>>();
			Set<ClusterNode<?>> leftCompatibleCoverageSet = new HashSet<ClusterNode<?>>();
			Set<ClusterNode<?>> rightCompatibleCoverageSet = new HashSet<ClusterNode<?>>();
			List<Integer> compatibleSubgraphDepths = new ArrayList<Integer>();

			int compatibleCount = 0;
			int incompatibleCount = 0;
			int totalRightNodes = 0;
			boolean compatible;
			Set<ClusterNode<?>> unmatchedRightNodes = new HashSet<ClusterNode<?>>();
			Set<Long> entryHashes = new HashSet<Long>(left.getEntryHashes());
			entryHashes.addAll(right.getEntryHashes());
			for (Long entryHash : entryHashes) {
				ClusterNode<?> leftEntry = left.getEntryPoint(entryHash);
				if (leftEntry == null) {
					Log.log("Entry point 0x%x does not occur on the left side!", entryHash);
					continue;
				}
				ClusterNode<?> rightEntry = right.getEntryPoint(entryHash);
				if (rightEntry == null) {
					Log.log("Entry point 0x%x does not occur on the right side!", entryHash);
					continue;
				}

				OrdinalEdgeList<ClusterNode<?>> leftEdges = leftEntry.getOutgoingEdges();
				OrdinalEdgeList<ClusterNode<?>> rightEdges = rightEntry.getOutgoingEdges();
				try {
					for (Edge<ClusterNode<?>> rightEdge : rightEdges) {
						unmatchedRightNodes.add(rightEdge.getToNode());
						totalRightNodes++;
					}

					for (Edge<ClusterNode<?>> leftEdge : leftEdges) {
						ClusterNode<?> leftToNode = leftEdge.getToNode();
						compatible = false;
						for (Edge<ClusterNode<?>> rightEdge : rightEdges) {
							ClusterNode<?> rightToNode = rightEdge.getToNode();
							if (leftToNode.getHash() == rightToNode.getHash()) {
								leftCoverageSet.clear();
								rightCoverageSet.clear();
								maxExploredDepth.setVal(Integer.MAX_VALUE);
								maxCompatibleDepth.setVal(Integer.MAX_VALUE);
								if (isCompatible(leftToNode, rightToNode, Integer.MAX_VALUE)) {
									unmatchedRightNodes.remove(rightToNode);
									leftCompatibleCoverageSet.addAll(leftCoverageSet);
									rightCompatibleCoverageSet.addAll(rightCoverageSet);
									compatibleSubgraphDepths.add(Integer.MAX_VALUE - maxCompatibleDepth.getVal());
									compatible = true;
									break;
								}
							}
						}

						if (compatible)
							compatibleCount++;
						else
							incompatibleCount++;
					}

					int rightGraphCoverage = (totalRightNodes - unmatchedRightNodes.size());
					Log.log("\tEntry point 0x%x per left node: %d compatible and %d incompatible (%d right coverage)",
							entryHash, compatibleCount, incompatibleCount, rightGraphCoverage);

					compatibleCount = rightGraphCoverage;
					incompatibleCount = 0;
					for (ClusterNode<?> rightToNode : unmatchedRightNodes) {
						compatible = false;
						for (Edge<ClusterNode<?>> leftEdge : leftEdges) {
							ClusterNode<?> leftToNode = leftEdge.getToNode();
							if (leftToNode.getHash() == rightToNode.getHash()) {
								leftCoverageSet.clear();
								rightCoverageSet.clear();
								maxExploredDepth.setVal(Integer.MAX_VALUE);
								maxCompatibleDepth.setVal(Integer.MAX_VALUE);
								if (isCompatible(leftToNode, rightToNode, Integer.MAX_VALUE)) {
									leftCompatibleCoverageSet.addAll(leftCoverageSet);
									rightCompatibleCoverageSet.addAll(rightCoverageSet);
									compatibleSubgraphDepths.add(Integer.MAX_VALUE - maxCompatibleDepth.getVal());
									compatible = true;
									break;
								}
							}
						}

						if (compatible)
							compatibleCount++;
						else
							incompatibleCount++;
					}
					Log.log("\tEntry point 0x%x per right node: %d compatible and %d incompatible", entryHash,
							compatibleCount, incompatibleCount);
					Log.log("\tCoverage of compatible entry subgraphs: %d left, %d right",
							leftCompatibleCoverageSet.size(), rightCompatibleCoverageSet.size());

					int totalCompatibleSubgraphDepth = 0;
					for (int compatibleSubgraphDepth : compatibleSubgraphDepths) {
						totalCompatibleSubgraphDepth += compatibleSubgraphDepth;
					}
					int averageCompatibleSubgraphDepth = totalCompatibleSubgraphDepth / compatibleSubgraphDepths.size();
					Log.log("\tAverage compatible subgraph depth: %d", averageCompatibleSubgraphDepth);
				} finally {
					leftEdges.release();
					rightEdges.release();
				}
			}
		}

		private boolean isCompatible(ClusterNode<?> left, ClusterNode<?> right, int depth) {
			if ((maxExploredDepth != null) && (depth < maxExploredDepth.getVal()))
				maxExploredDepth.setVal(depth);

			int ordinalCount = left.getOutgoingOrdinalCount();
			if ((depth > 0) && (left.getType() != MetaNodeType.CLUSTER_ENTRY) && (ordinalCount != 0)) {
				ordinals: for (int ordinal = 0; ordinal < ordinalCount; ordinal++) {
					OrdinalEdgeList<ClusterNode<?>> leftEdges = left.getOutgoingEdges(ordinal);
					OrdinalEdgeList<ClusterNode<?>> rightEdges = right.getOutgoingEdges(ordinal);
					try {
						if (leftEdges.isEmpty() || rightEdges.isEmpty())
							continue;

						EdgeType leftEdgeType = leftEdges.get(0).getEdgeType();
						EdgeType rightEdgeType = rightEdges.get(0).getEdgeType();

						if (leftEdgeType != rightEdgeType) {
							Log.log("Hash collision: edge types differ for hash 0x%x at ordinal %d!", left.getHash(),
									ordinal);
							return false;
						}

						switch (leftEdgeType) {
							case DIRECT:
							case CALL_CONTINUATION: {
								for (Edge<ClusterNode<?>> leftEdge : leftEdges) {
									ClusterNode<?> leftToNode = leftEdge.getToNode();
									for (Edge<ClusterNode<?>> rightEdge : rightEdges) {
										ClusterNode<?> rightToNode = rightEdge.getToNode();
										if (leftToNode.getHash() == rightToNode.getHash())
											if (isCompatible(leftToNode, rightToNode, depth - 1))
												continue ordinals;
									}
								}
								return false;
							}
							case INDIRECT:
							case UNEXPECTED_RETURN:
								for (Edge<ClusterNode<?>> leftEdge : leftEdges) {
									ClusterNode<?> leftToNode = leftEdge.getToNode();
									for (Edge<ClusterNode<?>> rightEdge : rightEdges) {
										ClusterNode<?> rightToNode = rightEdge.getToNode();
										if (leftToNode.getHash() == rightToNode.getHash()) {
											if (followIndirectBranches) {
												if (!isCompatible(leftToNode, rightToNode, depth - 1))
													continue;
											}
											continue ordinals;
										}
									}
								}
								return false;
							case CLUSTER_ENTRY:
								throw new IllegalStateException(
										"Cluster entry edges should only appear on cluster entry nodes!");
						}
						break;
					} finally {
						leftEdges.release();
						rightEdges.release();
					}
				}
			}

			if ((maxCompatibleDepth != null) && (depth < maxCompatibleDepth.getVal()))
				maxCompatibleDepth.setVal(depth);
			if (leftCoverageSet != null)
				leftCoverageSet.add(left);
			if (rightCoverageSet != null)
				rightCoverageSet.add(right);

			return true;
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

	final Map<Long, AutonomousSoftwareDistribution> clustersByAnonymousEntryHash = new HashMap<Long, AutonomousSoftwareDistribution>();
	final Map<Long, AutonomousSoftwareDistribution> clustersByAnonymousExitHash = new HashMap<Long, AutonomousSoftwareDistribution>();

	GraphMergeCandidate leftData;
	GraphMergeCandidate rightData;

	List<ModuleGraphCluster<ClusterNode<?>>> maximalSubgraphs = new ArrayList<ModuleGraphCluster<ClusterNode<?>>>();

	void initialize(GraphMergeCandidate leftData, GraphMergeCandidate rightData) {
		this.leftData = leftData;
		this.rightData = rightData;

		for (AutonomousSoftwareDistribution cluster : leftData.getRepresentedClusters()) {
			addAnonymousEdgeHashes(cluster);
		}
		for (AutonomousSoftwareDistribution cluster : rightData.getRepresentedClusters()) {
			if (!leftData.getRepresentedClusters().contains(cluster))
				addAnonymousEdgeHashes(cluster);
		}
	}

	// soon to be <anonymous> for all anonymous modules
	private void addAnonymousEdgeHashes(AutonomousSoftwareDistribution cluster) {
		for (SoftwareUnit unit : cluster.getUnits()) {
			if (unit.filename.equals("ole32.dll"))
				toString();

			long hash = stringHash(String.format("%s/<anonymous>!callback", unit.filename));
			clustersByAnonymousEntryHash.put(hash, cluster);

			hash = stringHash(String.format("<anonymous>/%s!callback", unit.filename));
			clustersByAnonymousExitHash.put(hash, cluster);
		}
	}

	private long stringHash(String string) {
		long hash = 0L;
		for (int i = 0; i < string.length(); i++) {
			hash = hash ^ (hash << 5) ^ ((int) string.charAt(i));
		}
		return hash;
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
		}
	}

	void reportSubgraph(String name, ModuleGraphCluster<ClusterNode<?>> subgraph) throws IOException {
		Log.log("\n === %s of %d nodes", name, subgraph.getExecutableNodeCount());

		String clusterName;
		AutonomousSoftwareDistribution cluster;
		for (Long entryHash : subgraph.getEntryHashes()) {
			ClusterNode<?> entryPoint = subgraph.getEntryPoint(entryHash);

			OrdinalEdgeList<?> edges = entryPoint.getOutgoingEdges();
			try {
				int leftCallSiteCount = 0, rightCallSiteCount = 0;
				cluster = clustersByAnonymousEntryHash.get(entryHash);
				if (cluster == null) {
					clusterName = "?";
				} else {
					clusterName = cluster.name;
					leftCallSiteCount = getExitEdgeCount(entryHash, leftData.getClusterGraph(cluster));
					rightCallSiteCount = getExitEdgeCount(entryHash, rightData.getClusterGraph(cluster));
				}
				Log.log("     Entry point 0x%x (%s) reaches %d nodes from %d left call sites and %d right call sites",
						entryHash, clusterName, edges.size(), leftCallSiteCount, rightCallSiteCount);
			} finally {
				edges.release();
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
					cluster = clustersByAnonymousExitHash.get(node.getHash());
					if (cluster == null) {
						clusterName = "?";
					} else {
						clusterName = cluster.name;
						leftTargetCount = getEntryEdgeCount(node.getHash(), leftData.getClusterGraph(cluster));
						rightTargetCount = getEntryEdgeCount(node.getHash(), rightData.getClusterGraph(cluster));
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
		Log.log("     Average indirect edge fanout: %03f; Max: %d; singletons: %d (%d%%); pairs: %d (%d%%)",
				averageIndirectEdgeCount, maxIndirectEdgeCountPerOrdinal, singletonIndirectOrdinalCount,
				singletonIndirectPercentage, pairIndirectOrdinalCount, pairIndirectPercentage);
	}

	private int getEntryEdgeCount(long entryHash, ModuleGraphCluster<?> targetGraph) {
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
		CompatibilityAnalysis analysis = new CompatibilityAnalysis(left, right);
		analysis.localCompatibilityPerNode(20);
		analysis.fullCompatibilityPerEntry();
	}

	public static void main(String[] args) {
		long foo = (0L ^ (1L << 0x3fL));
		System.out.println(String.format("foo: 0x%x", foo));
	}
}
