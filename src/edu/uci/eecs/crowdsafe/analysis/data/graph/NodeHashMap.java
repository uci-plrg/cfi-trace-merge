package edu.uci.eecs.crowdsafe.analysis.data.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.analysis.data.graph.execution.ExecutionNode;

public class NodeHashMap {
	private final Map<Long, NodeList> map = new HashMap<Long, NodeList>();

	public void add(ExecutionNode node) {
		NodeList existing = map.get(node.getHash());
		if (existing == null) {
			map.put(node.getHash(), node);
		} else {
			if (existing.isSingleton()) {
				NodeArrayList list = new NodeArrayList();
				list.add((ExecutionNode) existing);
				list.add(node);
				map.put(node.getHash(), list);
			} else {
				((NodeArrayList) existing).add(node);
			}
		}
	}

	public void addAll(NodeHashMap other) {
		map.putAll(other.map);
	}
	
	public void add(Long hash, NodeList list) {
		map.put(hash, list);
	}

	public NodeList get(long hash) {
		return map.get(hash);
	}

	public Set<Long> keySet() {
		return map.keySet();
	}
}
