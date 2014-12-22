package edu.uci.eecs.crowdsafe.merge.graph.tag;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.util.CrowdSafeCollections;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.results.Graph;
import edu.uci.eecs.crowdsafe.graph.util.ModuleEdgeCounter;

public class ClusterTagMergeFragment {

	private final ClusterTagMergeSession session;

	private int exeutableNodeCount = 0;
	private final ModuleEdgeCounter edgeCounter = new ModuleEdgeCounter();
	private final Set<Edge<ClusterNode<?>>> addedEdges = new HashSet<Edge<ClusterNode<?>>>();

	ClusterTagMergeFragment(ClusterTagMergeSession session) {
		this.session = session;
	}

	void nodeAdded(ClusterNode<?> node) {
		if (!node.isMetaNode())
			exeutableNodeCount++;
	}

	void edgeAdded(Edge<ClusterNode<?>> edge) {
		addedEdges.add(edge);
		if ((edge.getFromNode().getType() == MetaNodeType.CLUSTER_ENTRY)
				|| (edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT))
			edgeCounter.tallyInterEdge(edge.getEdgeType());
		else
			edgeCounter.tallyIntraEdge(edge.getEdgeType());
	}

	public Collection<Edge<ClusterNode<?>>> getAddedEdges() {
		return addedEdges;
	}

	Graph.Cluster summarizeCurrentCluster() {
		Graph.Cluster.Builder clusterBuilder = Graph.Cluster.newBuilder();
		Graph.Module.Builder moduleBuilder = Graph.Module.newBuilder();
		Graph.ModuleInstance.Builder moduleInstanceBuilder = Graph.ModuleInstance.newBuilder();
		Graph.EdgeTypeCount.Builder edgeTypeCountBuilder = Graph.EdgeTypeCount.newBuilder();

		clusterBuilder.setDistributionName(session.left.cluster.name);
		clusterBuilder.setNodeCount(session.statistics.getAddedNodeCount());
		clusterBuilder.setExecutableNodeCount(exeutableNodeCount);
		clusterBuilder.setEntryPointCount(edgeCounter.getInterCount(EdgeType.DIRECT)
				+ edgeCounter.getInterCount(EdgeType.INDIRECT) + edgeCounter.getInterCount(EdgeType.GENCODE_PERM)
				+ edgeCounter.getInterCount(EdgeType.GENCODE_WRITE));
		clusterBuilder.setMetadata(Graph.ModuleMetadata.newBuilder().build());

		for (ModuleGraph moduleGraph : CrowdSafeCollections.createSortedCopy(session.left.getGraphs(),
				ModuleGraphCluster.ModuleGraphSorter.INSTANCE)) {
			moduleBuilder.clear().setName(moduleGraph.softwareUnit.filename);
			moduleBuilder.setVersion(moduleGraph.softwareUnit.version);
			moduleInstanceBuilder.setModule(moduleBuilder.build());
			moduleInstanceBuilder.setNodeCount(session.statistics.getAddedNodeCount());
			clusterBuilder.addModule(moduleInstanceBuilder.build());
		}

		for (EdgeType type : EdgeType.values()) {
			edgeTypeCountBuilder.clear().setType(type.mapToResultType());
			edgeTypeCountBuilder.setCount(edgeCounter.getInterCount(type));
			clusterBuilder.addInterModuleEdgeCount(edgeTypeCountBuilder.build());
		}
		for (EdgeType type : EdgeType.values()) {
			edgeTypeCountBuilder.clear().setType(type.mapToResultType());
			edgeTypeCountBuilder.setCount(edgeCounter.getIntraCount(type));
			clusterBuilder.addIntraModuleEdgeCount(edgeTypeCountBuilder.build());
		}

		return clusterBuilder.build();
	}
}
