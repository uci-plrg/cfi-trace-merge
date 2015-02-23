package edu.uci.eecs.crowdsafe.merge.graph.anonymous;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeSource;

public class MaximalSubgraphs {

	private class Subgraph {
		final AnonymousSubgraph graph = new AnonymousSubgraph("Anonymous maximal subgraph of " + source.label
				+ " cluster " + originalGraph.cluster.name, source, originalGraph.cluster);
		final Map<Long, ClusterBoundaryNode> boundaryNodes = new HashMap<Long, ClusterBoundaryNode>();

		ClusterNode<?> addNode(ClusterNode<?> node, Edge<ClusterNode<?>> edge) {
			if (node.getType() == MetaNodeType.CLUSTER_ENTRY) {
				ClusterBoundaryNode existingEntry = boundaryNodes.get(node.getHash());
				if (edge == null) {
					if (existingEntry == null) {
						boundaryNodes.put(node.getHash(), (ClusterBoundaryNode) node);
						graph.addNode(node);
						return node;
					} else {
						List<Edge<ClusterNode<?>>> patchEdges = new ArrayList<Edge<ClusterNode<?>>>();
						OrdinalEdgeList<ClusterNode<?>> edgeList = node.getOutgoingEdges();
						try {
							for (Edge<ClusterNode<?>> entryEdge : edgeList) {
								Edge<ClusterNode<?>> patchEdge = new Edge<ClusterNode<?>>(existingEntry,
										entryEdge.getToNode(), entryEdge.getEdgeType(), entryEdge.getOrdinal());
								patchEdges.add(patchEdge);
								entryEdge.getToNode().replaceEdge(edge, patchEdge);
							}
						} finally {
							edgeList.release();
						}

						for (Edge<ClusterNode<?>> patchEdge : patchEdges)
							existingEntry.addOutgoingEdge(patchEdge);

						return existingEntry;
					}
				} else {
					if (existingEntry == null) {
						existingEntry = new ClusterBoundaryNode(node.getHash(), MetaNodeType.CLUSTER_ENTRY);
						graph.addNode(existingEntry);
						boundaryNodes.put(existingEntry.getHash(), existingEntry);
					}

					Edge<ClusterNode<?>> patchEdge = new Edge<ClusterNode<?>>(existingEntry, edge.getToNode(),
							edge.getEdgeType(), edge.getOrdinal());
					existingEntry.addOutgoingEdge(patchEdge);
					edge.getToNode().replaceEdge(edge, patchEdge);
					return existingEntry;
				}
			} else if (node.getType() == MetaNodeType.CLUSTER_EXIT) {
				ClusterBoundaryNode existingExit = boundaryNodes.get(node.getHash());
				if (edge == null) {
					if (existingExit == null) {
						boundaryNodes.put(node.getHash(), (ClusterBoundaryNode) node);
						graph.addNode(node);
						return node;
					} else {
						List<Edge<ClusterNode<?>>> patchEdges = new ArrayList<Edge<ClusterNode<?>>>();
						OrdinalEdgeList<ClusterNode<?>> edgeList = node.getIncomingEdges();
						try {
							for (Edge<ClusterNode<?>> exitEdge : edgeList) {
								Edge<ClusterNode<?>> patchEdge = new Edge<ClusterNode<?>>(exitEdge.getFromNode(),
										existingExit, exitEdge.getEdgeType(), exitEdge.getOrdinal());
								patchEdges.add(patchEdge);
								exitEdge.getFromNode().replaceEdge(exitEdge, patchEdge);
							}
						} finally {
							edgeList.release();
						}

						for (Edge<ClusterNode<?>> patchEdge : patchEdges)
							existingExit.addIncomingEdge(patchEdge);

						return existingExit;
					}
				} else {
					if (existingExit == null) {
						existingExit = new ClusterBoundaryNode(node.getHash(), MetaNodeType.CLUSTER_EXIT);
						graph.addNode(existingExit);
						boundaryNodes.put(existingExit.getHash(), existingExit);
					}

					Edge<ClusterNode<?>> patchEdge = new Edge<ClusterNode<?>>(edge.getFromNode(), existingExit,
							edge.getEdgeType(), edge.getOrdinal());
					existingExit.addIncomingEdge(patchEdge);
					edge.getFromNode().replaceEdge(edge, patchEdge);
					return existingExit;
				}
			} else {
				graph.addNode(node);
				return node;
			}
		}
	}

	// this method modifies `graph`
	public static Set<AnonymousSubgraph> getMaximalSubgraphs(GraphMergeSource source,
			ModuleGraphCluster<ClusterNode<?>> graph) {
		MaximalSubgraphs processor = new MaximalSubgraphs(source, graph);

		for (ClusterNode<?> node : graph.getAllNodes()) {
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
		ClusterNode<?> fromNode = consumeFromAtom(edge);
		if (fromNode != null) {
			ClusterNode<?> toNode;
			if (edge.getFromNode() == edge.getToNode())
				toNode = fromNode;
			else
				toNode = consumeToAtom(edge);
			if (toNode != null) {
				Subgraph subgraph = addSubgraph();
				fromNode = subgraph.addNode(fromNode, edge);
				toNode = subgraph.addNode(toNode, edge);
				subgraphs.put(fromNode, subgraph);
				subgraphs.put(toNode, subgraph);
			} else {
				Subgraph subgraph = subgraphs.get(edge.getToNode());
				fromNode = subgraph.addNode(fromNode, edge);
				subgraphs.put(fromNode, subgraph);
			}
		} else {
			ClusterNode<?> toNode = consumeToAtom(edge);
			if (toNode != null) {
				Subgraph subgraph = subgraphs.get(edge.getFromNode());
					toNode = subgraph.addNode(toNode, edge);
				subgraphs.put(toNode, subgraph);
			} else {
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
						largeSubgraph.addNode(node, null);
					}
					for (ClusterNode<?> node : smallSubgraph.graph.getAllNodes()) {
						subgraphs.put(node, largeSubgraph);
					}
					removeSubgraph(smallSubgraph);
				}
			}
		}
	}

	private ClusterNode<?> consumeFromAtom(Edge<ClusterNode<?>> edge) {
		ClusterNode<?> fromNode = edge.getFromNode();
		if (fromNode.getType() != MetaNodeType.CLUSTER_ENTRY) {
			if (!atoms.remove(fromNode))
				return null;
		}
		return fromNode;
	}

	private ClusterNode<?> consumeToAtom(Edge<ClusterNode<?>> edge) {
		ClusterNode<?> toNode = edge.getToNode();
		if (toNode.getType() != MetaNodeType.CLUSTER_EXIT) {
			if (!atoms.remove(toNode))
				return null;
		}
		return toNode;
	}

	private Subgraph addSubgraph() {
		Subgraph subgraph = new Subgraph();
		for (ModuleGraph moduleGraph : originalGraph.getGraphs()) {
			subgraph.graph.addModule(moduleGraph);
		}
		distinctSubgraphs.add(subgraph.graph);
		return subgraph;
	}

	private void removeSubgraph(Subgraph subgraph) {
		distinctSubgraphs.remove(subgraph.graph);
	}
}
