package edu.uci.eecs.crowdsafe.merge.graph.report;

import edu.uci.eecs.crowdsafe.common.util.IdCounter;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSSC;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterUIB;

public class ProgramEventFrequencies {

	private final IdCounter<Long> indirectEdgeTargetCounts = new IdCounter<Long>();
	private IdCounter<Integer> sscCountsBySysnum = new IdCounter<Integer>();
	private int sgeCount = 0;
	private int suibCount = 0;
	private int abnormalReturnCount = 0;
	private int gencodePermCount = 0;
	private int gencodeWriteCount = 0;

	public void countMetadataEvents(ClusterMetadataExecution metadata) {
		sgeCount += metadata.sges.size();

		for (ClusterSSC ssc : metadata.sscs)
			sscCountsBySysnum.increment(ssc.sysnum);

		for (ClusterUIB uib : metadata.uibs) {
			if (!uib.isAdmitted)
				suibCount++;
		}
	}

	public int getSuspiciousSysnumCount(int sysnum) {
		return sscCountsBySysnum.get(sysnum);
	}

	public int getSgeCount() {
		return sgeCount;
	}

	public int getSuibCount() {
		return suibCount;
	}

	public void incrementAbnormalReturns() {
		abnormalReturnCount++;
	}

	public int getAbnormalReturnCount() {
		return abnormalReturnCount;
	}
	
	public void incrementIndirectTarget(long target) {
		indirectEdgeTargetCounts.increment(target);
	}
	
	public int getIndirectEdgeTargetCount(long target) {
		return indirectEdgeTargetCounts.get(target);
	}
	
	public void incrementGencodePerms() {
		gencodePermCount++;
	}
	
	public int getGencodePermCount() {
		return gencodePermCount;
	}
	
	public void incrementGencodeWrites() {
		gencodeWriteCount++;
	}
	
	public int getGencodeWriteCount() {
		return gencodeWriteCount;
	}
}
