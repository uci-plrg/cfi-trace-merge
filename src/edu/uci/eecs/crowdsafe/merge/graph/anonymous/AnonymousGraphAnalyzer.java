package edu.uci.eecs.crowdsafe.merge.graph.anonymous;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.graph.hash.MaximalSubgraphs;

class AnonymousGraphAnalyzer {

	private static class SizeOrder implements Comparator<ModuleGraphCluster<ClusterNode<?>>> {
		@Override
		public int compare(ModuleGraphCluster<ClusterNode<?>> first, ModuleGraphCluster<ClusterNode<?>> second) {
			int comparison = second.getExecutableNodeCount() - first.getExecutableNodeCount();
			if (comparison != 0)
				return comparison;

			return (int) second.hashCode() - (int) first.hashCode();
		}
	}

	int totalSize = 0;
	int minSize = Integer.MAX_VALUE;
	int maxSize = 0;
	int averageSize;
	int twiceAverage;
	int thriceAverage;
	int halfAverage;
	int subgraphsOverTwiceAverage;
	int subgraphsOverThriceAverage;
	int subgraphsUnderHalfAverage;

	List<ModuleGraphCluster<ClusterNode<?>>> maximalSubgraphs = new ArrayList<ModuleGraphCluster<ClusterNode<?>>>();

	List<ModuleGraphCluster<ClusterNode<?>>> getMaximalSubgraphs(List<ModuleGraphCluster<ClusterNode<?>>> dynamicGraphs) {
		for (ModuleGraphCluster<ClusterNode<?>> dynamicGraph : dynamicGraphs) {
			for (ModuleGraphCluster<ClusterNode<?>> maximalSubgraph : MaximalSubgraphs
					.getMaximalSubgraphs(dynamicGraph)) {
				int size = maximalSubgraph.getNodeCount();
				totalSize += size;
				if (size < minSize)
					minSize = size;
				if (size > maxSize)
					maxSize = size;

				maximalSubgraphs.add(maximalSubgraph);
			}
		}

		Collections.sort(maximalSubgraphs, new SizeOrder());

		averageSize = totalSize / maximalSubgraphs.size();
		twiceAverage = averageSize * 2;
		thriceAverage = averageSize * 3;
		halfAverage = averageSize / 2;
		subgraphsOverTwiceAverage = 0;
		subgraphsOverThriceAverage = 0;
		subgraphsUnderHalfAverage = 0;
		for (ModuleGraphCluster<ClusterNode<?>> maximalSubgraph : maximalSubgraphs) {
			int size = maximalSubgraph.getNodeCount();
			if (size > twiceAverage) {
				subgraphsOverTwiceAverage++;
				if (size > thriceAverage)
					subgraphsOverThriceAverage++;
			} else if (size < halfAverage) {
				subgraphsUnderHalfAverage++;
			}
		}

		Log.log("Found %d maximal subgraphs.", maximalSubgraphs.size());
		Log.log("Min size %d, max size %d, average size %d", minSize, maxSize, averageSize);
		Log.log("Over twice average %d, over thrice average %d, under half average %d", subgraphsOverTwiceAverage,
				subgraphsOverThriceAverage, subgraphsUnderHalfAverage);

		return maximalSubgraphs;
	}

	void analyzeSubgraphs() {
		for (ModuleGraphCluster<ClusterNode<?>> subgraph : maximalSubgraphs) {
			Log.log("\n === Subgraph of %d nodes", subgraph.getExecutableNodeCount());

			for (Long entryHash : subgraph.getEntryHashes()) {
				ClusterNode<?> entryPoint = subgraph.getEntryPoint(entryHash);

				OrdinalEdgeList<?> edges = entryPoint.getOutgoingEdges();
				try {
					Log.log("     Entry point 0x%x reaches %d nodes", entryHash, edges.size());
				} finally {
					edges.release();
				}
			}

			for (ClusterNode<?> node : subgraph.getAllNodes()) {
				if (node.getType() == MetaNodeType.CLUSTER_EXIT) {
					OrdinalEdgeList<?> edges = node.getIncomingEdges();
					try {
						Log.log("     Callout 0x%x from %d nodes", node.getHash(), edges.size());
					} finally {
						edges.release();
					}
				}
			}
		}
	}
}
