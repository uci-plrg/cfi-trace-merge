package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.util.Properties;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.IdCounter;
import edu.uci.eecs.crowdsafe.common.util.RiskySystemCall;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadata;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadataSequence;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleSSC;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleUIB;

public class ProgramEventFrequencies {

	private static void setInt(Properties properties, String key, int value) {
		properties.setProperty(key, String.valueOf(value));
	}

	public static class ProgramPropertyReader {
		private final Properties alphas;
		private final Properties counts;

		public ProgramPropertyReader(Properties alphas, Properties counts) {
			this.alphas = alphas;
			this.counts = counts;
		}

		public int getCount(String key) {
			String value = counts.getProperty(key);
			if (value == null)
				return 0;
			else
				return Integer.parseInt(value);
		}

		public ModuleEventFrequencies.ModulePropertyReader getModule(String moduleName) {
			String idValue = counts.getProperty(moduleName);
			if (idValue == null)
				return null;
			else
				return new ModuleEventFrequencies.ModulePropertyReader(moduleName, Integer.parseInt(idValue), alphas,
						counts);
		}

		public int getModuleId(String moduleName) {
			String idValue = counts.getProperty(moduleName);
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
	static final String JIT_COUNT = "jit-count";
	static final String SDR_COUNT = "sdr-count";

	// private final IdCounter<Long> indirectEdgeTargetCounts = new IdCounter<Long>();
	private IdCounter<Integer> sscCountsBySysnum = new IdCounter<Integer>();
	private int sgeCount = 0;
	private int suibCount = 0;
	private int abnormalReturnCount = 0;
	private int gencodePermCount = 0;
	private int gencodeWriteCount = 0;
	private int jitCount = 0;
	private int sdrCount = 0;

	public void countMetadataEvents(ModuleMetadata metadata) {
		ModuleMetadataSequence sequence = metadata.getRootSequence();
		if (sequence == null)
			return;
		ModuleMetadataExecution execution = sequence.getHeadExecution();
		if (execution == null)
			return;

		sgeCount += execution.sges.size();

		for (ModuleSSC ssc : execution.sscs) {
			if (RiskySystemCall.sysnumMap.containsKey(ssc.sysnum))
				sscCountsBySysnum.increment(ssc.sysnum);
		}

		for (ModuleUIB uib : execution.uibs) {
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
		setInt(properties, JIT_COUNT, jitCount);
		setInt(properties, SDR_COUNT, sdrCount);
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

	public void incrementJITCount() {
		jitCount++;
	}

	public void addStandaloneCount(int count) {
		sdrCount += count;
	}
}
