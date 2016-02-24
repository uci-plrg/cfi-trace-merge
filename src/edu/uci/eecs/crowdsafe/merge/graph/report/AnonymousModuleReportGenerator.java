package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.AnonymousGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.ModuleAnonymousGraphs;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ApplicationGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeCandidate;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeSource;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashMergeDebugLog;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashMergeSession;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ContextMatchState;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashMatchedNodes;

public class AnonymousModuleReportGenerator {

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
				// if ((left.getExecutableNodeCount() < 35) && (right.getExecutableNodeCount() < 35)) {
				// if (reportCount < 50) {
				// Log.log("Evaluate subgraphs of %d and %d nodes: %d%% | exact? %b | failed? %b",
				// left.getNodeCount(), right.getNodeCount(), greaterMatchPercentage, exactMatch, isFailed);
				// left.logGraph();
				// right.logGraph();
				// reportCount++;
				// }
				// } else {
				// Log.log("%d%% | exact? %b | failed? %b", greaterMatchPercentage, exactMatch, isFailed);
				// }

				// if ((greaterMatchPercentage > 50) && (greaterMatchPercentage < 80))
				// toString();
				// left.logGraph();
				// right.logGraph();
			}
		}
	}

	private static class SubgraphCluster {
		final int id = SUBGRAPH_ID_INDEX++;
		List<ModuleGraph<ModuleNode<?>>> graphs = new ArrayList<ModuleGraph<ModuleNode<?>>>();
		List<CompatibilityRecord> compatibilityRecords = new ArrayList<CompatibilityRecord>();

		public SubgraphCluster(ModuleGraph<ModuleNode<?>> graph) {
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

	private static class SizeOrder implements Comparator<SubgraphCluster> {
		@Override
		public int compare(SubgraphCluster first, SubgraphCluster second) {
			if (first == second)
				return 0;

			int comparison = second.graphs.size() - first.graphs.size();
			if (comparison != 0)
				return comparison;

			if (first.hashCode() < second.hashCode())
				return -1;
			else
				return 1;
		}
	}

	private static class DescendingSizeSorter implements Comparator<AnonymousGraph> {
		static final DescendingSizeSorter INSTANCE = new DescendingSizeSorter();

		@Override
		public int compare(AnonymousGraph first, AnonymousGraph second) {
			if (first == second)
				return 0;

			int result = second.getNodeCount() - first.getNodeCount();
			if (result != 0)
				return result;

			if (first.hashCode() < second.hashCode())
				return -1;
			else
				return 1;
		}
	}

	public static void addAnonymousReportEntries(ExecutionReport report, GraphMergeCandidate leftData,
			GraphMergeCandidate rightData, List<ModuleGraph<ModuleNode<?>>> execution,
			List<ModuleGraph<ModuleNode<?>>> dataset) {
		AnonymousModuleReportGenerator generator = new AnonymousModuleReportGenerator(report, leftData, rightData,
				execution, dataset);
		generator.addEntries();
	}

	private static int SUBGRAPH_ID_INDEX = 0;

	private final DynamicHashMatchEvaluator dynamicEvaluator = new DynamicHashMatchEvaluator();

	private final ExecutionReport report;
	private final GraphMergeCandidate leftData;
	private final GraphMergeCandidate rightData;

	private final List<ModuleGraph<ModuleNode<?>>> execution;
	private final List<ModuleGraph<ModuleNode<?>>> dataset;

	// private final AnonymousModuleReportSet leftModuleSet;
	// private final AnonymousModuleReportSet rightModuleSet;

	private AnonymousModuleReportGenerator(ExecutionReport report, GraphMergeCandidate leftData,
			GraphMergeCandidate rightData, List<ModuleGraph<ModuleNode<?>>> execution,
			List<ModuleGraph<ModuleNode<?>>> dataset) {
		this.leftData = leftData;
		this.rightData = rightData;
		this.report = report;

		this.execution = execution;
		this.dataset = dataset;
		// this.leftModuleSet = new AnonymousModuleReportSet("<left>");
		// this.rightModuleSet = new AnonymousModuleReportSet("<right>");
	}

	private void addEntries() {
		// leftModuleSet.installSubgraphs(GraphMergeSource.LEFT, execution);
		// rightModuleSet.installSubgraphs(GraphMergeSource.RIGHT, dataset);

		List<ModuleAnonymousGraphs> mergedModules = new ArrayList<ModuleAnonymousGraphs>();
		List<ModuleAnonymousGraphs.OwnerKey> empty = null;
		for (ModuleAnonymousGraphs.OwnerKey leftOwner : empty) { // leftModuleSet.getModuleOwners()) {
			ModuleAnonymousGraphs leftModule = null; // leftModuleSet.getModule(leftOwner);
			ModuleAnonymousGraphs rightModule = null; // rightModuleSet.getModule(leftOwner);

			if (leftOwner.isJIT) {
				if (rightModule != null) {
					mergeJITs(leftModule, rightModule);
				}
				mergedModules.add(leftModule); // nothing to compile
			} else {
				ModuleAnonymousGraphs mergedModule = new ModuleAnonymousGraphs(leftOwner.module);
				if (rightModule != null) {
					compileStandalones(rightModule, mergedModule, false);
				} else {
					Log.log("Dataset has no anonymous module owned by %s", leftOwner.module.filename);
				}
				compileStandalones(leftModule, mergedModule, true);
				mergedModules.add(mergedModule);
			}
		}

		// compileAnonymousGraph(mergedModules);
	}

	private void mergeJITs(ModuleAnonymousGraphs executionModule, ModuleAnonymousGraphs datasetModule) {
		if (executionModule.subgraphs.size() != 1)
			throw new InvalidGraphException("Black box has %d modules, but exactly one is required.",
					executionModule.subgraphs.size());
		if (datasetModule.subgraphs.size() != 1)
			throw new InvalidGraphException("Black box has %d modules, but exactly one is required.",
					datasetModule.subgraphs.size());

		AnonymousGraph executionBox = executionModule.subgraphs.get(0);
		AnonymousGraph datasetBox = datasetModule.subgraphs.get(0);
		ModuleNode<?> executionSingleton = executionBox.getJITSingleton();

		OrdinalEdgeList<?> executionIncoming = executionBox.getJITSingleton().getIncomingEdges();
		OrdinalEdgeList<?> datasetIncoming = datasetBox.getJITSingleton().getIncomingEdges();
		try {
			for (Edge<? extends Node<?>> executionEdge : executionIncoming) {
				if (!datasetIncoming.contains(executionEdge)) {
					ModuleBoundaryNode newEntry = new ModuleBoundaryNode(executionEdge.getFromNode().getHash(),
							executionEdge.getFromNode().getType());
					datasetBox.addNode(newEntry);

					Edge<ModuleNode<?>> mergedEdge = new Edge<ModuleNode<?>>(newEntry, executionSingleton,
							executionEdge.getEdgeType(), executionEdge.getOrdinal());
					newEntry.addOutgoingEdge(mergedEdge);
					executionSingleton.addIncomingEdge(mergedEdge);
					report.addEntry(new NewEdgeReport(mergedEdge));
				}
			}
		} finally {
			executionIncoming.release();
		}

		OrdinalEdgeList<?> executionOutgoing = executionBox.getJITSingleton().getOutgoingEdges();
		OrdinalEdgeList<?> datasetOutgoing = datasetBox.getJITSingleton().getOutgoingEdges();
		try {
			for (Edge<? extends Node<?>> executionEdge : executionOutgoing) {
				if (!datasetOutgoing.contains(executionEdge)) {
					ModuleBoundaryNode newExit = new ModuleBoundaryNode(executionEdge.getToNode().getHash(),
							executionEdge.getToNode().getType());
					executionBox.addNode(newExit);

					Edge<ModuleNode<?>> mergedEdge = new Edge<ModuleNode<?>>(executionSingleton, newExit,
							executionEdge.getEdgeType(), executionEdge.getOrdinal());
					newExit.addIncomingEdge(mergedEdge);
					executionSingleton.addOutgoingEdge(mergedEdge);
					report.addEntry(new NewEdgeReport(mergedEdge));
				}
			}
		} finally {
			executionOutgoing.release();
		}
	}

	private void compileStandalones(ModuleAnonymousGraphs inputModule, ModuleAnonymousGraphs mergedModule,
			boolean reportNew) {
		HashMergeDebugLog ignoreDebug = new HashMergeDebugLog();
		List<AnonymousGraph> descendingSizeInputSubgraphs = new ArrayList<AnonymousGraph>(inputModule.subgraphs);
		Collections.sort(descendingSizeInputSubgraphs, DescendingSizeSorter.INSTANCE);
		for (AnonymousGraph inputSubgraph : descendingSizeInputSubgraphs) {
			boolean match = false;
			AnonymousGraph replace = null;
			for (AnonymousGraph mergedSubgraph : mergedModule.subgraphs) {
				HashMergeSession.evaluateTwoGraphs(inputSubgraph, mergedSubgraph, dynamicEvaluator, ignoreDebug);
				if (dynamicEvaluator.exactMatch) {
					if (inputSubgraph.getNodeCount() > mergedSubgraph.getNodeCount()) {
						replace = mergedSubgraph;
					} else {
						match = true;
					}
					break;
				}
			}
			if (!match) {
				if (replace != null)
					mergedModule.replaceSubgraph(replace, inputSubgraph);
				else
					mergedModule.addSubgraph(inputSubgraph);
				if (reportNew) {
					if (replace != null) {
						report.addEntry(new NewStandaloneReport(inputModule, inputSubgraph, replace.getNodeCount()));
					} else {
						report.addEntry(new NewStandaloneReport(inputModule, inputSubgraph));
					}
				}
			}
		}
	}

	private void compileAnonymousGraph(List<ModuleAnonymousGraphs> mergedModules) {
		ApplicationGraph compiledGraph = new ApplicationGraph("Compiled anonymous graph",
				ApplicationModule.ANONYMOUS_MODULE);
		Map<ModuleNode<?>, ModuleNode<?>> copyMap = new HashMap<ModuleNode<?>, ModuleNode<?>>();
		int fakeTagIndex = ModuleNode.SYSCALL_SINGLETON_END + 1;
		for (ModuleAnonymousGraphs module : mergedModules) {
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
								Log.log("Error! Missing copy of 'to' node %s in edge from %s", edge.getToNode(),
										edge.getFromNode());
								continue;
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
	}
}
