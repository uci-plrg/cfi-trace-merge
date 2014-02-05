package edu.uci.eecs.crowdsafe.merge.graph.tag;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.data.results.Graph;
import edu.uci.eecs.crowdsafe.common.data.results.NodeResultsFactory;
import edu.uci.eecs.crowdsafe.common.util.CrowdSafeCollections;
import edu.uci.eecs.crowdsafe.common.util.ModuleEdgeCounter;

class ClusterTagMergeFragment {

	private final ClusterTagMergeSession session;

	private int exeutableNodeCount = 0;
	private final ModuleEdgeCounter edgeCounter = new ModuleEdgeCounter();

	ClusterTagMergeFragment(ClusterTagMergeSession session) {
		this.session = session;
	}

	void nodeAdded(ClusterNode<?> node) {
		if (!node.isMetaNode())
			exeutableNodeCount++;
	}

	void edgeAdded(Edge<ClusterNode<?>> edge) {
		if ((edge.getFromNode().getType() == MetaNodeType.CLUSTER_ENTRY)
				|| (edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT))
			edgeCounter.tallyInterEdge(edge.getEdgeType());
		else
			edgeCounter.tallyIntraEdge(edge.getEdgeType());
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
				+ edgeCounter.getInterCount(EdgeType.INDIRECT));
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
