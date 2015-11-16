package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeCandidate;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeSource;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousModule;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousSubgraph;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeDebugLog;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeSession;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ContextMatchState;
import edu.uci.eecs.crowdsafe.merge.graph.hash.HashMatchedNodes;

public class AnonymousModuleReportGenerator {

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
			// Log.log(buffer.toString());
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

	private static class SizeOrder implements Comparator<SubgraphCluster> {
		@Override
		public int compare(SubgraphCluster first, SubgraphCluster second) {
			int comparison = second.graphs.size() - first.graphs.size();
			if (comparison != 0)
				return comparison;

			return (int) second.hashCode() - (int) first.hashCode();
		}
	}

	public static void addAnonymousReportEntries(ExecutionReport report, GraphMergeCandidate leftData,
			GraphMergeCandidate rightData, List<ModuleGraphCluster<ClusterNode<?>>> execution,
			List<ModuleGraphCluster<ClusterNode<?>>> dataset) {
		AnonymousModuleReportGenerator generator = new AnonymousModuleReportGenerator(report, leftData, rightData,
				execution, dataset);
		generator.addEntries();
	}

	private static int SUBGRAPH_ID_INDEX = 0;

	private final DynamicHashMatchEvaluator dynamicEvaluator = new DynamicHashMatchEvaluator();

	private final ExecutionReport report;
	private final GraphMergeCandidate leftData;
	private final GraphMergeCandidate rightData;

	private final List<ModuleGraphCluster<ClusterNode<?>>> execution;
	private final List<ModuleGraphCluster<ClusterNode<?>>> dataset;
	private final AnonymousModuleReportSet leftModuleSet;
	private final AnonymousModuleReportSet rightModuleSet;

	private AnonymousModuleReportGenerator(ExecutionReport report, GraphMergeCandidate leftData,
			GraphMergeCandidate rightData, List<ModuleGraphCluster<ClusterNode<?>>> execution,
			List<ModuleGraphCluster<ClusterNode<?>>> dataset) {
		this.leftData = leftData;
		this.rightData = rightData;
		this.report = report;

		this.execution = execution;
		this.dataset = dataset;
		this.leftModuleSet = new AnonymousModuleReportSet("<left>");
		this.rightModuleSet = new AnonymousModuleReportSet("<right>");

		AnonymousModule.initialize();
	}

	private void addEntries() {
		leftModuleSet.installSubgraphs(GraphMergeSource.LEFT, execution);
		rightModuleSet.installSubgraphs(GraphMergeSource.RIGHT, dataset);

		List<AnonymousModule> mergedModules = new ArrayList<AnonymousModule>();
		for (AnonymousModule.OwnerKey leftOwner : leftModuleSet.getModuleOwners()) {
			AnonymousModule leftModule = leftModuleSet.getModule(leftOwner);
			AnonymousModule rightModule = rightModuleSet.getModule(leftOwner);

			if (leftOwner.isBlackBox) {
				if (rightModule != null) {
					mergeBlackBoxes(leftModule, rightModule);
				}
				mergedModules.add(leftModule); // nothing to compile
			} else {
				AnonymousModule mergedModule = new AnonymousModule(leftOwner.cluster);
				if (rightModule != null) {
					compileWhiteBoxes(rightModule, mergedModule, false);
				}
				compileWhiteBoxes(leftModule, mergedModule, true);
				mergedModules.add(mergedModule);
			}
		}

		compileAnonymousGraph(mergedModules);
	}

	// add to the left module all entry points and exit points that are unique to the right
	private void mergeBlackBoxes(AnonymousModule leftModule, AnonymousModule rightModule) {
		if (leftModule.subgraphs.size() != 1)
			throw new InvalidGraphException("Black box has %d modules, but exactly one is required.",
					leftModule.subgraphs.size());
		if (rightModule.subgraphs.size() != 1)
			throw new InvalidGraphException("Black box has %d modules, but exactly one is required.",
					rightModule.subgraphs.size());

		AnonymousSubgraph leftBox = leftModule.subgraphs.get(0);
		AnonymousSubgraph rightBox = rightModule.subgraphs.get(0);
		ClusterNode<?> leftSingleton = leftBox.getBlackBoxSingleton();

		OrdinalEdgeList<?> leftIncoming = leftBox.getBlackBoxSingleton().getIncomingEdges();
		OrdinalEdgeList<?> rightIncoming = rightBox.getBlackBoxSingleton().getIncomingEdges();
		try {
			for (Edge<? extends Node<?>> rightEdge : rightIncoming) {
				if (!leftIncoming.contains(rightEdge)) {
					ClusterBoundaryNode newEntry = new ClusterBoundaryNode(rightEdge.getFromNode().getHash(), rightEdge
							.getFromNode().getType());
					leftBox.addNode(newEntry);

					Edge<ClusterNode<?>> mergedEdge = new Edge<ClusterNode<?>>(newEntry, leftSingleton,
							rightEdge.getEdgeType(), rightEdge.getOrdinal());
					newEntry.addOutgoingEdge(mergedEdge);
					leftSingleton.addIncomingEdge(mergedEdge);
				}
			}
		} finally {
			rightIncoming.release();
		}

		OrdinalEdgeList<?> leftOutgoing = leftBox.getBlackBoxSingleton().getOutgoingEdges();
		OrdinalEdgeList<?> rightOutgoing = rightBox.getBlackBoxSingleton().getOutgoingEdges();
		try {
			for (Edge<? extends Node<?>> rightEdge : rightOutgoing) {
				if (!leftOutgoing.contains(rightEdge)) {
					ClusterBoundaryNode newExit = new ClusterBoundaryNode(rightEdge.getToNode().getHash(), rightEdge
							.getToNode().getType());
					leftBox.addNode(newExit);

					Edge<ClusterNode<?>> mergedEdge = new Edge<ClusterNode<?>>(leftSingleton, newExit,
							rightEdge.getEdgeType(), rightEdge.getOrdinal());
					newExit.addIncomingEdge(mergedEdge);
					leftSingleton.addOutgoingEdge(mergedEdge);
				}
			}
		} finally {
			rightOutgoing.release();
		}
	}

	private void compileWhiteBoxes(AnonymousModule inputModule, AnonymousModule mergedModule, boolean report) {
		for (AnonymousSubgraph inputSubgraph : inputModule.subgraphs) {
			boolean match = false;
			for (AnonymousSubgraph mergedSubgraph : mergedModule.subgraphs) {
				ClusterHashMergeSession.evaluateTwoGraphs(inputSubgraph, mergedSubgraph, dynamicEvaluator,
						new ClusterHashMergeDebugLog());
				if (dynamicEvaluator.exactMatch) {
					match = true;
					break;
				}
			}
			if (!match) {
				if (report) {
					Log.log("Add dynamic standalone of size %d nodes owned by %s",
							inputSubgraph.getExecutableNodeCount(), inputModule.owningCluster.name);
				}
				mergedModule.addSubgraph(inputSubgraph);
			}
		}
	}

	private void compileAnonymousGraph(List<AnonymousModule> mergedModules) {
		ClusterGraph compiledGraph = new ClusterGraph("Compiled anonymous cluster",
				ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER);
		Map<ClusterNode<?>, ClusterNode<?>> copyMap = new HashMap<ClusterNode<?>, ClusterNode<?>>();
		int fakeTagIndex = ClusterNode.SYSCALL_SINGLETON_END + 1;
		for (AnonymousModule module : mergedModules) {
			for (AnonymousSubgraph subgraph : module.subgraphs) {
				for (ClusterNode<?> node : subgraph.getAllNodes()) {
					int relativeTag = node.getRelativeTag();
					if (relativeTag > ClusterNode.BLACK_BOX_SINGLETON_END)
						relativeTag = fakeTagIndex++;
					ClusterNode<?> copy = compiledGraph.addNode(node.getHash(), SoftwareModule.ANONYMOUS_MODULE,
							relativeTag, node.getType());
					copyMap.put(node, copy);
				}

				for (ClusterNode<?> node : subgraph.getAllNodes()) {
					OrdinalEdgeList<?> edges = node.getOutgoingEdges();
					try {
						for (Edge<? extends Node<?>> edge : edges) {
							ClusterNode<?> fromNode = copyMap.get(edge.getFromNode());
							ClusterNode<?> toNode = copyMap.get(edge.getToNode());
							if (toNode == null) {
								Log.log("Error! Missing copy of 'to' node %s in edge from %s", edge.getToNode(),
										edge.getFromNode());
								continue;
							}
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
		}
	}
}
