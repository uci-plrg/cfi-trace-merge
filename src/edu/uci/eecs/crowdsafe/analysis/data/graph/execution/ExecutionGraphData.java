package edu.uci.eecs.crowdsafe.analysis.data.graph.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.analysis.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.InvalidGraphException;
import edu.uci.eecs.crowdsafe.util.log.Log;

public class ExecutionGraphData {
	public final ProcessExecutionGraph containingGraph;

	// False means that the file doesn't exist or is in wrong format
	protected boolean isValidGraph = true;

	// nodes in an array in the read order from file
	// TODO: would prefer to keep the nodes only by hash, or also by key if that is necessary
	public final List<ExecutionNode> nodes = new ArrayList<ExecutionNode>();

	public final NodeHashMap nodesByHash = new NodeHashMap();

	public final Map<ExecutionNode.Key, ExecutionNode> nodesByKey = new HashMap<ExecutionNode.Key, ExecutionNode>();

	public ExecutionGraphData(ProcessExecutionGraph containingGraph) {
		this.containingGraph = containingGraph;
	}

	public ExecutionNode HACK_relativeTagLookup(ExecutionNode foreignNode) {
		if (foreignNode.getTagVersion() > 0)
			return null; // no way to find it across versions

		SoftwareDistributionUnit foreignUnit = foreignNode.getModule().unit;
		ModuleInstance localModule = containingGraph.getModules().getModule(
				foreignUnit);
		long localTag = localModule.start + foreignNode.getRelativeTag();
		return nodesByKey.get(new ExecutionNode.Key(localTag, 0, localModule));
	}

	/**
	 * To validate the correctness of the graph. Basically it checks if entry points have no incoming edges, exit points
	 * have no outgoing edges. It might include more validation stuff later...
	 * 
	 * @return true means this is a valid graph, otherwise it's invalid
	 */
	public boolean validate() {
		// Check if the index of the node is correct
		for (int i = 0; i < nodes.size(); i++) {
			ExecutionNode n = nodes.get(i);
			if (n.getIndex() != i) {
				Log.log("Wrong index: " + n.getIndex());
				return false;
			}
		}

		outerLoop: for (int i = 0; i < nodes.size(); i++) {
			ExecutionNode n = nodes.get(i);
			switch (n.getType()) {
				case PROCESS_ENTRY:
					if (n.getIncomingEdges().size() != 0) {
						throw new InvalidGraphException(
								"Exit point has outgoing edges!");
					}
					break;
				case PROCESS_EXIT:
					if (n.getOutgoingEdges().size() != 0) {
						Log.log("");
						throw new InvalidGraphException(
								"Exit point has outgoing edges!");
					}
					break;
				default:
					break;
			}
		}
		return isValidGraph;
	}
}
