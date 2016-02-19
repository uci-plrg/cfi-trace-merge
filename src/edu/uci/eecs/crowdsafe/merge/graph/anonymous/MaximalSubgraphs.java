package edu.uci.eecs.crowdsafe.merge.graph.anonymous;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeSource;

public class MaximalSubgraphs {

	private static class Subgraph {
		final AnonymousSubgraph graph;
		final Map<Long, ClusterBoundaryNode> boundaryNodes = new HashMap<Long, ClusterBoundaryNode>();

		public Subgraph(GraphMergeSource source, ModuleGraphCluster<ClusterNode<?>> originalGraph) {
			graph = new AnonymousSubgraph("Anonymous maximal subgraph of " + source.label + " cluster "
					+ originalGraph.cluster.name, source, originalGraph.cluster);
		}

		void addClusterBoundaryEdge(ClusterBoundaryNode node, Edge<ClusterNode<?>> edge) {
			ClusterBoundaryNode subgraphBoundaryNode = boundaryNodes.get(node.getHash());
			if (subgraphBoundaryNode == null) {
				subgraphBoundaryNode = new ClusterBoundaryNode(node.getHash(), node.getType());
				graph.addNode(subgraphBoundaryNode);
				boundaryNodes.put(subgraphBoundaryNode.getHash(), subgraphBoundaryNode);
			}

			switch (node.getType()) {
				case CLUSTER_ENTRY: {
					Edge<ClusterNode<?>> patchEdge = new Edge<ClusterNode<?>>(subgraphBoundaryNode, edge.getToNode(),
							edge.getEdgeType(), edge.getOrdinal());
					subgraphBoundaryNode.addOutgoingEdge(patchEdge);
					edge.getToNode().replaceEdge(edge, patchEdge);
					break;
				}
				case CLUSTER_EXIT: {
					Edge<ClusterNode<?>> patchEdge = new Edge<ClusterNode<?>>(edge.getFromNode(), subgraphBoundaryNode,
							edge.getEdgeType(), edge.getOrdinal());
					subgraphBoundaryNode.addIncomingEdge(patchEdge);
					edge.getFromNode().replaceEdge(edge, patchEdge);
					break;
				}
				default:
					; /* nothing */
			}
		}

		void mergeClusterBoundaryNode(ClusterBoundaryNode mergingSubgraphBoundaryNode) {
			ClusterBoundaryNode subgraphBoundaryNode = boundaryNodes.get(mergingSubgraphBoundaryNode.getHash());
			if (subgraphBoundaryNode == null) {
				graph.addNode(mergingSubgraphBoundaryNode);
				boundaryNodes.put(mergingSubgraphBoundaryNode.getHash(), mergingSubgraphBoundaryNode);
			} else {
				switch (mergingSubgraphBoundaryNode.getType()) {
					case CLUSTER_ENTRY: {
						OrdinalEdgeList<ClusterNode<?>> mergingEdgeList = mergingSubgraphBoundaryNode
								.getOutgoingEdges();
						try {
							for (Edge<ClusterNode<?>> mergingEntryEdge : mergingEdgeList) {
								Edge<ClusterNode<?>> replacement = new Edge<ClusterNode<?>>(subgraphBoundaryNode,
										mergingEntryEdge.getToNode(), mergingEntryEdge.getEdgeType(),
										mergingEntryEdge.getOrdinal());
								mergingEntryEdge.getToNode().replaceEdge(mergingEntryEdge, replacement);
								subgraphBoundaryNode.addOutgoingEdge(replacement);
							}
						} finally {
							mergingEdgeList.release();
						}
						break;
					}
					case CLUSTER_EXIT: {
						OrdinalEdgeList<ClusterNode<?>> mergingEdgeList = mergingSubgraphBoundaryNode
								.getIncomingEdges();
						try {
							for (Edge<ClusterNode<?>> mergingExitEdge : mergingEdgeList) {
								Edge<ClusterNode<?>> replacement = new Edge<ClusterNode<?>>(
										mergingExitEdge.getFromNode(), subgraphBoundaryNode,
										mergingExitEdge.getEdgeType(), mergingExitEdge.getOrdinal());
								mergingExitEdge.getFromNode().replaceEdge(mergingExitEdge, replacement);
								subgraphBoundaryNode.addIncomingEdge(replacement);
							}
						} finally {
							mergingEdgeList.release();
						}
						break;
					}
					default:
						; /* nothing */
				}
			}
		}

		void addFrontierEntryEdge(Edge<ClusterNode<?>> edge) {
			ClusterBoundaryNode globalEntryNode = (ClusterBoundaryNode) edge.getFromNode();
			ClusterBoundaryNode subgraphBoundaryNode = boundaryNodes.get(globalEntryNode.getHash());
			if (subgraphBoundaryNode == null) {
				subgraphBoundaryNode = new ClusterBoundaryNode(globalEntryNode.getHash(), globalEntryNode.getType());
				graph.addNode(subgraphBoundaryNode);
				boundaryNodes.put(globalEntryNode.getHash(), subgraphBoundaryNode);
			}

			Edge<ClusterNode<?>> replacement = new Edge<ClusterNode<?>>(subgraphBoundaryNode, edge.getToNode(),
					edge.getEdgeType(), edge.getOrdinal());
			edge.getToNode().replaceEdge(edge, replacement);
			subgraphBoundaryNode.addOutgoingEdge(replacement);
		}

		void addFrontierExitEdge(Edge<ClusterNode<?>> edge) {
			ClusterBoundaryNode globalExitNode = (ClusterBoundaryNode) edge.getToNode();
			ClusterBoundaryNode subgraphBoundaryNode = boundaryNodes.get(globalExitNode.getHash());
			if (subgraphBoundaryNode == null) {
				subgraphBoundaryNode = new ClusterBoundaryNode(globalExitNode.getHash(), globalExitNode.getType());
				graph.addNode(subgraphBoundaryNode);
				boundaryNodes.put(globalExitNode.getHash(), subgraphBoundaryNode);
			}

			Edge<ClusterNode<?>> replacement = new Edge<ClusterNode<?>>(edge.getFromNode(), subgraphBoundaryNode,
					edge.getEdgeType(), edge.getOrdinal());
			edge.getFromNode().replaceEdge(edge, replacement);
			subgraphBoundaryNode.addIncomingEdge(replacement);
		}
	}

	// this method modifies `graph`
	public static Set<AnonymousSubgraph> getMaximalSubgraphs(GraphMergeSource source,
			ModuleGraphCluster<ClusterNode<?>> graph) {
		MaximalSubgraphs processor = new MaximalSubgraphs(source, graph);

		for (ClusterNode<?> node : graph.getAllNodes()) {
			if (node.getType().isApplicationNode)
				processor.atoms.add(node);
		}

		for (ClusterNode<?> node : graph.getAllNodes()) {
			OrdinalEdgeList<ClusterNode<?>> edgeList = node.getOutgoingEdges();
			try {
				for (Edge<ClusterNode<?>> edge : edgeList) {
					processor.addEdge(edge);
				}
			} finally {
				edgeList.release();
			}
		}

		return processor.distinctSubgraphs;
	}

	private final GraphMergeSource source;
	private final ModuleGraphCluster<ClusterNode<?>> originalGraph;

	private final Set<ClusterNode<?>> atoms = new HashSet<ClusterNode<?>>();
	private final Map<ClusterNode<?>, Subgraph> subgraphs = new HashMap<ClusterNode<?>, Subgraph>();
	private final Set<AnonymousSubgraph> distinctSubgraphs = new HashSet<AnonymousSubgraph>();

	private MaximalSubgraphs(GraphMergeSource source, ModuleGraphCluster<ClusterNode<?>> graph) {
		this.source = source;
		this.originalGraph = graph;
	}

	private void addEdge(Edge<ClusterNode<?>> edge) {
		ClusterNode<?> fromAtom = consumeFromAtom(edge);
		ClusterNode<?> toAtom = consumeToAtom(edge);

		Log.log("Process edge: %s | from: %s | to: %s", edge, fromAtom, toAtom);

		if (edge.getFromNode().getType().isApplicationNode) {
			if (edge.getToNode().getType().isApplicationNode) {

				Log.log("\tBoth sides executable");

				if (fromAtom == null) {
					if (toAtom == null) {
						addConsumedEdge(edge);

						Log.log("\tAdd consumed edge");
					} else {
						Subgraph subgraph = subgraphs.get(edge.getFromNode());
						subgraph.graph.addNode(toAtom);
						subgraphs.put(toAtom, subgraph);

						Log.log("\tAttach <to> to <from>'s subgraph (0x%x)", subgraph.graph.hashCode());
					}
				} else {
					if (edge.getFromNode() == edge.getToNode())
						toAtom = fromAtom;
					if (toAtom == null) {
						Subgraph subgraph = subgraphs.get(edge.getToNode());
						subgraph.graph.addNode(fromAtom);
						subgraphs.put(fromAtom, subgraph);

						Log.log("\tAttach <from> to <to>'s subgraph (0x%x)", subgraph.graph.hashCode());
						subgraph.graph.logGraph();
					} else {
						addIsolatedEdge(edge);

						Log.log("\tAdd isolated edge");
					}
				}
			} else { /* cluster exit */
				Log.log("\tCluster exit");

				if (fromAtom == null) { /* already in a subgraph */
					Subgraph subgraph = subgraphs.get(edge.getFromNode());
					subgraph.addFrontierExitEdge(edge);

					Log.log("\tAdd frontier exit edge to 0x%x", subgraph.graph.hashCode());
				} else {
					addIsolatedEdge(edge);

					Log.log("\tAdd isolated edge");
				}
			}
		} else { /* cluster entry */
			Log.log("\tCluster entry");

			if (!edge.getToNode().getType().isApplicationNode)
				throw new InvalidGraphException("Cluster entry links directly to cluster exit:\n%s", edge);

			if (toAtom == null) { /* already in a subgraph */
				Subgraph subgraph = subgraphs.get(edge.getToNode());
				subgraph.addFrontierEntryEdge(edge);

				Log.log("\tAdd frontier entry edge to 0x%x", subgraph.graph.hashCode());
			} else {
				addIsolatedEdge(edge);

				Log.log("\tAdd isolated edge");
			}
		}
	}

	private void addIsolatedEdge(Edge<ClusterNode<?>> edge) {
		Subgraph subgraph = addSubgraph();
		ClusterNode<?> fromNode = edge.getFromNode();
		if (fromNode.getType().isApplicationNode) {
			subgraphs.put(fromNode, subgraph);
			subgraph.graph.addNode(fromNode);
		} else {
			subgraph.addClusterBoundaryEdge((ClusterBoundaryNode) fromNode, edge);
		}

		ClusterNode<?> toNode = edge.getToNode();
		if (toNode.getType().isApplicationNode) {
			subgraphs.put(toNode, subgraph);
			subgraph.graph.addNode(toNode);
		} else {
			subgraph.addClusterBoundaryEdge((ClusterBoundaryNode) toNode, edge);
		}
	}

	private void addConsumedEdge(Edge<ClusterNode<?>> edge) {
		Subgraph fromSubgraph = subgraphs.get(edge.getFromNode());
		Subgraph toSubgraph = subgraphs.get(edge.getToNode());
		if ((fromSubgraph != null) && (toSubgraph != null) && (fromSubgraph != toSubgraph)) {
			Subgraph smallSubgraph, largeSubgraph;
			if (fromSubgraph.graph.getNodeCount() < toSubgraph.graph.getNodeCount()) {
				smallSubgraph = fromSubgraph;
				largeSubgraph = toSubgraph;
			} else {
				smallSubgraph = toSubgraph;
				largeSubgraph = fromSubgraph;
			}
			for (ClusterNode<?> node : smallSubgraph.graph.getAllNodes()) {
				switch (node.getType()) {
					case CLUSTER_ENTRY:
					case CLUSTER_EXIT:
						largeSubgraph.mergeClusterBoundaryNode((ClusterBoundaryNode) node);
						break;
					default:
						largeSubgraph.graph.addNode(node);
						subgraphs.put(node, largeSubgraph);
				}
			}
			Log.log("Merged subgraph 0x%x into 0x%x", smallSubgraph.graph.hashCode(), largeSubgraph.graph.hashCode());
			Log.log("\tConsumed: ");
			smallSubgraph.graph.logGraph();
			Log.log("\tInto: ");
			largeSubgraph.graph.logGraph();
			distinctSubgraphs.remove(smallSubgraph.graph);
		}
	}

	private ClusterNode<?> consumeFromAtom(Edge<ClusterNode<?>> edge) {
		ClusterNode<?> fromNode = edge.getFromNode();
		if (!atoms.remove(fromNode))
			return null;
		return fromNode;
	}

	private ClusterNode<?> consumeToAtom(Edge<ClusterNode<?>> edge) {
		ClusterNode<?> toNode = edge.getToNode();
		if (!atoms.remove(toNode))
			return null;
		return toNode;
	}

	private Subgraph addSubgraph() {
		Subgraph subgraph = new Subgraph(source, originalGraph);
		for (ModuleGraph moduleGraph : originalGraph.getGraphs()) {
			subgraph.graph.addModule(new ModuleGraph(moduleGraph.softwareUnit));
		}
		Log.log("Adding subgraph 0x%x", subgraph.graph.hashCode());
		distinctSubgraphs.add(subgraph.graph);
		return subgraph;
	}
}
