package edu.uci.eecs.crowdsafe.merge.graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.AnonymousGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.AnonymousGraphCollection;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.AnonymousModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ApplicationGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.merge.exception.MergedFailedException;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ContextMatchState;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashMatchedNodes;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashMergeDebugLog;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashMergeSession;

public class AnonymousGraphMergeEngine {

	private static class DynamicHashMatchEvaluator implements HashMergeSession.MergeEvaluator {
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
		public boolean attemptMerge(HashMergeSession session) {
			ModuleGraph<?> left = session.getLeft();
			ModuleGraph<?> right = session.getRight();

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
		public boolean acceptGraphs(HashMergeSession session) {
			if (isFailed)
				throw new IllegalStateException("Cannot evaluate a merge with a failed evaluator!");

			if (mergeMode) {
				if (session.isFailed())
					return false;

				return true;
			}

			ModuleGraph<?> left = session.getLeft();
			ModuleGraph<?> right = session.getRight();
			HashMatchedNodes matchedNodes = session.getMatchedNodes();

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

			// TODO: may want to tag subgraphs with an id representing the original anonymous module, to use as a hint

			exactMatch = false;
			int leftMatchPercentage = Math.round((matchedNodes.size() / (float) left.getNodeCount()) * 100f);
			int rightMatchPercentage = Math.round((matchedNodes.size() / (float) right.getNodeCount()) * 100f);
			greaterMatchPercentage = Math.max(leftMatchPercentage, rightMatchPercentage);

			return greaterMatchPercentage > 50;
		}
	}

	private static class SubgraphCollection {
		final int id = SUBGRAPH_ID_INDEX++;
		List<ModuleGraph<ModuleNode<?>>> graphs = new ArrayList<ModuleGraph<ModuleNode<?>>>();
		List<CompatibilityRecord> compatibilityRecords = new ArrayList<CompatibilityRecord>();

		public SubgraphCollection(ModuleGraph<ModuleNode<?>> graph) {
			graphs.add(graph);
		}

		void add(ModuleGraph<ModuleNode<?>> subgraph, CompatibilityRecord compatibility) {
			graphs.add(subgraph);
			compatibilityRecords.add(compatibility);
		}

		void reportCompatibility() {
			StringBuilder buffer = new StringBuilder(String.format("\tOriginal graph has %d nodes\n", graphs.get(0)
					.getExecutableNodeCount()));
			for (CompatibilityRecord compatibility : compatibilityRecords) {
				buffer.append(String.format("\tCompatibility of %d node graph: ", compatibility.subgraphSize));
				for (int i = 0; i < compatibility.comparisonSubgraphSizes.size(); i++) {
					buffer.append(String.format("(%d @ %d%%%%)", compatibility.comparisonSubgraphSizes.get(i),
							compatibility.mergeScores.get(i)));
				}
				buffer.append("\n");
			}
			// Log.log(buffer.toString());
		}

		CompatibilityRecord getCompatibilityRecord(int subgraphIndex) {
			return compatibilityRecords.get(subgraphIndex - 1);
		}
	}

	private static class CompatibilityRecord {
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

	private static class SizeOrder implements Comparator<SubgraphCollection> {
		@Override
		public int compare(SubgraphCollection first, SubgraphCollection second) {
			int comparison = second.graphs.size() - first.graphs.size();
			if (comparison != 0)
				return comparison;

			return (int) second.hashCode() - (int) first.hashCode();
		}
	}

	private static int SUBGRAPH_ID_INDEX = 0;

	private final AnonymousModuleSet leftModuleSet;
	private final AnonymousModuleSet rightModuleSet;

	private final DynamicHashMatchEvaluator dynamicEvaluator = new DynamicHashMatchEvaluator();
	private final HashMergeDebugLog debugLog;

	public AnonymousGraphMergeEngine(GraphMergeCandidate leftData, GraphMergeCandidate rightData,
			HashMergeDebugLog debugLog) {
		leftModuleSet = new AnonymousModuleSet(leftData);
		rightModuleSet = new AnonymousModuleSet(rightData);
		this.debugLog = debugLog;
	}

	public ApplicationGraph createAnonymousGraph(List<ModuleGraph<ModuleNode<?>>> leftAnonymousGraphs,
			List<ModuleGraph<ModuleNode<?>>> rightAnonymousGraphs) throws IOException {

		leftModuleSet.installSubgraphs(GraphMergeSource.LEFT, leftAnonymousGraphs);
		leftModuleSet.analyzeModules();
		// leftModuleSet.printDotFiles();

		rightModuleSet.installSubgraphs(GraphMergeSource.RIGHT, rightAnonymousGraphs);
		rightModuleSet.analyzeModules();
		// rightModuleSet.printDotFiles();

		List<AnonymousGraphCollection> mergedModules = new ArrayList<AnonymousGraphCollection>();
		for (AnonymousGraphCollection.OwnerKey leftOwner : leftModuleSet.getModuleOwners()) {
			AnonymousGraphCollection leftModule = leftModuleSet.getModule(leftOwner);
			AnonymousGraphCollection rightModule = rightModuleSet.getModule(leftOwner);

			if (leftOwner.isJIT) {
				if (rightModule != null) {
					mergeJITs(leftModule, rightModule);
				}
				mergedModules.add(leftModule); // nothing to compile
			} else {
				AnonymousGraphCollection mergedModule = new AnonymousGraphCollection(leftOwner.module);
				if (rightModule != null) {
					compileStandalones(rightModule, mergedModule);
				}
				compileStandalones(leftModule, mergedModule);
				mergedModules.add(mergedModule);
			}
		}
		for (AnonymousGraphCollection.OwnerKey rightOwner : rightModuleSet.getModuleOwners()) {
			AnonymousGraphCollection leftModule = leftModuleSet.getModule(rightOwner);
			if (leftModule == null) {// otherwise it was merged above
				if (rightOwner.isJIT) {
					mergedModules.add(rightModuleSet.getModule(rightOwner)); // nothing to compile
				} else {
					AnonymousGraphCollection mergedModule = new AnonymousGraphCollection(rightOwner.module);
					compileStandalones(rightModuleSet.getModule(rightOwner), mergedModule);
					mergedModules.add(mergedModule);
				}
			}
		}

		// this step is just for logging
		Log.log("\n     ========== Merged Anonymous Graph ==========\n");
		AnonymousModuleSet mergedModuleSet = new AnonymousModuleSet("<merge>");
		mergedModuleSet.installModules(mergedModules);
		mergedModuleSet.analyzeModules();

		ApplicationGraph anonymousGraph = compileAnonymousGraph(mergedModules);
		return anonymousGraph;
	}

	// add to the left module all entry points and exit points that are unique to the right
	private void mergeJITs(AnonymousGraphCollection leftModule, AnonymousGraphCollection rightModule) {
		if (leftModule.subgraphs.size() != 1) {
			throw new InvalidGraphException("Black box has %d modules, but exactly one is required.",
					leftModule.subgraphs.size());
		}
		if (rightModule.subgraphs.size() != 1) {
			int index = 0;
			for (AnonymousGraph subgraph : rightModule.subgraphs) {
				Log.log("Subgraph %d has %d nodes (%d entry, %d exit)", index++, subgraph.getNodeCount(), subgraph
						.getEntryPoints().size(), subgraph.getExitPoints().size());
				for (ModuleNode<?> node : subgraph.getAllNodes()) {
					if (node.getType() != MetaNodeType.MODULE_ENTRY && node.getType() != MetaNodeType.MODULE_EXIT)
						Log.log("    Node type %s with hash 0x%x", node.getType(), node.getHash());
				}
			}
			throw new InvalidGraphException("Black box has %d modules, but exactly one is required.",
					rightModule.subgraphs.size());
		}

		AnonymousGraph leftBox = leftModule.subgraphs.get(0);
		AnonymousGraph rightBox = rightModule.subgraphs.get(0);
		ModuleNode<?> leftSingleton = leftBox.getJITSingleton();

		OrdinalEdgeList<?> leftIncoming = leftBox.getJITSingleton().getIncomingEdges();
		OrdinalEdgeList<?> rightIncoming = rightBox.getJITSingleton().getIncomingEdges();
		try {
			for (Edge<? extends Node<?>> rightEdge : rightIncoming) {
				if (!leftIncoming.contains(rightEdge)) {
					ModuleBoundaryNode newEntry = new ModuleBoundaryNode(rightEdge.getFromNode().getHash(), rightEdge
							.getFromNode().getType());
					leftBox.addNode(newEntry);

					Edge<ModuleNode<?>> mergedEdge = new Edge<ModuleNode<?>>(newEntry, leftSingleton,
							rightEdge.getEdgeType(), rightEdge.getOrdinal());
					newEntry.addOutgoingEdge(mergedEdge);
					leftSingleton.addIncomingEdge(mergedEdge);
				}
			}
		} finally {
			rightIncoming.release();
		}

		OrdinalEdgeList<?> leftOutgoing = leftBox.getJITSingleton().getOutgoingEdges();
		OrdinalEdgeList<?> rightOutgoing = rightBox.getJITSingleton().getOutgoingEdges();
		try {
			for (Edge<? extends Node<?>> rightEdge : rightOutgoing) {
				if (!leftOutgoing.contains(rightEdge)) {
					ModuleBoundaryNode newExit = new ModuleBoundaryNode(rightEdge.getToNode().getHash(), rightEdge
							.getToNode().getType());
					leftBox.addNode(newExit);

					Edge<ModuleNode<?>> mergedEdge = new Edge<ModuleNode<?>>(leftSingleton, newExit,
							rightEdge.getEdgeType(), rightEdge.getOrdinal());
					newExit.addIncomingEdge(mergedEdge);
					leftSingleton.addOutgoingEdge(mergedEdge);
				}
			}
		} finally {
			rightOutgoing.release();
		}
	}

	private void compileStandalones(AnonymousGraphCollection inputModule, AnonymousGraphCollection mergedModule) {
		for (AnonymousGraph inputSubgraph : inputModule.subgraphs) { // could skip this if right is a dataset
			boolean match = false;
			AnonymousGraph replace = null;
			for (AnonymousGraph mergedSubgraph : mergedModule.subgraphs) {
				HashMergeSession.evaluateTwoGraphs(inputSubgraph, mergedSubgraph, dynamicEvaluator, debugLog);
				if (dynamicEvaluator.exactMatch) {
					if (inputSubgraph.getNodeCount() > mergedSubgraph.getNodeCount())
						replace = mergedSubgraph;
					else
						match = true;
					break;
				}
			}
			if (match) {
				// Log.log("White box duplicate %s#%d from the %s side omittted.",
				// inputSubgraph.module.filename, inputSubgraph.id, inputSubgraph.source);
			} else {
				// Log.log("White box %s#%d from the %s side included.", inputSubgraph.module.filename,
				// inputSubgraph.id, inputSubgraph.source);
				if (replace != null)
					mergedModule.replaceSubgraph(replace, inputSubgraph);
				else
					mergedModule.addSubgraph(inputSubgraph);
			}
		}
	}

	private ApplicationGraph compileAnonymousGraph(List<AnonymousGraphCollection> mergedModules) {
		ApplicationGraph compiledGraph = new ApplicationGraph("Compiled anonymous module",
				ApplicationModule.ANONYMOUS_MODULE);
		Map<ModuleNode<?>, ModuleNode<?>> copyMap = new HashMap<ModuleNode<?>, ModuleNode<?>>();
		int fakeTagIndex = ModuleNode.SYSCALL_SINGLETON_END + 1;
		for (AnonymousGraphCollection module : mergedModules) {
			for (AnonymousGraph subgraph : module.subgraphs) {
				for (ModuleNode<?> node : subgraph.getAllNodes()) {
					int relativeTag = node.getRelativeTag();
					if (relativeTag > ModuleNode.JIT_SINGLETON_END)
						relativeTag = fakeTagIndex++;
					ModuleNode<?> copy = compiledGraph.addNode(node.getHash(), ApplicationModule.ANONYMOUS_MODULE,
							relativeTag, node.getType());
					copyMap.put(node, copy);
				}

				for (ModuleNode<?> node : subgraph.getAllNodes()) {
					OrdinalEdgeList<?> edges = node.getOutgoingEdges();
					try {
						for (Edge<? extends Node<?>> edge : edges) {
							ModuleNode<?> fromNode = copyMap.get(edge.getFromNode());
							ModuleNode<?> toNode = copyMap.get(edge.getToNode());
							if (toNode == null) {
								throw new MergedFailedException("Error! Missing copy of 'to' node %s in edge from %s",
										edge.getToNode(), edge.getFromNode());
							}
							Edge<ModuleNode<?>> mergedEdge = new Edge<ModuleNode<?>>(fromNode, toNode,
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
		}
		return compiledGraph;
	}
}
