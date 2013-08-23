package edu.uci.eecs.crowdsafe.analysis.data.graph.execution;

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

	protected final ProcessExecutionGraph containingGraph;

	private int blockCount = 0;

	public ModuleGraph(ProcessExecutionGraph containingGraph,
			SoftwareDistributionUnit softwareUnit) {
		this.containingGraph = containingGraph;
		this.softwareUnit = softwareUnit;
	}

	public ProcessExecutionGraph getContainingGraph() {
		return containingGraph;
	}

	void incrementBlockCount() {
		blockCount++;
	}

	public int getBlockCount() {
		return blockCount;
	}

	/**
	 * <pre>
	private void addModuleBoundaryNode(Node n) {
		Node newNode = new Node(containingGraph, n);
		newNode.setMetaNodeType(MetaNodeType.MODULE_BOUNDARY);
		graphData.nodesByHash.add(newNode);
		newNode.setIndex(graphData.nodes.size());
		graphData.nodes.add(newNode);

		graphData.blockHashes.add(newNode.getHash());
	}
	 */

	public String toString() {
		return "Module_" + softwareUnit.name;
	}
}
