package edu.uci.eecs.crowdsafe.analysis.data.graph.execution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import edu.uci.eecs.crowdsafe.analysis.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.analysis.data.graph.MetaNodeType;

public class ModuleGraphCluster {
	public final AutonomousSoftwareDistribution distribution;

	// Maps from signature hash to bogus signature node
	protected Map<Long, ExecutionNode> entryNodesBySignatureHash = new HashMap<Long, ExecutionNode>();
	protected Map<Long, ExecutionNode> exitNodesBySignatureHash = new HashMap<Long, ExecutionNode>();

	protected final ExecutionGraphData graphData;

	private final Map<SoftwareDistributionUnit, ModuleGraph> graphs = new HashMap<SoftwareDistributionUnit, ModuleGraph>();

	public ModuleGraphCluster(AutonomousSoftwareDistribution distribution,
			ProcessExecutionGraph containingGraph) {
		this.distribution = distribution;
		this.graphData = new ExecutionGraphData(containingGraph);
	}

	public ExecutionGraphData getGraphData() {
		return graphData;
	}

	public ModuleGraph getModuleGraph(SoftwareDistributionUnit softwareUnit) {
		return graphs.get(softwareUnit);
	}

	public void addModule(ModuleGraph moduleGraph) {
		graphs.put(moduleGraph.softwareUnit, moduleGraph);
	}

	public Collection<ModuleGraph> getGraphs() {
		return graphs.values();
	}

	public int getEntryNodeCount() {
		return entryNodesBySignatureHash.size();
	}

	public Map<Long, ExecutionNode> getEntryPoints() {
		return entryNodesBySignatureHash;
	}

	public void addNode(ExecutionNode node) {
		graphData.nodes.add(node);
		graphData.nodesByHash.add(node);
		graphData.nodesByKey.put(node.getKey(), node);
	}

	// Add the signature node to the graph
	public ExecutionNode addClusterEntryNode(long crossModuleSignatureHash) {
		ExecutionNode entryNode = entryNodesBySignatureHash
				.get(crossModuleSignatureHash);
		if (entryNode == null) {
			entryNode = new ExecutionNode(graphData.containingGraph,
					MetaNodeType.SIGNATURE_HASH, 0L, 0,
					crossModuleSignatureHash);
			entryNodesBySignatureHash.put(entryNode.getHash(), entryNode);
			entryNode.setIndex(graphData.nodes.size());
			graphData.nodes.add(entryNode);
		}
		return entryNode;
	}

	/**
	 * Node n is assumed to be a node in this module. When calling this function, check this property first.
	 * 
	 * The edges are added in the node outside this function. Cross-module edges are not seen in any ExecutionGraph. For
	 * cross-module edges, it assumes that the indirect edge between the signature node to the real entry node has
	 * already been established.
	 * 
	 * @param node
	 */
	public void addModuleNode(ExecutionNode node) {
		MetaNodeType type = node.getType();
		if (type == MetaNodeType.MODULE_BOUNDARY) {
			// addModuleBoundaryNode(n); // TODO: do we need module boundary nodes?
		} else {
			node.setIndex(graphData.nodes.size());
			graphData.nodes.add(node);
			graphData.nodesByHash.add(node);
			graphData.nodesByKey.put(node.getKey(), node);
		}
	}

	public Set<ExecutionNode> searchAccessibleNodes() {
		Set<ExecutionNode> accessibleNodes = new HashSet<ExecutionNode>();
		for (int i = 0; i < graphData.nodes.size(); i++) {
			graphData.nodes.get(i).resetVisited();
		}
		Queue<ExecutionNode> bfsQueue = new LinkedList<ExecutionNode>();
		bfsQueue.addAll(entryNodesBySignatureHash.values());
		// TODO: do this with all entry points
		/**
		 * <pre>
		if (this instanceof ModuleGraph) {
			ModuleGraph mGraph = (ModuleGraph) this;
			if (mGraph.softwareUnit.name.startsWith("ntdll.dll-")) {
				bfsQueue.add(graphData.nodes.get(0));
			}
		}
		 */

		while (bfsQueue.size() > 0) {
			ExecutionNode n = bfsQueue.remove();
			n.setVisited();
			accessibleNodes.add(n);
			for (int i = 0; i < n.getOutgoingEdges().size(); i++) {
				ExecutionNode neighbor = n.getOutgoingEdges().get(i)
						.getToNode();
				if (!neighbor.isVisited()) {
					bfsQueue.add(neighbor);
					neighbor.setVisited();
				}
			}
		}
		return accessibleNodes;
	}

	public List<ExecutionNode> getDanglingNodes() {
		List<ExecutionNode> danglingNodes = new ArrayList<ExecutionNode>();
		for (int i = 0; i < graphData.nodes.size(); i++) {
			ExecutionNode n = graphData.nodes.get(i);
			if (n.getIncomingEdges().size() == 0
					&& n.getOutgoingEdges().size() == 0)
				danglingNodes.add(n);
		}
		return danglingNodes;
	}
}
