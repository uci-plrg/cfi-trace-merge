package edu.uci.eecs.crowdsafe.merge.graph.hash;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;

public class MaximalSubgraphs {

	// this method modifies `graph`
	public static Set<ModuleGraphCluster<ClusterNode<?>>> getMaximalSubgraphs(ModuleGraphCluster<ClusterNode<?>> graph) {
		MaximalSubgraphs processor = new MaximalSubgraphs(graph);

		for (ClusterNode<?> node : graph.getAllNodes()) {
			processor.atoms.add(node);
		}

		MaximalSubgraphs.class.toString();

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

	private final ModuleGraphCluster<ClusterNode<?>> originalGraph;

	private final Set<ClusterNode<?>> atoms = new HashSet<ClusterNode<?>>();
	private final Map<ClusterNode<?>, ModuleGraphCluster<ClusterNode<?>>> subgraphs = new HashMap<ClusterNode<?>, ModuleGraphCluster<ClusterNode<?>>>();
	private final Set<ModuleGraphCluster<ClusterNode<?>>> distinctSubgraphs = new HashSet<ModuleGraphCluster<ClusterNode<?>>>();

	public MaximalSubgraphs(ModuleGraphCluster<ClusterNode<?>> graph) {
		this.originalGraph = graph;
	}

	private void addEdge(Edge<ClusterNode<?>> edge) {
		ClusterNode<?> fromNode = consumeFromAtom(edge);
		if (fromNode != null) {
			ClusterNode<?> toNode = consumeToAtom(edge);
			if (toNode != null) {
				ModuleGraphCluster<ClusterNode<?>> subgraph = addSubgraph();
				subgraph.addNode(fromNode);
				subgraph.addNode(toNode);
				subgraphs.put(fromNode, subgraph);
				subgraphs.put(toNode, subgraph);
			} else if (edge.getToNode().getType() != MetaNodeType.CLUSTER_EXIT) {
				ModuleGraphCluster<ClusterNode<?>> subgraph = subgraphs.get(edge.getToNode());
				if (subgraph != null) {
					subgraph.addNode(fromNode);
				} else {
					subgraph = addSubgraph();
					subgraph.addNode(fromNode);
				}
				subgraphs.put(fromNode, subgraph);
			}
		} else {
			ClusterNode<?> toNode = consumeToAtom(edge);
			if (toNode != null) {
				ModuleGraphCluster<ClusterNode<?>> subgraph = subgraphs.get(edge.getFromNode());
				if (subgraph != null) {
					subgraph.addNode(toNode);
				} else {
					subgraph = addSubgraph();
					subgraph.addNode(toNode);
				}
				subgraphs.put(toNode, subgraph);
			} else if ((edge.getFromNode().getType() != MetaNodeType.CLUSTER_ENTRY)
					&& (edge.getToNode().getType() != MetaNodeType.CLUSTER_EXIT)) {
				ModuleGraphCluster<ClusterNode<?>> fromSubgraph = subgraphs.get(edge.getFromNode());
				ModuleGraphCluster<ClusterNode<?>> toSubgraph = subgraphs.get(edge.getToNode());
				if ((fromSubgraph != null) && (toSubgraph != null) && (fromSubgraph != toSubgraph)) {
					ModuleGraphCluster<ClusterNode<?>> smallSubgraph, largeSubgraph;
					if (fromSubgraph.getNodeCount() < toSubgraph.getNodeCount()) {
						smallSubgraph = fromSubgraph;
						largeSubgraph = toSubgraph;
					} else {
						smallSubgraph = toSubgraph;
						largeSubgraph = fromSubgraph;
					}
					for (ClusterNode<?> node : smallSubgraph.getAllNodes()) {
						largeSubgraph.addNode(node);
					}
					for (ClusterNode<?> node : smallSubgraph.getAllNodes()) {
						subgraphs.put(node, largeSubgraph);
					}
					removeSubgraph(smallSubgraph);
				}
			}
		}
	}

	private ClusterNode<?> consumeFromAtom(Edge<ClusterNode<?>> edge) {
		ClusterNode<?> fromNode = edge.getFromNode();
		if (!atoms.contains(fromNode))
			return null;

		if (fromNode.getType() == MetaNodeType.CLUSTER_ENTRY) {
			ClusterBoundaryNode copy = new ClusterBoundaryNode(fromNode.getHash(), MetaNodeType.CLUSTER_ENTRY);

			Edge<ClusterNode<?>> patchEdge = new Edge<ClusterNode<?>>(copy, edge.getToNode(), edge.getEdgeType(),
					edge.getOrdinal());
			copy.addOutgoingEdge(patchEdge);
			edge.getToNode().removeIncomingEdge(edge);
			edge.getToNode().addIncomingEdge(patchEdge);

			return copy;
		} else {
			atoms.remove(fromNode);
			return fromNode;
		}
	}

	private ClusterNode<?> consumeToAtom(Edge<ClusterNode<?>> edge) {
		ClusterNode<?> toNode = edge.getToNode();
		if (!atoms.contains(toNode))
			return null;

		if (toNode.getType() == MetaNodeType.CLUSTER_EXIT) {
			ClusterBoundaryNode copy = new ClusterBoundaryNode(toNode.getHash(), MetaNodeType.CLUSTER_EXIT);

			Edge<ClusterNode<?>> patchEdge = new Edge<ClusterNode<?>>(edge.getFromNode(), copy, edge.getEdgeType(),
					edge.getOrdinal());
			copy.addIncomingEdge(patchEdge);
			edge.getFromNode().replaceEdge(edge, patchEdge);

			return copy;
		} else {
			atoms.remove(toNode);
			return toNode;
		}
	}

	private ModuleGraphCluster<ClusterNode<?>> addSubgraph() {
		ModuleGraphCluster<ClusterNode<?>> subgraph = new ModuleGraphCluster<ClusterNode<?>>(originalGraph.cluster);
		for (ModuleGraph moduleGraph : originalGraph.getGraphs()) {
			subgraph.addModule(moduleGraph);
		}
		distinctSubgraphs.add(subgraph);
		return subgraph;
	}

	private void removeSubgraph(ModuleGraphCluster<ClusterNode<?>> subgraph) {
		distinctSubgraphs.remove(subgraph);
	}
}
