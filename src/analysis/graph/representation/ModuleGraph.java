package analysis.graph.representation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ModuleGraph extends ExecutionGraph {
	private String moduleName;
	
	private HashMap<Long, Node> signiture2EntryNode;
	
	public ModuleGraph(String moduleName) {
		this.moduleName = moduleName;
		this.signiture2EntryNode = new HashMap<Long, Node>();
		this.hash2Nodes = new NodeHashMap();
		this.nodes = new ArrayList<Node>();
		
		// These fields are all redundant for the purpose of debugging
		this.pairHashes = new HashSet<Long>();
		this.blockHashes = new HashSet<Long>();
		this.pairHashInstances = new ArrayList<Long>();
		this.blockHashInstances = new ArrayList<Long>();
	}
	
	public String toString() {
		return moduleName;
	}
	
	public void addEdge(Edge e) {
		
	}
	
	public void addNormalNode(Node n) {
		Node newNode = new Node(this, n.getHash(), nodes.size(),
				n.getMetaNodeType(), n.getNormalizedTag());
		nodes.add(newNode);
		hash2Nodes.add(newNode);
		blockHashes.add(newNode.getHash());
		blockHashInstances.add(newNode.getHash());
	}
	
	public void addSignitureNode(long sigHash) {
		if (!signiture2EntryNode.containsKey(sigHash)) {
			Node sigNode = new Node(this, sigHash, nodes.size(),
					MetaNodeType.SIGNITURE_HASH, null);
		}
	}
	
}
