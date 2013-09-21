package edu.uci.eecs.crowdsafe.merge.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.GraphLoadEventListener;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.log.Log;

public class MergeDebugLog implements GraphLoadEventListener {

	private static class TrackedNodeKey {
		int pageOffset;
		long hash;

		TrackedNodeKey() {
		}

		TrackedNodeKey(int pageOffset, long hash) {
			if (pageOffset > 0xfff)
				Log.log("Warning: page offset 0x%x exceeds page size of 0x1000!", pageOffset);

			this.pageOffset = pageOffset;
			this.hash = hash;
		}

		TrackedNodeKey assign(ExecutionNode node) {
			this.pageOffset = (int) (node.getKey().relativeTag & 0xfff);
			this.hash = node.getHash();
			return this;
		}

		TrackedNodeKey assign(long tag, long hash) {
			this.pageOffset = (int) (tag & 0xfff);
			this.hash = hash;
			return this;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (int) (hash ^ (hash >>> 32));
			result = prime * result + pageOffset;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TrackedNodeKey other = (TrackedNodeKey) obj;
			if (hash != other.hash)
				return false;
			if (pageOffset != other.pageOffset)
				return false;
			return true;
		}
	}

	private final Set<TrackedNodeKey> trackedNodes = new HashSet<TrackedNodeKey>();
	private final TrackedNodeKey trackedNodeLookupKey = new TrackedNodeKey();
	private final List<Long> debugRelativeTags = new ArrayList<Long>();

	public MergeDebugLog() {
		// TODO: hash differs on peer run of ls: omit absolute ops for nodes in the unknown module?

		// shell32.dll(0x12ed32-v0|0xb5d)
		// shell32.dll(0x1ac59c-v0|0x380ddc8bc5ee128b) (missed)
		// trackedNodes.add(new TrackedNodeKey(0xd32, 0xb5d));

		// shell32.dll(0x936fe-v0|0x11b8963216d79f)
		// debugRelativeTags.add(0x936feL);
	}

	void debugCheck(Node<?> node) {
		if (debugRelativeTags.contains(node.getRelativeTag()) && node.getModule().unit.filename.equals("shell32.dll"))
			node.getClass();
	}

	private boolean isTracked(Node<?> node) {
		if (node instanceof ExecutionNode) {
			return trackedNodes.contains(trackedNodeLookupKey.assign((ExecutionNode) node));
		}
		return false;
	}

	void nodesMatched(Node<?> left, Node<?> right) {
		if (isTracked(left) || isTracked(right))
			Log.log("Node %s matched with node %s", left.toString(), right.toString());
	}

	void matchEnqueued(PairNode match) {
		if (isTracked(match.getLeftNode()) || isTracked(match.getRightNode()))
			Log.log("Enqueue matched pair %s and %s", match.getLeftNode(), match.getRightNode());
	}

	void matchDequeued(PairNode match) {
		if (isTracked(match.getLeftNode()) || isTracked(match.getRightNode()))
			Log.log("Dequeue matched pair %s and %s", match.getLeftNode(), match.getRightNode());
	}

	void unmatchEnqueued(Node<?> unmatch) {
		if (isTracked(unmatch))
			Log.log("Enqueue unmatched node %s", unmatch);
	}

	void unmatchDequeued(Node<?> unmatch) {
		if (isTracked(unmatch))
			Log.log("Dequeue unmatched node %s", unmatch);
	}

	void indirectEdgeEnqueued(PairNodeEdge rightEdge) {
		if (isTracked(rightEdge.getLeftParentNode()) || isTracked(rightEdge.getRightParentNode()))
			Log.log("Enqueue indirect edge from %s to %s", rightEdge.getLeftParentNode(),
					rightEdge.getRightParentNode());
	}

	void indirectEdgeDequeued(PairNodeEdge rightEdge) {
		if (isTracked(rightEdge.getLeftParentNode()) || isTracked(rightEdge.getRightParentNode()))
			Log.log("Dequeue indirect edge from %s to %s", rightEdge.getLeftParentNode(),
					rightEdge.getRightParentNode());
	}

	void nodeMergedFromLeft(Node<?> leftNode) {
		if (isTracked(leftNode))
			Log.log("Merge node %s from the left graph.", leftNode.toString());
	}

	void nodeMergedFromRight(Node<?> rightNode) {
		if (isTracked(rightNode))
			Log.log("Merge node %s from the right graph.", rightNode.toString());
	}

	void edgeMergedFromLeft(Edge<?> edge) {
		if (isTracked(edge.getFromNode()) || isTracked(edge.getToNode()))
			Log.log("Merge edge %s from the left graph.", edge.toString());
	}

	void edgeMergedFromRight(Edge<?> edge) {
		if (isTracked(edge.getFromNode()) || isTracked(edge.getToNode()))
			Log.log("Merge edge %s from the right graph.", edge.toString());
	}

	// 2% hot during load!
	@Override
	public void nodeLoadReference(long tag, long hash, LoadTarget target) {
		if (trackedNodes.contains(trackedNodeLookupKey.assign(tag, hash)))
			Log.log("Node 0x%x(0x%x) referenced during %s load.", tag, hash, target.displayName);
	}

	@Override
	public void nodeLoadReference(Node<?> node, LoadTarget target) {
		if (isTracked(node))
			Log.log("Node %s referenced during %s load.", node.toString(), target.displayName);
	}

	@Override
	public void nodeCreation(Node<?> node) {
		if (isTracked(node))
			Log.log("Node %s created.", node.toString());
	}

	// 5% hot during load!
	@Override
	public void edgeCreation(Edge<?> edge) {
		if (isTracked(edge.getFromNode()) || isTracked(edge.getToNode())) {
			Log.log("Edge %s created.", edge.toString());
		}
	}

	@Override
	public void graphAddition(Node<?> node, ModuleGraphCluster<?> cluster) {
		if (isTracked(node)) {
			Log.log("Node %s added to cluster %s.", node.toString(), cluster.cluster.name);
		}
	}
}
