package edu.uci.eecs.crowdsafe.merge.graph.anonymous;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeCandidate;

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

	private static class ModuleOwnerKey {
		final AutonomousSoftwareDistribution cluster;
		final boolean isBlackBox;

		ModuleOwnerKey(AutonomousSoftwareDistribution cluster, boolean isBlackBox) {
			this.cluster = cluster;
			this.isBlackBox = isBlackBox;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((cluster == null) ? 0 : cluster.hashCode());
			result = prime * result + (isBlackBox ? 1231 : 1237);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ModuleOwnerKey other = (ModuleOwnerKey) obj;
			if (cluster == null) {
				if (other.cluster != null)
					return false;
			} else if (!cluster.equals(other.cluster))
				return false;
			if (isBlackBox != other.isBlackBox)
				return false;
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

	final ClusterGraphCache graphCache;
	final AnonymousSubgraphFlowAnalysis flowAnalsis = new AnonymousSubgraphFlowAnalysis();

	List<AnonymousSubgraph> maximalSubgraphs = new ArrayList<AnonymousSubgraph>();
	Map<ModuleOwnerKey, AnonymousModule> modulesByOwner = new HashMap<ModuleOwnerKey, AnonymousModule>();

	public AnonymousGraphAnalyzer(GraphMergeCandidate leftData, GraphMergeCandidate rightData) {
		graphCache = new ClusterGraphCache(leftData, rightData);
	}

	void installSubgraphs(List<ModuleGraphCluster<ClusterNode<?>>> anonymousGraphs) throws IOException {
		if (anonymousGraphs.isEmpty())
			return;

		for (ModuleGraphCluster<ClusterNode<?>> dynamicGraph : anonymousGraphs) {
			for (AnonymousSubgraph maximalSubgraph : MaximalSubgraphs.getMaximalSubgraphs(dynamicGraph)) {
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

		Set<AutonomousSoftwareDistribution> allConnectingClusters = new HashSet<AutonomousSoftwareDistribution>();
		AutonomousSoftwareDistribution owningCluster;
		for (AnonymousSubgraph subgraph : maximalSubgraphs) {
			// if (subgraph.getAllNodes().size() > 1000)
			// Log.log("\n === Subgraph of %d nodes with %d total hashes", subgraph.getExecutableNodeCount(),
			// subgraph.getGraphData().nodesByHash.keySet().size());

			String clusterName;
			AutonomousSoftwareDistribution cluster;
			owningCluster = null;
			allConnectingClusters.clear();
			if (subgraph.getEntryPoints().isEmpty()) {
				Log.log("Error: entry point missing for anonymous subgraph of %d nodes!", subgraph.getNodeCount());
				subgraph.logGraph();
				continue;
			}

			for (ClusterNode<?> entryPoint : subgraph.getEntryPoints()) {
				OrdinalEdgeList<?> edges = entryPoint.getOutgoingEdges();
				try {
					int leftCallSiteCount = 0, rightCallSiteCount = 0;
					cluster = ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousEntryHash(
							entryPoint.getHash());
					if (cluster == null) {
						cluster = ConfiguredSoftwareDistributions.getInstance().getClusterByInterceptionHash(
								entryPoint.getHash());
					}
					if (cluster == null) {
						Log.log("Error: unrecognized entry point 0x%x for anonymous subgraph of %d nodes!",
								entryPoint.getHash(), subgraph.getNodeCount());
						Log.log("\tSubgraph is owned by %s", owningCluster);
						continue;
					}

					cluster = AnonymousModule.resolveAlias(cluster);
					allConnectingClusters.add(cluster);

					if (AnonymousModule.isEligibleOwner(cluster)) {
						if (owningCluster == null) {
							owningCluster = cluster;
						} else if (owningCluster != cluster) {
							Log.log("Error: subgraph of %d nodes has entry points from multiple clusters: %s and %s",
									subgraph.getNodeCount(), cluster.name, owningCluster.name);
							owningCluster = null;
							break;
						}
					}

					clusterName = cluster.name;
					leftCallSiteCount = getExitEdgeCount(entryPoint.getHash(), graphCache.getLeftGraph(cluster));
					rightCallSiteCount = getExitEdgeCount(entryPoint.getHash(), graphCache.getRightGraph(cluster));

					// Log.log("     Entry point 0x%x (%s) reaches %d nodes from %d left call sites and %d right call sites",
					// entryPoint.getHash(), clusterName, edges.size(), leftCallSiteCount, rightCallSiteCount);
				} finally {
					edges.release();
				}
			}

			for (ClusterNode<?> exitPoint : subgraph.getExitPoints()) {
				OrdinalEdgeList<?> edges = exitPoint.getIncomingEdges();
				try {
					int leftTargetCount = 0, rightTargetCount = 0;
					cluster = ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousExitHash(
							exitPoint.getHash());
					if (cluster == null) {
						// Log.log("     Callout 0x%x (%s) to an exported function", node.getHash());
						continue;
					}

					cluster = AnonymousModule.resolveAlias(cluster);
					allConnectingClusters.add(cluster);

					if (AnonymousModule.isEligibleOwner(cluster)) {
						if (owningCluster == null) {
							owningCluster = cluster;
						}
						/**
						 * <pre>else if (owningCluster != cluster) {
							Log.log("Error: subgraph of %d nodes owned by %s has exit points to a potential alternate owner: %s",
									subgraph.getNodeCount(), owningCluster.name, cluster.name);
							owningCluster = null;
							break;
						}
						 */
					}

					clusterName = cluster.name;
					leftTargetCount = getEntryEdgeCount(exitPoint.getHash(), graphCache.getLeftGraph(cluster));
					rightTargetCount = getEntryEdgeCount(exitPoint.getHash(), graphCache.getRightGraph(cluster));

					// Log.log("     Callout 0x%x (%s) from %d nodes to %d left targets and %d right targets",
					// node.getHash(), clusterName, edges.size(), leftTargetCount, rightTargetCount);
				} finally {
					edges.release();
				}
			}

			if (owningCluster == null) {
				if (allConnectingClusters.size() == 1) {
					owningCluster = allConnectingClusters.iterator().next();
				} else {
					Log.log("Error: could not determine the owning cluster of an anonymous subgraph with %d nodes!",
							subgraph.getNodeCount());
					Log.log("\tPotential owners are: %s", allConnectingClusters);
					continue;
				}
			}

			ModuleOwnerKey key = new ModuleOwnerKey(owningCluster, subgraph.isAnonymousBlackBox());
			AnonymousModule module = modulesByOwner.get(key);
			if (module == null) {
				module = new AnonymousModule(owningCluster);
				modulesByOwner.put(key, module);
			}
			module.addSubgraph(subgraph);
		}
	}

	void analyzeModules() throws IOException {
		for (AnonymousModule module : modulesByOwner.values()) {
			if (module.isBlackBox()) {
				Log.log(" === Anonymous black box module owned by %s ===", module.owningCluster.name);

				AutonomousSoftwareDistribution cluster;
				int arbitrarySubgraphId = -1;
				for (AnonymousSubgraph subgraph : module.subgraphs) {
					arbitrarySubgraphId++;
					if (module.hasEscapes(subgraph)) {
						Log.log("\tEscapes in subgraph %d:", arbitrarySubgraphId);
						for (ClusterNode<?> entry : subgraph.getEntryPoints()) {
							cluster = ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousEntryHash(
									entry.getHash());
							if (cluster != module.owningCluster)
								Log.log("\t\tEntry from %s", cluster.name);
						}
						for (ClusterNode<?> exit : subgraph.getExitPoints()) {
							cluster = ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousExitHash(
									exit.getHash());
							if (cluster == null)
								Log.log("\t\tError! Exit to unknown cluster!");
							else if (cluster != module.owningCluster)
								Log.log("\t\tExit to %s", cluster.name);
						}
					} else {
						Log.log("\tNo escapes in subgraph %d", arbitrarySubgraphId);
					}
				}
				Log.log();
			} else {
				Log.log(" ==== Anonymous white box module owned by %s ====", module.owningCluster.name);
				Log.log("\t%d subgraphs with %d total nodes", module.subgraphs.size(), module.getNodeCount());

				module.reportEdgeProfile();
				if (module.subgraphs.size() < 10) {
					for (AnonymousSubgraph subgraph : module.subgraphs)
						if (subgraph.getNodeCount() < 10)
							subgraph.logGraph();
				}

				// flowAnalsis.clear();
				// flowAnalsis.analyzeFlow(module);

				/**
				 * <pre>
    			for (int i = 0; i < module.subgraphs.size() - 1; i++) {
    				for (int j = i + 1; j < module.subgraphs.size(); j++) {
    					ModuleGraphCluster<ClusterNode<?>> iGraph = module.subgraphs.get(i);
    					ModuleGraphCluster<ClusterNode<?>> jGraph = module.subgraphs.get(j);
    
    					int sizeDelta = (iGraph.getNodeCount() / jGraph.getNodeCount());
    					if (sizeDelta > 100)
    						continue;
    
    					AnonymousSubgraphCompatibilityAnalysis analysis = new AnonymousSubgraphCompatibilityAnalysis(
    							iGraph, jGraph);
    					analysis.fullCompatibilityPerEntry();
    				}
    			}
				 */
			}
		}
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
	}

	void fullCompatibilityAnalysis(ModuleGraphCluster<ClusterNode<?>> left, ModuleGraphCluster<ClusterNode<?>> right) {
		AnonymousSubgraphCompatibilityAnalysis analysis = new AnonymousSubgraphCompatibilityAnalysis(left, right);
		analysis.fullCompatibilityPerEntry();
	}

	public static void main(String[] args) {
		long foo = (0L ^ (1L << 0x3fL));
		System.out.println(String.format("foo: 0x%x", foo));
	}
}