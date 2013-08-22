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
		graphData.nodesByHash.add(node);
		graphData.nodesByKey.put(node.getKey(), node);
	}

	// Add the signature node to the graph
	public ExecutionNode addClusterEntryNode(long crossModuleSignatureHash,
			ModuleInstance module) {
		ExecutionNode entryNode = entryNodesBySignatureHash
				.get(crossModuleSignatureHash);
		if (entryNode == null) {
			entryNode = new ExecutionNode(module, MetaNodeType.CLUSTER_ENTRY,
					0L, 0, crossModuleSignatureHash);
			entryNodesBySignatureHash.put(entryNode.getHash(), entryNode);
			graphData.nodesByKey.put(entryNode.getKey(), entryNode);
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
			graphData.nodesByHash.add(node);
			graphData.nodesByKey.put(node.getKey(), node);
		}
	}

	public Set<ExecutionNode> searchAccessibleNodes() {
		Set<ExecutionNode> accessibleNodes = new HashSet<ExecutionNode>();
		Set<ExecutionNode> visitedNodes = new HashSet<ExecutionNode>();
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
			accessibleNodes.add(n);
			visitedNodes.add(n);
			for (int i = 0; i < n.getOutgoingEdges().size(); i++) {
				ExecutionNode neighbor = n.getOutgoingEdges().get(i)
						.getToNode();
				if (!visitedNodes.contains(neighbor)) {
					bfsQueue.add(neighbor);
					visitedNodes.add(neighbor);
				}
			}
		}
		return accessibleNodes;
	}

	public List<ExecutionNode> getDanglingNodes() {
		List<ExecutionNode> danglingNodes = new ArrayList<ExecutionNode>();
		for (ExecutionNode n : graphData.nodesByKey.values()) {
			if (n.getIncomingEdges().size() == 0
					&& n.getOutgoingEdges().size() == 0)
				danglingNodes.add(n);
		}
		return danglingNodes;
	}
}
