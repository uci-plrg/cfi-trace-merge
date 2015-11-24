package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.util.Properties;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.IdCounter;
import edu.uci.eecs.crowdsafe.common.util.RiskySystemCall;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSSC;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterUIB;

public class ProgramEventFrequencies {

	private static void setInt(Properties properties, String key, int value) {
		properties.setProperty(key, String.valueOf(value));
	}

	public static class ProgramPropertyReader {
		private final Properties properties;

		public ProgramPropertyReader(Properties properties) {
			this.properties = properties;
		}

		public int getProperty(String key) {
			String value = properties.getProperty(key);
			if (value == null)
				return 0;
			else
				return Integer.parseInt(value);
		}

		public ModuleEventFrequencies.ModulePropertyReader getModule(String moduleName) {
			String idValue = properties.getProperty(moduleName);
			if (idValue == null)
				return null;
			else
				return new ModuleEventFrequencies.ModulePropertyReader(Integer.parseInt(idValue), properties);
		}

		public int getModuleId(String moduleName) {
			String idValue = properties.getProperty(moduleName);
			if (idValue == null)
				return -1;
			else
				return Integer.parseInt(idValue);
		}
	}

	static final String SUSPICIOUS_SYSCALL = "suspicious-syscall-";
	static final String SGE_COUNT = "sge-count";
	static final String SUIB_COUNT = "suib-count";
	static final String ABNORMAL_RETURNS = "abnormal-return-count";
	static final String GENCODE_PERM_COUNT = "gencode-perm-count";
	static final String GENCODE_WRITE_COUNT = "gencode-write-count";
	static final String BLACK_BOX_COUNT = "black-box-count@";
	static final String WHITE_BOX_COUNT = "white-box-count@";

	// private final IdCounter<Long> indirectEdgeTargetCounts = new IdCounter<Long>();
	private IdCounter<Integer> sscCountsBySysnum = new IdCounter<Integer>();
	private int sgeCount = 0;
	private int suibCount = 0;
	private int abnormalReturnCount = 0;
	private int gencodePermCount = 0;
	private int gencodeWriteCount = 0;
	private int blackBoxCount = 0;
	private int whiteBoxCount = 0;

	public void countMetadataEvents(ClusterMetadataExecution metadata) {
		sgeCount += metadata.sges.size();

		for (ClusterSSC ssc : metadata.sscs) {
			if (RiskySystemCall.sysnumMap.containsKey(ssc.sysnum))
				sscCountsBySysnum.increment(ssc.sysnum);
		}

		for (ClusterUIB uib : metadata.uibs) {
			if (!uib.isAdmitted)
				suibCount++;
		}
	}

	public void exportTo(Properties properties) {
		for (Integer id : sscCountsBySysnum.idSet())
			setInt(properties, SUSPICIOUS_SYSCALL + id, sscCountsBySysnum.get(id));
		setInt(properties, ABNORMAL_RETURNS, abnormalReturnCount);
		setInt(properties, SGE_COUNT, sgeCount);
		setInt(properties, SUIB_COUNT, suibCount);
		setInt(properties, GENCODE_PERM_COUNT, gencodePermCount);
		setInt(properties, GENCODE_WRITE_COUNT, gencodeWriteCount);
		setInt(properties, BLACK_BOX_COUNT, blackBoxCount);
		setInt(properties, WHITE_BOX_COUNT, whiteBoxCount);
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

	public void incrementBlackBoxCount() {
		blackBoxCount++;
	}

	public void addWhiteBoxCount(int count) {
		whiteBoxCount += count;
	}
}
