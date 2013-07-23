package analysis.graph.representation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * <p>
 * ModuleGraph is a special ExecutionGraph which starts from multiple different
 * signature hash node. An indirect edge links the signature hash node to the
 * real entry nodes of the module. If there are conflicts on signature hash,
 * there will be multiple indirect edges from the same signature hash node to
 * different target entry nodes. This class has a special field, signature2Node,
 * which maps from signature hash to the "bogus" node representing that
 * signature.
 * </p>
 * 
 * <p>
 * When matching the module graph, we suppose that the "moduleName" field is the
 * universal identity of the graph, which means we can will and only will match
 * the graphs that have the same module names. The matching procedure is almost
 * the same as that of the ExecutionGraph except that we should think all the
 * signature nodes are already matched.
 * </p>
 * 
 * 
 * @author peizhaoo
 * 
 */

public class ModuleGraph extends ExecutionGraph {
	public final String moduleName;

	private HashMap<Long, Node> signature2Node;
	
	public HashMap<Long, Node> getSigature2Node() {
		return signature2Node;
	}

	public ModuleGraph(String moduleName) {
		this.moduleName = moduleName;
		this.signature2Node = new HashMap<Long, Node>();
		this.hash2Nodes = new NodeHashMap();
		this.nodes = new ArrayList<Node>();

		// These fields are all redundant for the purpose of debugging
		this.pairHashes = new HashSet<Long>();
		this.blockHashes = new HashSet<Long>();
		this.pairHashInstances = new ArrayList<Long>();
		this.blockHashInstances = new ArrayList<Long>();
	}

	/**
	 * Only according to the name of the module graph
	 */
	public boolean equals(Object o) {
		if (o == null || o.getClass() != ModuleGraph.class) {
			return false;
		}
		ModuleGraph anotherGraph = (ModuleGraph) o;
		if (anotherGraph.moduleName.equals(moduleName)) {
			return true;
		} else {
			return false;
		}
	}
	
	public String toString() {
		return "Module_" + moduleName;
	}

	/**
	 * Node n is assumed to be a node in this module. When calling this
	 * function, check this property first.
	 * 
	 * The edges are added in the node outside this function. Cross-module edges
	 * are not seen in any ExecutionGraph. For cross-module edges, it assumes
	 * that the indirect edge between the signature node to the real entry node
	 * has already been established.
	 * 
	 * @param n
	 */
	public void addModuleNode(Node n) {
		MetaNodeType type = n.getMetaNodeType();
		if (type == MetaNodeType.MODULE_BOUNDARY) {
			addModuleBoundaryNode(n);
		} else if (type == MetaNodeType.SIGNATURE_HASH) {
			addSignitureNode(n.getHash());
		} else {
			addNormalNode(n);
		}
	}

	private void addNormalNode(Node n) {
		n.setIndex(nodes.size());
		nodes.add(n);
		hash2Nodes.add(n);
		blockHashes.add(n.getHash());
		blockHashInstances.add(n.getHash());
	}

	private void addModuleBoundaryNode(Node n) {
		Node newNode = new Node(this, n);
		newNode.setMetaNodeType(MetaNodeType.MODULE_BOUNDARY);
		hash2Nodes.add(newNode);
		newNode.setIndex(nodes.size());
		nodes.add(newNode);

		blockHashInstances.add(newNode.getHash());
		blockHashes.add(newNode.getHash());
	}

	private void addSignitureNode(long sigHash) {
		if (!signature2Node.containsKey(sigHash)) {
			Node sigNode = new Node(this, sigHash, nodes.size(),
					MetaNodeType.SIGNATURE_HASH, null);
			signature2Node.put(sigNode.getHash(), sigNode);
			sigNode.setIndex(nodes.size());
			nodes.add(sigNode);
		}
	}

	/**
	 * When completing adding all nodes of the graph, set the containing graph
	 * of all nodes.
	 */
	public void setAllContainingGraph() {
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			n.setContainingGraph(this);
		}
	}

}
