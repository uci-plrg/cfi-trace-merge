package edu.uci.eecs.crowdsafe.merge.graph.report;

import edu.uci.eecs.crowdsafe.common.util.IdCounter;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterUIB;

class ModuleEventFrequencies {

	private final IdCounter<Long> indirectEdgeTargetCounts = new IdCounter<Long>();
	private int gencodePermCount = 0;
	private int gencodeWriteCount = 0;
	private int intraModuleUnexpectedReturns = 0;
	private int crossModuleUnexpectedReturns = 0;
	private int abnormalReturnCount = 0;
	private int suibCount = 0;

	public ModuleEventFrequencies(ClusterGraph dataset, ProgramEventFrequencies programEventFrequencies) {
		
		if (dataset == null)
			return;
		
		for (ClusterNode<?> node : dataset.graph.getAllNodes()) {
			OrdinalEdgeList<ClusterNode<?>> edgeList = node.getOutgoingEdges();
			try {
				if (node.getType() == MetaNodeType.RETURN) {
					if (!edgeList.isEmpty()) {
						abnormalReturnCount++;
						programEventFrequencies.incrementAbnormalReturns();
					}
				}

				for (Edge<ClusterNode<?>> edge : edgeList) {
					switch (edge.getEdgeType()) {
						case INDIRECT:
							// N.B.: assuming no collisions between relative tags and exit hashes
							if (edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT) {
								indirectEdgeTargetCounts.increment(edge.getToNode().getHash());
								programEventFrequencies.incrementIndirectTarget(edge.getToNode().getHash());
							}else {
								indirectEdgeTargetCounts.increment((long) edge.getToNode().getRelativeTag());
								programEventFrequencies.incrementIndirectTarget(edge.getToNode().getRelativeTag());
							}
							break;
						case UNEXPECTED_RETURN:
							if (edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT)
								crossModuleUnexpectedReturns++;
							else
								intraModuleUnexpectedReturns++;
							break;
						case GENCODE_PERM:
							gencodePermCount++;
							programEventFrequencies.incrementGencodePerms();
							break;
						case GENCODE_WRITE:
							gencodeWriteCount++;
							programEventFrequencies.incrementGencodeWrites();
							break;
					}
				}
			} finally {
				edgeList.release();
			}
		}
		
		ClusterMetadataExecution metadata = dataset.graph.metadata.getRootSequence().getHeadExecution();
		for (ClusterUIB uib : metadata.uibs) {
			if (!uib.isAdmitted)
				suibCount++;
		}
	}

	public int getAbnormalReturnCount() {
		return abnormalReturnCount;
	}

	public int getCrossModuleUnexpectedReturns() {
		return crossModuleUnexpectedReturns;
	}

	public int getGencodePermCount() {
		return gencodePermCount;
	}

	public int getGencodeWriteCount() {
		return gencodeWriteCount;
	}

	public int getIndirectEdgeTargetCount(long target) {
		return indirectEdgeTargetCounts.get(target);
	}

	public int getIntraModuleUnexpectedReturns() {
		return intraModuleUnexpectedReturns;
	}

	public int getSuibCount() {
		return suibCount;
	}
}
