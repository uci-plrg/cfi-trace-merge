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
		private int maximumIndirectsInPath = -1;

		public Subgraph() {
			distinctSubgraphs.add(this);
		}

		private void analyze() {
			if (!entries.isEmpty())
				return;

			findBridges();
			pathAnalyzer.findLongestPath(this);
			maximumPathLength = pathAnalyzer.longestPath;
			maximumIndirectsInPath = pathAnalyzer.maxIndirects;
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

		public int getMaximumIndirectsInPath() {
			return maximumIndirectsInPath;
		}

		public boolean contains(Node<?> node) {
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

	private class PathFrame {
		Node<?> node;
		int level;
		int indirectCount;

		int edgeIndex;
		private OrdinalEdgeList<? extends Node<?>> edgeList;
	}

	private class PathAnalyzer {
		private final LinkedList<PathFrame> framePool = new LinkedList<PathFrame>();
		private final LinkedList<PathFrame> frameStack = new LinkedList<PathFrame>();
		private final Set<Node<?>> currentPathNodes = new HashSet<Node<?>>();
		int longestPath;
		int maxIndirects;

		void findLongestPath(Subgraph subgraph) {
			longestPath = 0;
			maxIndirects = 0;

			for (Edge<? extends Node<?>> entry : subgraph.entries) {
				findLongestPath(subgraph, entry);
			}
		}

		private void findLongestPath(Subgraph subgraph, Edge<? extends Node<?>> entry) {
			currentPathNodes.clear();

			PathFrame rootFrame = getEmptyFrame(entry.getToNode(), 0, entry.getEdgeType() == EdgeType.INDIRECT ? 1 : 0);
			PathFrame currentFrame = rootFrame;
			while (true) {
				if (currentFrame.edgeIndex < currentFrame.edgeList.size()) {
					Edge<? extends Node<?>> edge = currentFrame.edgeList.get(currentFrame.edgeIndex++);
					Node<?> toNode = edge.getToNode();
					if (currentPathNodes.contains(toNode)) {
						continue;
					} else if (!subgraph.nodes.contains(toNode)) {
						if (currentFrame.level > longestPath)
							longestPath = currentFrame.level;

						int indirectCount = currentFrame.indirectCount;
						if (edge.getEdgeType() == EdgeType.INDIRECT)
							indirectCount++;
						if (indirectCount > maxIndirects) {
							maxIndirects = indirectCount;
						}

						continue;
					}
					currentPathNodes.add(toNode);
					frameStack.push(currentFrame);
					currentFrame = getEmptyFrame(toNode, currentFrame.level + 1, currentFrame.indirectCount);
					if (edge.getEdgeType() == EdgeType.INDIRECT)
						currentFrame.indirectCount++;
				} else {
					recycleFrame(currentFrame);
					currentPathNodes.remove(currentFrame.node);

					if (frameStack.isEmpty())
						break;

					currentFrame = frameStack.pop();
				}
			}
		}

		private PathFrame getEmptyFrame(Node<?> node, int level, int indirectCount) {
			PathFrame frame;
			if (framePool.isEmpty())
				frame = new PathFrame();
			else
				frame = framePool.pop();

			frame.node = node;
			frame.level = level;
			frame.indirectCount = indirectCount;
			frame.edgeIndex = 0;
			frame.edgeList = node.getOutgoingEdges();
			return frame;
		}

		private void recycleFrame(PathFrame frame) {
			frame.edgeList.release();
			framePool.push(frame);
		}
	}

	public static class UnmatchedIndirectCounts {
		int total = 0;
		int withinExpected = 0;
		int withinUnexpected = 0;
		int fromUnexpected = 0;
		int toUnexpected = 0;
		
		public int getTotal() {
			return total;
		}
		
		public int getWithinExpected() {
			return withinExpected;
		}
		
		public int getFromUnexpected() {
			return fromUnexpected;
		}
		
		public int getToUnexpected() {
			return toUnexpected;
		}
		
		public int getWithinUnexpected() {
			return withinUnexpected;
		}
	}

	private int unmatchedEdgeCount;
	private final Set<Node<?>> unmatchedNodes = new HashSet<Node<?>>();
	private final Set<Node<?>> atoms = new HashSet<Node<?>>();
	private final Map<Node<?>, Subgraph> subgraphs = new HashMap<Node<?>, Subgraph>();
	private final List<Subgraph> distinctSubgraphs = new ArrayList<Subgraph>();
	private final PathAnalyzer pathAnalyzer = new PathAnalyzer();
	public final UnmatchedIndirectCounts unmatchedIndirectCounts = new UnmatchedIndirectCounts();
	
	int getSubgraphCount() {
		return getSubgraphs().size();
	}
	
	int getTotalUnmatchedNodes() {
		return unmatchedNodes.size();
	}
	
	int getTotalUnmatchedEdges() {
		return unmatchedEdgeCount;
	}

	void nodeAdded(Node<?> node) {
		unmatchedNodes.add(node);
		atoms.add(node);
	}

	void edgeAdded(Edge<?> edge) {
		unmatchedEdgeCount++;
		
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

		if (edge.getEdgeType() == EdgeType.INDIRECT) {
			unmatchedIndirectCounts.total++;
			if (unmatchedNodes.contains(edge.getFromNode())) {
				if (unmatchedNodes.contains(edge.getToNode())) {
					unmatchedIndirectCounts.withinUnexpected++;
				} else {
					unmatchedIndirectCounts.fromUnexpected++;
				}
			} else {
				if (unmatchedNodes.contains(edge.getToNode())) {
					unmatchedIndirectCounts.toUnexpected++;
				} else {
					unmatchedIndirectCounts.withinExpected++;
				}
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
