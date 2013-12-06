package edu.uci.eecs.crowdsafe.merge.graph.anonymous;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.MutableInteger;

public class AnonymousModule {

	static class OwnerKey {
		final AutonomousSoftwareDistribution cluster;
		final boolean isBlackBox;

		OwnerKey(AutonomousSoftwareDistribution cluster, boolean isBlackBox) {
			this.cluster = cluster;
			this.isBlackBox = isBlackBox;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((cluster == null) ? 0 : cluster.hashCode());
			result = prime * result + (isBlackBox ? 1231 : 1237);
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
			OwnerKey other = (OwnerKey) obj;
			if (cluster == null) {
				if (other.cluster != null)
					return false;
			} else if (!cluster.equals(other.cluster))
				return false;
			if (isBlackBox != other.isBlackBox)
				return false;
			return true;
		}
	}

	static void initialize() {
		INELIGIBLE_OWNERS.add("ntdll.dll");
		INELIGIBLE_OWNERS.add("kernel32.dll");
		INELIGIBLE_OWNERS.add("kernelbase.dll");
		INELIGIBLE_OWNERS.add("user32.dll");
		INELIGIBLE_OWNERS.add("system32.dll");
		INELIGIBLE_OWNERS.add("gdi32.dll");
		INELIGIBLE_OWNERS.add("msvcr100.dll");

		OWNER_ALIAS.put("acrord32.dll", "acrord32.exe");
		OWNER_ALIAS.put("annots.api", "acrord32.exe");
	}

	static boolean isEligibleOwner(AutonomousSoftwareDistribution potentialOwner) {
		for (SoftwareUnit potentialOwnerUnit : potentialOwner.getUnits()) {
			if (INELIGIBLE_OWNERS.contains(potentialOwnerUnit.filename))
				return false;
		}
		return true;
	}

	static AutonomousSoftwareDistribution resolveAlias(AutonomousSoftwareDistribution cluster) {
		String aliasTo = null;
		for (SoftwareUnit unit : cluster.getUnits()) {
			aliasTo = OWNER_ALIAS.get(unit.filename);
			if (aliasTo != null) {
				for (SoftwareUnit configuredUnit : ConfiguredSoftwareDistributions.getInstance().unitsByName.values()) {
					if (configuredUnit.filename.equals(aliasTo))
						return ConfiguredSoftwareDistributions.getInstance().distributionsByUnit.get(configuredUnit);
				}
			}
		}
		return cluster;
	}

	private static final Set<String> INELIGIBLE_OWNERS = new HashSet<String>();
	private static final Map<String, String> OWNER_ALIAS = new HashMap<String, String>();

	final AutonomousSoftwareDistribution owningCluster;
	final List<AnonymousSubgraph> subgraphs = new ArrayList<AnonymousSubgraph>();

	private int totalNodeCount = 0;
	private int executableNodeCount = 0;
	private boolean isBlackBox = false;

	public AnonymousModule(AutonomousSoftwareDistribution owningCluster) {
		this.owningCluster = owningCluster;
	}

	void addSubgraph(AnonymousSubgraph subgraph) {
		if (subgraphs.isEmpty()) {
			isBlackBox = subgraph.isAnonymousBlackBox();
		} else if (subgraph.isAnonymousBlackBox() != isBlackBox) {
			if (isBlackBox) {
				throw new IllegalArgumentException("Attempt to add a white box subgraph to a black box module!");
			} else {
				throw new IllegalArgumentException("Attempt to add a black box subgraph to a white box module!");
			}
		}

		subgraphs.add(subgraph);

		totalNodeCount += subgraph.getNodeCount();
		executableNodeCount += subgraph.getExecutableNodeCount();
	}

	public int getNodeCount() {
		return totalNodeCount;
	}

	public int getExecutableNodeCount() {
		return executableNodeCount;
	}

	public boolean isBlackBox() {
		return isBlackBox;
	}

	boolean hasEscapes(ModuleGraphCluster<ClusterNode<?>> subgraph) {
		for (ClusterNode<?> entry : subgraph.getEntryPoints()) {
			if (ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousEntryHash(entry.getHash()) != owningCluster)
				return true;
		}
		for (ClusterNode<?> exit : subgraph.getExitPoints()) {
			if (ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousExitHash(exit.getHash()) != owningCluster)
				return true;
		}
		return false;
	}

	void reportEdgeProfile() {
		Log.log("    --- Edge Profile ---");

		Map<EdgeType, MutableInteger> edgeCountsByType = new EnumMap<EdgeType, MutableInteger>(EdgeType.class);
		Map<EdgeType, MutableInteger> edgeOrdinalCountsByType = new EnumMap<EdgeType, MutableInteger>(EdgeType.class);
		for (EdgeType type : EdgeType.values()) {
			edgeCountsByType.put(type, new MutableInteger(0));
			edgeOrdinalCountsByType.put(type, new MutableInteger(0));
		}

		int maxIndirectEdgeCountPerOrdinal = 0;
		int singletonIndirectOrdinalCount = 0;
		int pairIndirectOrdinalCount = 0;
		int totalOrdinals = 0;
		int totalEdges = 0;
		int ordinalCount;
		EdgeType edgeType;

		for (AnonymousSubgraph subgraph : subgraphs) {
			for (ClusterNode<?> node : subgraph.getAllNodes()) {
				if (node.getType().isExecutable) {
					ordinalCount = node.getOutgoingOrdinalCount();
					totalOrdinals += ordinalCount;
					for (int ordinal = 0; ordinal < ordinalCount; ordinal++) {
						OrdinalEdgeList<ClusterNode<?>> edges = node.getOutgoingEdges(ordinal);
						try {
							if (edges.isEmpty())
								continue;

							edgeType = edges.get(0).getEdgeType();
							edgeCountsByType.get(edgeType).add(edges.size());
							edgeOrdinalCountsByType.get(edgeType).increment();
							totalEdges += edges.size();

							if (edgeType == EdgeType.INDIRECT) {
								if (edges.size() > maxIndirectEdgeCountPerOrdinal)
									maxIndirectEdgeCountPerOrdinal = edges.size();
								if (edges.size() == 1)
									singletonIndirectOrdinalCount++;
								else if (edges.size() == 2)
									pairIndirectOrdinalCount++;
							}
						} finally {
							edges.release();
						}
					}
				}
			}
		}

		Log.log("     Total edges: %d; Total ordinals: %d", totalEdges, totalOrdinals);

		int instances;
		int ordinals;
		int instancePercentage;
		int ordinalPercentage;
		Set<EdgeType> reportedEdgeTypes = EnumSet.of(EdgeType.DIRECT, EdgeType.CALL_CONTINUATION,
				EdgeType.EXCEPTION_CONTINUATION, EdgeType.INDIRECT, EdgeType.UNEXPECTED_RETURN);
		for (EdgeType type : reportedEdgeTypes) {
			instances = edgeCountsByType.get(type).getVal();
			ordinals = edgeOrdinalCountsByType.get(type).getVal();
			instancePercentage = Math.round((instances / (float) totalEdges) * 100f);
			ordinalPercentage = Math.round((ordinals / (float) totalOrdinals) * 100f);
			Log.log("     Edge type %s: %d total edges (%d%%), %d ordinals (%d%%)", type.name(), instances,
					instancePercentage, ordinals, ordinalPercentage);
		}

		int indirectTotal = edgeCountsByType.get(EdgeType.INDIRECT).getVal();
		float averageIndirectEdgeCount = (indirectTotal / (float) edgeOrdinalCountsByType.get(EdgeType.INDIRECT)
				.getVal());
		int singletonIndirectPercentage = Math.round((singletonIndirectOrdinalCount / (float) indirectTotal) * 100f);
		int pairIndirectPercentage = Math.round((pairIndirectOrdinalCount / (float) indirectTotal) * 100f);
		Log.log("     Average indirect edge fanout: %.3f; Max: %d; singletons: %d (%d%%); pairs: %d (%d%%)",
				averageIndirectEdgeCount, maxIndirectEdgeCountPerOrdinal, singletonIndirectOrdinalCount,
				singletonIndirectPercentage, pairIndirectOrdinalCount, pairIndirectPercentage);
	}
}
