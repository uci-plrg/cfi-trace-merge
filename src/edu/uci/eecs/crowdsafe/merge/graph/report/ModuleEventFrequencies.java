package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.util.Properties;
import java.util.UUID;

import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.ModuleAnonymousGraphs;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadataSequence;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleUIB;

public class ModuleEventFrequencies {

	private static void setInt(Properties properties, int moduleId, String key, int value) {
		properties.setProperty(key + moduleId, String.valueOf(value));
	}

	public static class ModulePropertyReader {
		private final String moduleName;
		private final int moduleId;
		private final Properties alphas;
		private final Properties counts;

		public ModulePropertyReader(String moduleName, int moduleId, Properties alphas, Properties counts) {
			this.moduleName = moduleName;
			this.moduleId = moduleId;
			this.alphas = alphas;
			this.counts = counts;
		}

		public int getCount(String key) {
			return getCount(key, moduleId);
		}

		public int getCount(String key, int moduleId) {
			String value = counts.getProperty(key + moduleId);
			if (value == null) {
				return 0;
			} else {
				return Integer.parseInt(value);
			}
		}
		
		public double getAlpha(String key) {
			String value = alphas.getProperty(key + moduleName);
			if (value == null)
				return 0.0;
			else
				return Double.parseDouble(value);
		}
	}

	static final String GENCODE_PERM_COUNT = "gencode-perm-count@";
	static final String GENCODE_WRITE_COUNT = "gencode-write-count@";
	static final String INTRA_MODULE_UNEXPECTED_RETURNS = "intra-module-ur-count@";
	static final String CROSS_MODULE_UNEXPECTED_RETURNS = "cross-module-ur-count@";
	static final String ABNORMAL_RETURNS = "abnormal-return-count@";
	static final String UIB_COUNT = "uib-count@";
	static final String SUIB_COUNT = "suib-count@";
	static final String IS_JIT = "jit@";
	static final String SDR_COUNT = "standalone-count@";

	// private final IdCounter<Long> indirectEdgeTargetCounts = new IdCounter<Long>();
	private int gencodePermCount = 0;
	private int gencodeWriteCount = 0;
	private int intraModuleUnexpectedReturns = 0;
	private int crossModuleUnexpectedReturns = 0;
	private int abnormalReturnCount = 0;
	private int uibCount = 0;
	private int suibCount = 0;
	private int isJIT = 0;
	private int sdrCount = 0;

	public final int moduleId;

	private UUID metadataId;

	public ModuleEventFrequencies(int moduleId) {
		this.moduleId = moduleId;
	}

	public void extractStatistics(ModuleGraph<ModuleNode<?>> dataset,
			ProgramEventFrequencies programEventFrequencies) {

		if (dataset == null)
			return;

		for (ModuleNode<?> node : dataset.getAllNodes()) {
			OrdinalEdgeList<ModuleNode<?>> edgeList = node.getOutgoingEdges();
			try {
				if (node.getType() == MetaNodeType.RETURN) {
					if (!edgeList.isEmpty()) {
						abnormalReturnCount++;
						programEventFrequencies.incrementAbnormalReturns();
					}
				}

				for (Edge<ModuleNode<?>> edge : edgeList) {
					switch (edge.getEdgeType()) {
						case UNEXPECTED_RETURN:
							if (edge.getToNode().getType() == MetaNodeType.MODULE_EXIT)
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

		ModuleMetadataSequence root = dataset.metadata.getRootSequence();
		if (root != null) {
			ModuleMetadataExecution metadata = root.getHeadExecution();
			metadataId = metadata.id;
			for (ModuleUIB uib : metadata.uibs) {
				if (uib.isAdmitted)
					uibCount++;
				else
					suibCount++;
			}
		}
	}

	public void extractStatistics(ModuleAnonymousGraphs module, ProgramEventFrequencies programEventFrequencies) {
		if (module.isJIT()) {
			isJIT = 1;
			programEventFrequencies.incrementJITCount();
			sdrCount = 0;
		} else {
			isJIT = 0;
			sdrCount = module.subgraphs.size();
			programEventFrequencies.addStandaloneCount(sdrCount);
		}
	}

	public void reset() {
		gencodePermCount = 0;
		gencodeWriteCount = 0;
		intraModuleUnexpectedReturns = 0;
		crossModuleUnexpectedReturns = 0;
		abnormalReturnCount = 0;
		uibCount = 0;
		suibCount = 0;
	}

	public void exportTo(int moduleId, Properties properties, UUID mainId) {
		if (metadataId != null && mainId != null && !metadataId.equals(mainId)) {
			uibCount = suibCount = 0; // lazily clear metadata counts for a missing execution
		}
		setInt(properties, moduleId, GENCODE_PERM_COUNT, gencodePermCount);
		setInt(properties, moduleId, GENCODE_WRITE_COUNT, gencodeWriteCount);
		setInt(properties, moduleId, INTRA_MODULE_UNEXPECTED_RETURNS, intraModuleUnexpectedReturns);
		setInt(properties, moduleId, CROSS_MODULE_UNEXPECTED_RETURNS, crossModuleUnexpectedReturns);
		setInt(properties, moduleId, ABNORMAL_RETURNS, abnormalReturnCount);
		setInt(properties, moduleId, UIB_COUNT, uibCount);
		setInt(properties, moduleId, SUIB_COUNT, suibCount);
		setInt(properties, moduleId, IS_JIT, isJIT);
		setInt(properties, moduleId, SDR_COUNT, sdrCount);
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

	public int getIntraModuleUnexpectedReturns() {
		return intraModuleUnexpectedReturns;
	}

	public int getSuibCount() {
		return suibCount;
	}

	public int getUibCount() {
		return uibCount;
	}
}
