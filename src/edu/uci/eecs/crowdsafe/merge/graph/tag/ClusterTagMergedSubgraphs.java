package edu.uci.eecs.crowdsafe.merge.graph.tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;

class ClusterTagMergedSubgraphs {

	class Subgraph implements Comparable<Subgraph> {
		private final Set<Node<?>> nodes = new HashSet<Node<?>>();
		private int bridgeCount = 0;
		private int instanceCount = 1;

		public Subgraph() {
			distinceSubgraphs.add(this);
		}

		int getNodeCount() {
			return nodes.size();
		}

		int getBridgeCount() {
			return bridgeCount;
		}

		public int getInstanceCount() {
			return instanceCount;
		}

		@Override
		public int compareTo(Subgraph o) {
			int comparison = nodes.size() - o.nodes.size();
			if (comparison != 0)
				return comparison;
			return bridgeCount - o.bridgeCount;
		}
	}

	private final Set<Node<?>> atoms = new HashSet<Node<?>>();
	private final Map<Node<?>, Subgraph> subgraphs = new HashMap<Node<?>, Subgraph>();
	private final List<Subgraph> distinceSubgraphs = new ArrayList<Subgraph>();

	void nodeAdded(Node<?> node) {
		atoms.add(node);
	}

	void edgeAdded(Edge<?> edge) {
		if (atoms.remove(edge.getFromNode())) {
			if (atoms.remove(edge.getToNode())) {
				Subgraph subgraph = new Subgraph();
				subgraph.nodes.add(edge.getFromNode());
				subgraph.nodes.add(edge.getToNode());
				subgraphs.put(edge.getFromNode(), subgraph);
				subgraphs.put(edge.getToNode(), subgraph);
			} else {
				Subgraph subgraph = subgraphs.get(edge.getToNode());
				if (subgraph != null) {
					subgraph.nodes.add(edge.getFromNode());
				} else {
					subgraph = new Subgraph();
					subgraph.nodes.add(edge.getFromNode());
					subgraph.bridgeCount++;
				}
				subgraphs.put(edge.getFromNode(), subgraph);
			}
		} else if (atoms.remove(edge.getToNode())) {
			Subgraph subgraph = subgraphs.get(edge.getFromNode());
			if (subgraph != null) {
				subgraph.nodes.add(edge.getToNode());
			} else {
				subgraph = new Subgraph();
				subgraph.nodes.add(edge.getToNode());
				subgraph.bridgeCount++;
			}
			subgraphs.put(edge.getToNode(), subgraph);
		} else {
			Subgraph fromSubgraph = subgraphs.get(edge.getFromNode());
			Subgraph toSubgraph = subgraphs.get(edge.getToNode());
			if (fromSubgraph != null) {
				if (toSubgraph != null) {
					Subgraph smallSubgraph, largeSubgraph;
					if (fromSubgraph.nodes.size() < toSubgraph.nodes.size()) {
						smallSubgraph = fromSubgraph;
						largeSubgraph = toSubgraph;
					} else {
						smallSubgraph = toSubgraph;
						largeSubgraph = fromSubgraph;
					}
					largeSubgraph.nodes.addAll(smallSubgraph.nodes);
					for (Node<?> node : smallSubgraph.nodes) {
						subgraphs.put(node, largeSubgraph);
					}
				} else {
					fromSubgraph.bridgeCount++;
				}
			} else if (toSubgraph != null) {
				toSubgraph.bridgeCount++;
			}
		}
	}

	public Collection<Subgraph> getSubgraphs() {
		Collections.sort(distinceSubgraphs);
		for (int i = distinceSubgraphs.size() - 1; i >= 1; i--) {
			Subgraph current = distinceSubgraphs.get(i);
			Subgraph previous = distinceSubgraphs.get(i - 1);
			if (current.compareTo(previous) == 0) {
				previous.instanceCount = current.instanceCount + 1;
				distinceSubgraphs.remove(i);
			}

		}
		return distinceSubgraphs;
	}
}
