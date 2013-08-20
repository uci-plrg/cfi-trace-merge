package edu.uci.eecs.crowdsafe.analysis.merge.graph;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.analysis.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ModuleGraph;

public class GraphMergeTarget {

	private final GraphMergeSession session;

	final NodeHashMap nodesByHash = new NodeHashMap();
	final Map<ExecutionNode.Key, ExecutionNode> nodesByKey = new HashMap<ExecutionNode.Key, ExecutionNode>();

	GraphMergeTarget(GraphMergeSession session) {
		this.session = session;
	}

	void addModule(ModuleGraph module) {
		if (session.state != GraphMergeSession.State.INITIALIZATION)
			throw new IllegalStateException(String.format(
					"Attempt to add a module to a merge session in %s state.",
					session.state));

		nodesByHash.addAll(module.getGraphData().nodesByHash);
		nodesByKey.putAll(module.getGraphData().nodesByKey);
	}
}
