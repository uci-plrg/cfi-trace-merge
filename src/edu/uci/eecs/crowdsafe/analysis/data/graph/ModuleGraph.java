package edu.uci.eecs.crowdsafe.analysis.data.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;

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
	protected Map<Long, Node> signature2Node;

	protected final ProcessExecutionGraph containingGraph;
	protected final ExecutionGraphData graphData;

	public ModuleGraph(ProcessExecutionGraph containingGraph,
			SoftwareDistributionUnit softwareUnit) {
		this.containingGraph = containingGraph;
		this.softwareUnit = softwareUnit;
		this.signature2Node = new HashMap<Long, Node>();
		this.graphData = new ExecutionGraphData(containingGraph);
	}

	public ExecutionGraphData getGraphData() {
		return graphData;
	}

	public int getCrossModuleSignatureCount() {
		return signature2Node.size();
	}

	// Add a node with hashcode hash and return the newly
	// created node
	public void addNode(Node node) {
		graphData.nodes.add(node);
		graphData.hash2Nodes.add(node);
	}

	// Add the signature node to the graph
	public Node addSignatureNode(long sigHash) {
		Node sigNode = signature2Node.get(sigHash);
		if (sigNode == null) {
			sigNode = new Node(containingGraph, MetaNodeType.SIGNATURE_HASH,
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
	public void addModuleNode(Node n) {
		MetaNodeType type = n.getMetaNodeType();
		if (type == MetaNodeType.MODULE_BOUNDARY) {
			// addModuleBoundaryNode(n); // TODO: do we need module boundary nodes?
		} else if (type == MetaNodeType.SIGNATURE_HASH) {
			addSignatureNode(n.getHash());
		} else {
			addNormalNode(n);
		}
	}

	private void addNormalNode(Node n) {
		n.setIndex(graphData.nodes.size());
		graphData.nodes.add(n);
		graphData.hash2Nodes.add(n);
		graphData.blockHashes.add(n.getHash());
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

	public HashSet<Node> getAccessibleNodes() {
		HashSet<Node> accessibleNodes = new HashSet<Node>();
		for (int i = 0; i < graphData.nodes.size(); i++) {
			graphData.nodes.get(i).resetVisited();
		}
		Queue<Node> bfsQueue = new LinkedList<Node>();
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
			Node n = bfsQueue.remove();
			n.setVisited();
			accessibleNodes.add(n);
			for (int i = 0; i < n.getOutgoingEdges().size(); i++) {
				Node neighbor = n.getOutgoingEdges().get(i).getToNode();
				if (!neighbor.isVisited()) {
					bfsQueue.add(neighbor);
					neighbor.setVisited();
				}
			}
		}
		return accessibleNodes;
	}

	public ArrayList<Node> getDanglingNodes() {
		ArrayList<Node> danglingNodes = new ArrayList<Node>();
		for (int i = 0; i < graphData.nodes.size(); i++) {
			Node n = graphData.nodes.get(i);
			if (n.getIncomingEdges().size() == 0
					&& n.getOutgoingEdges().size() == 0)
				danglingNodes.add(n);
		}
		return danglingNodes;
	}

	/**
	 * Only according to the name of the module graph
	 */
	public boolean equals(Object o) {
		if (o == null || o.getClass() != ModuleGraph.class) {
			return false;
		}
		ModuleGraph anotherGraph = (ModuleGraph) o;
		return (anotherGraph.softwareUnit == softwareUnit);
	}

	public String toString() {
		return "Module_" + softwareUnit.name;
	}
}
