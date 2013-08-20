package edu.uci.eecs.crowdsafe.analysis.data.graph.execution;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.analysis.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.analysis.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.InvalidGraphException;

public class ExecutionGraphData {
	private final ProcessExecutionGraph process;

	// False means that the file doesn't exist or is in wrong format
	protected boolean isValidGraph = true;

	// nodes in an array in the read order from file
	// TODO: would prefer to keep the nodes only by hash, or also by key if that is necessary
	protected List<ExecutionNode> nodes;

	public final NodeHashMap nodesByHash = new NodeHashMap();

	public final Map<ExecutionNode.Key, ExecutionNode> nodesByKey;

	public ExecutionGraphData(ProcessExecutionGraph process) {
		this.process = process;
	}

	public Collection<Long> getBlockHashes() {
		return nodesByHash.keySet();
	}

	public ExecutionNode getNode(ExecutionNode.Key key) {
		return nodesByKey.get(key);
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
				System.out.println("Wrong index: " + n.getIndex());
				return false;
			}
		}

		outerLoop: for (int i = 0; i < nodes.size(); i++) {
			ExecutionNode n = nodes.get(i);
			switch (n.getMetaNodeType()) {
				case ENTRY:
					if (n.getIncomingEdges().size() != 0) {
						throw new InvalidGraphException(
								"Exit point has outgoing edges!");
					}
					break;
				case EXIT:
					if (n.getOutgoingEdges().size() != 0) {
						System.out.println("");
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
