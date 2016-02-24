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
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.AnonymousGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.AnonymousGraphCollection;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.MaximalSubgraphs;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeCandidate;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeSource;

public class AnonymousModuleReportSet {
	private static class SizeOrder implements Comparator<ModuleGraph<ModuleNode<?>>> {
		@Override
		public int compare(ModuleGraph<ModuleNode<?>> first, ModuleGraph<ModuleNode<?>> second) {
			int comparison = second.getExecutableNodeCount() - first.getExecutableNodeCount();
			if (comparison != 0)
				return comparison;

			return (int) second.hashCode() - (int) first.hashCode();
		}
	}

	private static class GraphCache {
		final GraphMergeCandidate mergeCandidate;

		final Map<ApplicationModule, ModuleGraph<?>> graphs = new HashMap<ApplicationModule, ModuleGraph<?>>();

		public GraphCache(GraphMergeCandidate mergeCandidate) {
			this.mergeCandidate = mergeCandidate;
		}

		ModuleGraph<?> getGraph(ApplicationModule module) throws IOException {
			ModuleGraph<?> graph = graphs.get(module);
			if (graph == null) {
				graph = mergeCandidate.getModuleGraph(module);
				graphs.put(module, graph);
			}
			return graph;
		}
	}

	final String name;

	List<AnonymousGraph> maximalSubgraphs = new ArrayList<AnonymousGraph>();
	private final Map<AnonymousGraphCollection.OwnerKey, AnonymousGraphCollection> modulesByOwner = new HashMap<AnonymousGraphCollection.OwnerKey, AnonymousGraphCollection>();

	public AnonymousModuleReportSet(String name) {
		this.name = name;
	}

	Set<AnonymousGraphCollection.OwnerKey> getModuleOwners() {
		return modulesByOwner.keySet();
	}

	AnonymousGraphCollection getModule(AnonymousGraphCollection.OwnerKey owner) {
		return modulesByOwner.get(owner);
	}

	void installSubgraphs(GraphMergeSource source, List<? extends ModuleGraph<ModuleNode<?>>> anonymousGraphs) {
		if (anonymousGraphs.isEmpty())
			return;

		for (ModuleGraph<ModuleNode<?>> dynamicGraph : anonymousGraphs) {
			maximalSubgraphs.addAll(MaximalSubgraphs.getMaximalSubgraphs(dynamicGraph));
		}
		analyzeModules();
	}

	void installModules(List<AnonymousGraphCollection> modules) throws IOException {
		if (modules.isEmpty())
			return;

		for (AnonymousGraphCollection module : modules) {
			maximalSubgraphs.addAll(module.subgraphs);
		}
		analyzeModules();
	}

	private void analyzeModules() {
		Collections.sort(maximalSubgraphs, new SizeOrder());

		Set<ApplicationModule> allConnectingModules = new HashSet<ApplicationModule>();
		ApplicationModule owningModule;
		Set<ApplicationModule> ambiguousOwnerSet = new HashSet<ApplicationModule>();
		subgraphs: for (AnonymousGraph subgraph : maximalSubgraphs) {
			ApplicationModule module;
			boolean ambiguousOwner = false;
			ambiguousOwnerSet.clear();
			owningModule = null;
			allConnectingModules.clear();
			if (subgraph.getEntryPoints().isEmpty() && !subgraph.isJIT()) {
				Log.log("Error: entry point missing for anonymous subgraph of %d nodes in %s!",
						subgraph.getNodeCount(), name);
				Log.log("\tOmitting this subgraph from the merge.");
				// subgraph.logGraph();
				continue subgraphs;
			}

			for (ModuleNode<?> entryPoint : subgraph.getEntryPoints()) {
				OrdinalEdgeList<?> edges = entryPoint.getOutgoingEdges();
				try {
					if (ApplicationModuleSet.getInstance().getClusterByAnonymousGencodeHash(entryPoint.getHash()) != null)
						continue; // no ownership by gencode, it's not reliable

					module = ApplicationModuleSet.getInstance().getClusterByAnonymousEntryHash(entryPoint.getHash());
					if (module == null) {
						module = ApplicationModuleSet.getInstance().getClusterByInterceptionHash(entryPoint.getHash());
					}
					if (module == null) {
						Log.log("Error: unrecognized entry point 0x%x for anonymous subgraph of %d nodes!",
								entryPoint.getHash(), subgraph.getNodeCount());
						Log.log("\tKeeping the edge but discarding this owner.");
						// Log.log("\tSubgraph is owned by %s", owningCluster);
						continue;
					}

					module = AnonymousGraphCollection.resolveAlias(module);

					allConnectingModules.add(module);

					if (AnonymousGraphCollection.isEligibleOwner(module)) {
						if (ambiguousOwner) {
							ambiguousOwnerSet.add(module);
						} else {
							if (owningModule == null) {
								owningModule = module;
							} else if (owningModule != module) {
								ambiguousOwner = true;
								ambiguousOwnerSet.add(owningModule);
								ambiguousOwnerSet.add(module);
							}
						}
					}
				} finally {
					edges.release();
				}
			}

			if (!ambiguousOwner) {
				for (ModuleNode<?> exitPoint : subgraph.getExitPoints()) {
					OrdinalEdgeList<?> edges = exitPoint.getIncomingEdges();
					try {
						// int leftTargetCount = 0, rightTargetCount = 0;
						module = ApplicationModuleSet.getInstance().getClusterByAnonymousExitHash(exitPoint.getHash());
						if (module == null) {
							// Log.log("     Callout 0x%x (%s) to an exported function", node.getHash());
							continue;
						}

						module = AnonymousGraphCollection.resolveAlias(module);
						allConnectingModules.add(module);

						if (AnonymousGraphCollection.isEligibleOwner(module)) {
							if (owningModule == null) {
								owningModule = module;
							}
						}
					} finally {
						edges.release();
					}
				}
			}

			if (ambiguousOwner) {
				StringBuilder buffer = new StringBuilder();
				for (ApplicationModule c : ambiguousOwnerSet) {
					buffer.append(c.getUnitFilename());
					buffer.append(", ");
				}
				buffer.setLength(buffer.length() - 2);
				Log.log("Error: subgraph of %d nodes has entry points from multiple modules: %s",
						subgraph.getNodeCount(), buffer);
				Log.log("\tOmitting this subgraph from the merge.");
			} else {
				if (owningModule == null) {
					if (allConnectingModules.size() == 1) {
						owningModule = allConnectingModules.iterator().next();
					} else {
						Log.log("Error: could not determine the owning module of an anonymous subgraph with %d nodes!",
								subgraph.getNodeCount());
						Log.log("\tPotential owners are: %s", allConnectingModules);
						Log.log("\tOmitting this subgraph from the merge.");
						continue subgraphs;
					}
				}

				AnonymousGraphCollection.OwnerKey key = new AnonymousGraphCollection.OwnerKey(owningModule,
						subgraph.isJIT());
				AnonymousGraphCollection module = modulesByOwner.get(key);
				if (module == null) {
					module = new AnonymousGraphCollection(owningModule);
					modulesByOwner.put(key, module);
				}
				module.addSubgraph(subgraph);
			}
		}
	}
}
