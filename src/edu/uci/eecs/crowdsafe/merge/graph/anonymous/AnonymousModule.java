package edu.uci.eecs.crowdsafe.merge.graph.anonymous;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareUnit;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.util.EdgeCounter;

public class AnonymousModule {

	public static class OwnerKey {
		public final AutonomousSoftwareDistribution cluster;
		public final boolean isBlackBox;

		public OwnerKey(AutonomousSoftwareDistribution cluster, boolean isBlackBox) {
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

	public static void initialize() {
		INELIGIBLE_OWNERS.add("ntdll.dll");
		INELIGIBLE_OWNERS.add("kernelbase.dll");
		INELIGIBLE_OWNERS.add("kernel32.dll");
		INELIGIBLE_OWNERS.add("user32.dll");
		INELIGIBLE_OWNERS.add("system32.dll");
		INELIGIBLE_OWNERS.add("gdi32.dll");
		INELIGIBLE_OWNERS.add("ole32.dll");
		INELIGIBLE_OWNERS.add("comdlg32.dll");
		INELIGIBLE_OWNERS.add("msvcr100.dll");
		INELIGIBLE_OWNERS.add("shlwapi.dll");
		INELIGIBLE_OWNERS.add("mmdevapi.dll");
		INELIGIBLE_OWNERS.add("wininet.dll");
		INELIGIBLE_OWNERS.add("mshtml.dll");
		INELIGIBLE_OWNERS.add("wer.dll");
		INELIGIBLE_OWNERS.add("oleaut32.dll");
		INELIGIBLE_OWNERS.add("msctf.dll");
		INELIGIBLE_OWNERS.add("windowscodecs.dll");
		INELIGIBLE_OWNERS.add("gdiplus.dll");

		// Adobe utility libs
		INELIGIBLE_OWNERS.add("ace.dll");
		INELIGIBLE_OWNERS.add("agm.dll");
		INELIGIBLE_OWNERS.add("adobelinguistic.dll");

		OWNER_ALIAS.put("acrord32.dll", "acrord32.exe");
		// OWNER_ALIAS.put("clr.dll", "mso.dll");
	}

	public static boolean isEligibleOwner(AutonomousSoftwareDistribution potentialOwner) {
		boolean onlyApiFiles = true;
		for (SoftwareUnit potentialOwnerUnit : potentialOwner.getUnits()) {
			if (INELIGIBLE_OWNERS.contains(potentialOwnerUnit.filename))
				return false;
			onlyApiFiles &= potentialOwnerUnit.filename.endsWith("api");
		}
		return !onlyApiFiles;
	}

	public static AutonomousSoftwareDistribution resolveAlias(AutonomousSoftwareDistribution cluster) {
		String aliasTo = null;
		for (SoftwareUnit unit : cluster.getUnits()) {
			aliasTo = getOwnerAlias(unit);
			if (aliasTo != null) {
				for (SoftwareUnit configuredUnit : ConfiguredSoftwareDistributions.getInstance().unitsByName.values()) {
					if (configuredUnit.filename.equals(aliasTo))
						return ConfiguredSoftwareDistributions.getInstance().distributionsByUnit.get(configuredUnit);
				}
				Log.log("Warning: failed to locate alias %s of %s!", aliasTo, unit.filename);
			}
		}
		return cluster;
	}

	private static String getOwnerAlias(SoftwareUnit unit) {
		// if (unit.filename.endsWith(".ni.dll"))
		// return "mso.dll";
		// else
		return OWNER_ALIAS.get(unit.filename);
	}

	private static final File DOT_DIRECTORY = new File("./dot");

	private static final Set<String> INELIGIBLE_OWNERS = new HashSet<String>();
	private static final Map<String, String> OWNER_ALIAS = new HashMap<String, String>();

	public final AutonomousSoftwareDistribution owningCluster;
	public final List<AnonymousSubgraph> subgraphs = new ArrayList<AnonymousSubgraph>();

	private int totalNodeCount = 0;
	private int executableNodeCount = 0;
	private boolean isBlackBox = false;

	public AnonymousModule(AutonomousSoftwareDistribution owningCluster) {
		this.owningCluster = owningCluster;
	}

	public void addSubgraph(AnonymousSubgraph subgraph) {
		if (subgraphs.isEmpty()) {
			isBlackBox = subgraph.isAnonymousBlackBox();
		} else if (subgraph.isAnonymousBlackBox() != isBlackBox) {
			if (isBlackBox) {
				throw new IllegalArgumentException("Attempt to add a white box subgraph to a black box module!");
			} else {
				throw new IllegalArgumentException("Attempt to add a black box subgraph to a white box module!");
			}
		}
		
		if (isBlackBox && !subgraphs.isEmpty())
			throw new IllegalArgumentException("Cannot add a second subgraph to a black box module!");

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

	void printDotFiles() throws IOException {
		File outputDirectory = new File(DOT_DIRECTORY, owningCluster.getUnitFilename());
		for (AnonymousSubgraph subgraph : subgraphs) {
			String basename = isBlackBox ? "black-box" : "white-box";
			File outputFile = new File(outputDirectory, String.format("%s.%d.gv", basename, subgraph.id));
			String label = String.format("%s %s #%d", owningCluster.getUnitFilename(), basename, subgraph.id);
			subgraph.writeDotFile(outputFile, label);
		}
	}

	void reportEdgeProfile() {
		Log.log("    --- Edge Profile ---");

		EdgeCounter edgeCountsByType = new EdgeCounter();
		EdgeCounter edgeOrdinalCountsByType = new EdgeCounter();

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
							edgeCountsByType.tally(edgeType, edges.size());
							edgeOrdinalCountsByType.tally(edgeType);
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
			instances = edgeCountsByType.getCount(type);
			ordinals = edgeOrdinalCountsByType.getCount(type);
			instancePercentage = Math.round((instances / (float) totalEdges) * 100f);
			ordinalPercentage = Math.round((ordinals / (float) totalOrdinals) * 100f);
			Log.log("     Edge type %s: %d total edges (%d%%), %d ordinals (%d%%)", type.name(), instances,
					instancePercentage, ordinals, ordinalPercentage);
		}

		int indirectTotal = edgeCountsByType.getCount(EdgeType.INDIRECT);
		float averageIndirectEdgeCount = (indirectTotal / (float) edgeOrdinalCountsByType.getCount(EdgeType.INDIRECT));
		int singletonIndirectPercentage = Math.round((singletonIndirectOrdinalCount / (float) indirectTotal) * 100f);
		int pairIndirectPercentage = Math.round((pairIndirectOrdinalCount / (float) indirectTotal) * 100f);
		Log.log("     Average indirect edge fanout: %.3f; Max: %d; singletons: %d (%d%%); pairs: %d (%d%%)",
				averageIndirectEdgeCount, maxIndirectEdgeCountPerOrdinal, singletonIndirectOrdinalCount,
				singletonIndirectPercentage, pairIndirectOrdinalCount, pairIndirectPercentage);
	}
}
