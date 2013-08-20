package edu.uci.eecs.crowdsafe.analysis.data.graph.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.analysis.data.graph.MetaNodeType;

/**
 * <p>
 * ModuleGraph is a special ExecutionGraph which starts from multiple different signature hash node. An indirect edge
 * links the signature hash node to the real entry nodes of the module. If there are conflicts on signature hash, there
 * will be multiple indirect edges from the same signature hash node to different target entry nodes. This class has a
 * special field, signature2Node, which maps from signature hash to the "bogus" node representing that signature.
 * </p>
 * 
 * <p>
 * When matching the module graph, we suppose that the "moduleName" field is the universal identity of the graph, which
 * means we can will and only will match the graphs that have the same module names. The matching procedure is almost
 * the same as that of the ExecutionGraph except that we should think all the signature nodes are already matched.
 * </p>
 * 
 * 
 * @author peizhaoo
 * 
 */

public class ModuleGraph {
	public final SoftwareDistributionUnit softwareUnit;

	// Maps from signature hash to bogus signature node
	protected Map<Long, ExecutionNode> signature2Node;

	protected final ProcessExecutionGraph containingGraph;
	protected final ExecutionGraphData graphData;

	public ModuleGraph(ProcessExecutionGraph containingGraph,
			SoftwareDistributionUnit softwareUnit) {
		this.containingGraph = containingGraph;
		this.softwareUnit = softwareUnit;
		this.signature2Node = new HashMap<Long, ExecutionNode>();
		this.graphData = new ExecutionGraphData(containingGraph);
	}

	public ExecutionGraphData getGraphData() {
		return graphData;
	}
	
	public ProcessExecutionGraph getContainingGraph() {
		return containingGraph;
	}

	public int getCrossModuleSignatureCount() {
		return signature2Node.size();
	}

	public void addNode(ExecutionNode node) {
		graphData.nodes.add(node);
		graphData.hash2Nodes.add(node);
	}

	// Add the signature node to the graph
	public ExecutionNode addSignatureNode(long sigHash) {
		ExecutionNode sigNode = signature2Node.get(sigHash);
		if (sigNode == null) {
			sigNode = new ExecutionNode(containingGraph, MetaNodeType.SIGNATURE_HASH,
					0L, 0, sigHash);
			signature2Node.put(sigNode.getHash(), sigNode);
			sigNode.setIndex(graphData.nodes.size());
			graphData.nodes.add(sigNode);
		}
		return sigNode;
	}

	/**
	 * Node n is assumed to be a node in this module. When calling this function, check this property first.
	 * 
	 * The edges are added in the node outside this function. Cross-module edges are not seen in any ExecutionGraph. For
	 * cross-module edges, it assumes that the indirect edge between the signature node to the real entry node has
	 * already been established.
	 * 
	 * @param n
	 */
	public void addModuleNode(ExecutionNode n) {
		MetaNodeType type = n.getMetaNodeType();
		if (type == MetaNodeType.MODULE_BOUNDARY) {
			// addModuleBoundaryNode(n); // TODO: do we need module boundary nodes?
		} else if (type == MetaNodeType.SIGNATURE_HASH) {
			addSignatureNode(n.getHash());
		} else {
			addNormalNode(n);
		}
	}

	private void addNormalNode(ExecutionNode n) {
		n.setIndex(graphData.nodes.size());
		graphData.nodes.add(n);
		graphData.hash2Nodes.add(n);
	}

	/**
	 * <pre>
	private void addModuleBoundaryNode(Node n) {
		Node newNode = new Node(containingGraph, n);
		newNode.setMetaNodeType(MetaNodeType.MODULE_BOUNDARY);
		graphData.hash2Nodes.add(newNode);
		newNode.setIndex(graphData.nodes.size());
		graphData.nodes.add(newNode);

		graphData.blockHashes.add(newNode.getHash());
	}
	 */

	public HashSet<ExecutionNode> getAccessibleNodes() {
		HashSet<ExecutionNode> accessibleNodes = new HashSet<ExecutionNode>();
		for (int i = 0; i < graphData.nodes.size(); i++) {
			graphData.nodes.get(i).resetVisited();
		}
		Queue<ExecutionNode> bfsQueue = new LinkedList<ExecutionNode>();
		for (long sigHash : signature2Node.keySet()) {
			bfsQueue.add(signature2Node.get(sigHash));
		}
		if (this instanceof ModuleGraph) {
			ModuleGraph mGraph = (ModuleGraph) this;
			if (mGraph.softwareUnit.name.startsWith("ntdll.dll-")) {
				bfsQueue.add(graphData.nodes.get(0));
			}
		}

		while (bfsQueue.size() > 0) {
			ExecutionNode n = bfsQueue.remove();
			n.setVisited();
			accessibleNodes.add(n);
			for (int i = 0; i < n.getOutgoingEdges().size(); i++) {
				ExecutionNode neighbor = n.getOutgoingEdges().get(i).getToNode();
				if (!neighbor.isVisited()) {
					bfsQueue.add(neighbor);
					neighbor.setVisited();
				}
			}
		}
		return accessibleNodes;
	}

	public ArrayList<ExecutionNode> getDanglingNodes() {
		ArrayList<ExecutionNode> danglingNodes = new ArrayList<ExecutionNode>();
		for (int i = 0; i < graphData.nodes.size(); i++) {
			ExecutionNode n = graphData.nodes.get(i);
			if (n.getIncomingEdges().size() == 0
					&& n.getOutgoingEdges().size() == 0)
				danglingNodes.add(n);
		}
		return danglingNodes;
	}

	public String toString() {
		return "Module_" + softwareUnit.name;
	}
}
