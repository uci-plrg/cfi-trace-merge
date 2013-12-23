package edu.uci.eecs.crowdsafe.merge.graph.tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.common.log.Log;

// generalize for splitting a graph into subgraphs? or make a new one based on this?
class ClusterTagMergedSubgraphs {

	// this could be separated into a subgraph and a profile
	// comparison is based on profile, not node content
	class Subgraph implements Comparable<Subgraph> {
		private final Set<Node<?>> nodes = new HashSet<Node<?>>();
		private final Set<Edge<? extends Node<?>>> edges = new HashSet<Edge<? extends Node<?>>>();
		private final Set<Edge<? extends Node<?>>> entries = new HashSet<Edge<? extends Node<?>>>();
		private final Set<Edge<? extends Node<?>>> exits = new HashSet<Edge<? extends Node<?>>>();
		private int instanceCount = 1;
		private int maximumPathLength = -1;

		public Subgraph() {
			distinctSubgraphs.add(this);
		}

		private void analyze() {
			if (!entries.isEmpty())
				return;

			findBridges();
			maximumPathLength = pathAnalyzer.findLongestPath(this);
		}

		private void findBridges() {
			for (Edge<? extends Node<?>> edge : edges) {
				if ((edge.getEdgeType() == EdgeType.CLUSTER_ENTRY) || !nodes.contains(edge.getFromNode()))
					entries.add(edge);
				else if ((edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT) || !nodes.contains(edge.getToNode()))
					exits.add(edge);
			}

			if (entries.isEmpty())
				toString();
		}

		int getNodeCount() {
			return nodes.size();
		}

		int getBridgeCount() {
			return entries.size() + exits.size();
		}

		public int getInstanceCount() {
			return instanceCount;
		}

		public Collection<Edge<? extends Node<?>>> getEntries() {
			return entries;
		}

		public Collection<Edge<? extends Node<?>>> getExits() {
			return exits;
		}

		public Collection<Edge<? extends Node<?>>> getEdges() {
			return edges;
		}

		public int getMaximumPathLength() {
			return maximumPathLength;
		}

		public boolean contains(Node node) {
			return nodes.contains(node);
		}

		public void logGraph() {
			for (Edge<? extends Node<?>> edge : entries)
				Log.log("\tentry: %s", edge);
			for (Edge<? extends Node<?>> edge : exits)
				Log.log("\texit: %s", edge);

			for (Edge<? extends Node<?>> edge : edges) {
				if (!(entries.contains(edge) || exits.contains(edge)))
					Log.log("\tintra: %s", edge);
			}
		}

		@Override
		public int compareTo(Subgraph o) {
			int comparison = nodes.size() - o.nodes.size();
			if (comparison != 0)
				return comparison;
			return getBridgeCount() - o.getBridgeCount();
		}
	}

	private class PathAnalyzer {
		private final LinkedList<Node<?>> queue = new LinkedList<Node<?>>();
		private final Set<Node<?>> visitedNodes = new HashSet<Node<?>>();

		int findLongestPath(Subgraph subgraph) {
			int longestPath = -1;
			for (Edge<? extends Node<?>> entry : subgraph.entries) {
				int length = findLongestPath(subgraph, entry.getToNode());
				if (length > longestPath)
					longestPath = length;
			}
			return longestPath;
		}

		private int findLongestPath(Subgraph subgraph, Node<?> entry) {
			visitedNodes.clear();
			queue.clear();
			queue.add(entry);

			int level = 0; // simulating recursion depth with counters
			int currentLevelCount = 1;
			int nextLevelCount = 0;
			while (!queue.isEmpty()) {
				Node<?> node = queue.removeFirst();

				OrdinalEdgeList<? extends Node<?>> edgeList = node.getOutgoingEdges();
				try {
					for (Edge<? extends Node<?>> edge : edgeList) {
						Node<?> toNode = edge.getToNode();
						if (subgraph.nodes.contains(toNode) && !visitedNodes.contains(toNode)) {
							queue.add(toNode);
							visitedNodes.add(toNode);
							nextLevelCount++;
						}
					}
				} finally {
					edgeList.release();
				}

				if (--currentLevelCount == 0) {
					level++;
					currentLevelCount = nextLevelCount;
					nextLevelCount = 0;
				}
			}
			return level;
		}
	}

	private final Set<Node<?>> atoms = new HashSet<Node<?>>();
	private final Map<Node<?>, Subgraph> subgraphs = new HashMap<Node<?>, Subgraph>();
	private final List<Subgraph> distinctSubgraphs = new ArrayList<Subgraph>();
	private final PathAnalyzer pathAnalyzer = new PathAnalyzer();

	void nodeAdded(Node<?> node) {
		atoms.add(node);
	}

	void edgeAdded(Edge<?> edge) {
		if (atoms.remove(edge.getFromNode())) {
			if (atoms.remove(edge.getToNode())) {
				Subgraph subgraph = new Subgraph();
				subgraph.edges.add(edge);
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
				}
				subgraph.edges.add(edge);
				subgraphs.put(edge.getFromNode(), subgraph);
			}
		} else if (atoms.remove(edge.getToNode())) {
			Subgraph subgraph = subgraphs.get(edge.getFromNode());
			if (subgraph != null) {
				subgraph.nodes.add(edge.getToNode());
			} else {
				subgraph = new Subgraph();
				subgraph.nodes.add(edge.getToNode());
			}
			subgraph.edges.add(edge);
			subgraphs.put(edge.getToNode(), subgraph);
		} else {
			Subgraph fromSubgraph = subgraphs.get(edge.getFromNode());
			Subgraph toSubgraph = subgraphs.get(edge.getToNode());
			if (fromSubgraph != null) {
				if (toSubgraph != null) {
					if (fromSubgraph == toSubgraph) {
						fromSubgraph.edges.add(edge);
					} else {
						Subgraph smallSubgraph, largeSubgraph;
						if (fromSubgraph.nodes.size() < toSubgraph.nodes.size()) {
							smallSubgraph = fromSubgraph;
							largeSubgraph = toSubgraph;
						} else {
							smallSubgraph = toSubgraph;
							largeSubgraph = fromSubgraph;
						}
						largeSubgraph.nodes.addAll(smallSubgraph.nodes);
						largeSubgraph.edges.addAll(smallSubgraph.edges);
						largeSubgraph.edges.add(edge);
						for (Node<?> node : smallSubgraph.nodes) {
							subgraphs.put(node, largeSubgraph);
						}
						distinctSubgraphs.remove(smallSubgraph);
					}
				} else {
					fromSubgraph.edges.add(edge);
				}
			} else if (toSubgraph != null) {
				toSubgraph.edges.add(edge);
			}
		}
	}

	public Collection<Subgraph> getSubgraphs() {
		if (!distinctSubgraphs.isEmpty()) {
			Collections.sort(distinctSubgraphs);
			distinctSubgraphs.get(0).analyze();
			for (int i = distinctSubgraphs.size() - 1; i >= 1; i--) {
				Subgraph current = distinctSubgraphs.get(i);
				Subgraph previous = distinctSubgraphs.get(i - 1);
				if (current.compareTo(previous) == 0) {
					previous.instanceCount = current.instanceCount + 1;
					distinctSubgraphs.remove(i);
				} else {
					current.analyze();
				}
			}
		}
		return distinctSubgraphs;
	}
}
