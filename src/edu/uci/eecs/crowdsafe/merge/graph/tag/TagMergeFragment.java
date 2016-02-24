package edu.uci.eecs.crowdsafe.merge.graph.tag;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.results.Graph;
import edu.uci.eecs.crowdsafe.graph.util.ModuleEdgeCounter;

public class TagMergeFragment {

	private final TagMergeSession session;

	private int exeutableNodeCount = 0;
	private final ModuleEdgeCounter edgeCounter = new ModuleEdgeCounter();
	private final Set<Edge<ModuleNode<?>>> addedEdges = new HashSet<Edge<ModuleNode<?>>>();

	TagMergeFragment(TagMergeSession session) {
		this.session = session;
	}

	void nodeAdded(ModuleNode<?> node) {
		if (!node.isMetaNode())
			exeutableNodeCount++;
	}

	void edgeAdded(Edge<ModuleNode<?>> edge) {
		addedEdges.add(edge);
		if ((edge.getFromNode().getType() == MetaNodeType.MODULE_ENTRY)
				|| (edge.getToNode().getType() == MetaNodeType.MODULE_EXIT))
			edgeCounter.tallyInterEdge(edge.getEdgeType());
		else
			edgeCounter.tallyIntraEdge(edge.getEdgeType());
	}

	public Collection<Edge<ModuleNode<?>>> getAddedEdges() {
		return addedEdges;
	}

	Graph.Module summarizeCurrentCluster() {
		Graph.Module.Builder clusterBuilder = Graph.Module.newBuilder();
		Graph.ModuleVersion.Builder moduleVersionBuilder = Graph.ModuleVersion.newBuilder();
		Graph.ModuleInstance.Builder moduleInstanceBuilder = Graph.ModuleInstance.newBuilder();
		Graph.EdgeTypeCount.Builder edgeTypeCountBuilder = Graph.EdgeTypeCount.newBuilder();

		clusterBuilder.setDistributionName(session.left.module.name);
		clusterBuilder.setNodeCount(session.statistics.getAddedNodeCount());
		clusterBuilder.setExecutableNodeCount(exeutableNodeCount);
		clusterBuilder.setEntryPointCount(edgeCounter.getInterCount(EdgeType.DIRECT)
				+ edgeCounter.getInterCount(EdgeType.INDIRECT) + edgeCounter.getInterCount(EdgeType.GENCODE_PERM)
				+ edgeCounter.getInterCount(EdgeType.GENCODE_WRITE));
		clusterBuilder.setMetadata(Graph.ModuleMetadata.newBuilder().build());

		moduleVersionBuilder.clear().setName(session.left.module.filename);
		moduleVersionBuilder.setVersion(session.left.module.version);
		moduleInstanceBuilder.setVersion(moduleVersionBuilder.build());
		moduleInstanceBuilder.setNodeCount(session.statistics.getAddedNodeCount());
		clusterBuilder.addInstance(moduleInstanceBuilder.build());

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
