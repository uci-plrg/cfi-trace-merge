package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeCandidate;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeSource;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousModule;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousSubgraph;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.MaximalSubgraphs;

public class AnonymousModuleReportSet {
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
		final GraphMergeCandidate mergeCandidate;

		final Map<AutonomousSoftwareDistribution, ModuleGraphCluster<?>> graphs = new HashMap<AutonomousSoftwareDistribution, ModuleGraphCluster<?>>();

		public ClusterGraphCache(GraphMergeCandidate mergeCandidate) {
			this.mergeCandidate = mergeCandidate;
		}

		ModuleGraphCluster<?> getGraph(AutonomousSoftwareDistribution cluster) throws IOException {
			ModuleGraphCluster<?> graph = graphs.get(cluster);
			if (graph == null) {
				graph = mergeCandidate.getClusterGraph(cluster);
				graphs.put(cluster, graph);
			}
			return graph;
		}
	}

	final String name;

	List<AnonymousSubgraph> maximalSubgraphs = new ArrayList<AnonymousSubgraph>();
	private final Map<AnonymousModule.OwnerKey, AnonymousModule> modulesByOwner = new HashMap<AnonymousModule.OwnerKey, AnonymousModule>();

	public AnonymousModuleReportSet(String name) {
		this.name = name;
	}

	Set<AnonymousModule.OwnerKey> getModuleOwners() {
		return modulesByOwner.keySet();
	}

	AnonymousModule getModule(AnonymousModule.OwnerKey owner) {
		return modulesByOwner.get(owner);
	}

	void installSubgraphs(GraphMergeSource source, List<? extends ModuleGraphCluster<ClusterNode<?>>> anonymousGraphs) {
		if (anonymousGraphs.isEmpty())
			return;

		for (ModuleGraphCluster<ClusterNode<?>> dynamicGraph : anonymousGraphs) {
			maximalSubgraphs.addAll(MaximalSubgraphs.getMaximalSubgraphs(source, dynamicGraph));
		}
		analyzeModules();
	}

	void installModules(List<AnonymousModule> modules) throws IOException {
		if (modules.isEmpty())
			return;

		for (AnonymousModule module : modules) {
			maximalSubgraphs.addAll(module.subgraphs);
		}
		analyzeModules();
	}

	private void analyzeModules() {
		Collections.sort(maximalSubgraphs, new SizeOrder());

		Set<AutonomousSoftwareDistribution> allConnectingClusters = new HashSet<AutonomousSoftwareDistribution>();
		AutonomousSoftwareDistribution owningCluster;
		Set<AutonomousSoftwareDistribution> ambiguousOwnerSet = new HashSet<AutonomousSoftwareDistribution>();
		subgraphs: for (AnonymousSubgraph subgraph : maximalSubgraphs) {
			AutonomousSoftwareDistribution cluster;
			boolean ambiguousOwner = false;
			ambiguousOwnerSet.clear();
			owningCluster = null;
			allConnectingClusters.clear();
			if (subgraph.getEntryPoints().isEmpty() && !subgraph.isAnonymousBlackBox()) {
				Log.log("Error: entry point missing for anonymous subgraph of %d nodes in %s!",
						subgraph.getNodeCount(), name);
				Log.log("\tOmitting this subgraph from the merge.");
				// subgraph.logGraph();
				continue subgraphs;
			}

			for (ClusterNode<?> entryPoint : subgraph.getEntryPoints()) {
				OrdinalEdgeList<?> edges = entryPoint.getOutgoingEdges();
				try {
					if (ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousGencodeHash(
							entryPoint.getHash()) != null)
						continue; // no ownership by gencode, it's not reliable

					cluster = ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousEntryHash(
							entryPoint.getHash());
					if (cluster == null) {
						cluster = ConfiguredSoftwareDistributions.getInstance().getClusterByInterceptionHash(
								entryPoint.getHash());
					}
					if (cluster == null) {
						Log.log("Error: unrecognized entry point 0x%x for anonymous subgraph of %d nodes!",
								entryPoint.getHash(), subgraph.getNodeCount());
						Log.log("\tKeeping the edge but discarding this owner.");
						// Log.log("\tSubgraph is owned by %s", owningCluster);
						continue;
					}

					cluster = AnonymousModule.resolveAlias(cluster);

					allConnectingClusters.add(cluster);

					if (AnonymousModule.isEligibleOwner(cluster)) {
						if (ambiguousOwner) {
							ambiguousOwnerSet.add(cluster);
						} else {
							if (owningCluster == null) {
								owningCluster = cluster;
							} else if (owningCluster != cluster) {
								ambiguousOwner = true;
								ambiguousOwnerSet.add(owningCluster);
								ambiguousOwnerSet.add(cluster);
							}
						}
					}
				} finally {
					edges.release();
				}
			}

			if (!ambiguousOwner) {
				for (ClusterNode<?> exitPoint : subgraph.getExitPoints()) {
					OrdinalEdgeList<?> edges = exitPoint.getIncomingEdges();
					try {
						// int leftTargetCount = 0, rightTargetCount = 0;
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
						}
					} finally {
						edges.release();
					}
				}
			}

			if (ambiguousOwner) {
				StringBuilder buffer = new StringBuilder();
				for (AutonomousSoftwareDistribution c : ambiguousOwnerSet) {
					buffer.append(c.getUnitFilename());
					buffer.append(", ");
				}
				buffer.setLength(buffer.length() - 2);
				Log.log("Error: subgraph of %d nodes has entry points from multiple clusters: %s",
						subgraph.getNodeCount(), buffer);
				Log.log("\tOmitting this subgraph from the merge.");
			} else {
				if (owningCluster == null) {
					if (allConnectingClusters.size() == 1) {
						owningCluster = allConnectingClusters.iterator().next();
					} else {
						Log.log("Error: could not determine the owning cluster of an anonymous subgraph with %d nodes!",
								subgraph.getNodeCount());
						Log.log("\tPotential owners are: %s", allConnectingClusters);
						Log.log("\tOmitting this subgraph from the merge.");
						continue subgraphs;
					}
				}

				AnonymousModule.OwnerKey key = new AnonymousModule.OwnerKey(owningCluster,
						subgraph.isAnonymousBlackBox());
				AnonymousModule module = modulesByOwner.get(key);
				if (module == null) {
					module = new AnonymousModule(owningCluster);
					modulesByOwner.put(key, module);
				}
				module.addSubgraph(subgraph);
			}
		}
	}
}
