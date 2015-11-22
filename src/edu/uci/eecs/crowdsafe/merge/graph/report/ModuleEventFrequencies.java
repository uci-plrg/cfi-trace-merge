package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.util.Properties;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterUIB;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousModule;

public class ModuleEventFrequencies {

	private static void setInt(Properties properties, int moduleId, String key, int value) {
		properties.setProperty(key + moduleId, String.valueOf(value));
	}

	public static class ModulePropertyReader {
		private final int moduleId;
		private final Properties properties;

		public ModulePropertyReader(int moduleId, Properties properties) {
			this.moduleId = moduleId;
			this.properties = properties;
		}

		public int getProperty(String key) {
			return getProperty(key, moduleId);
		}

		public int getProperty(String key, int moduleId) {
			String value = properties.getProperty(key + moduleId);
			if (value == null) {
				return 0;
			} else {
				return Integer.parseInt(value);
			}
		}
	}

	static final String GENCODE_PERM_COUNT = "gencode-perm-count@";
	static final String GENCODE_WRITE_COUNT = "gencode-write-count@";
	static final String INTRA_MODULE_UNEXPECTED_RETURNS = "intra-module-ur-count@";
	static final String CROSS_MODULE_UNEXPECTED_RETURNS = "cross-module-ur-count@";
	static final String ABNORMAL_RETURNS = "abnormal-return-count@";
	static final String UIB_COUNT = "uib-count@";
	static final String SUIB_COUNT = "suib-count@";
	static final String IS_BLACK_BOX = "black-box@";
	static final String WHITE_BOX_COUNT = "white-box-count@";

	// private final IdCounter<Long> indirectEdgeTargetCounts = new IdCounter<Long>();
	private int gencodePermCount = 0;
	private int gencodeWriteCount = 0;
	private int intraModuleUnexpectedReturns = 0;
	private int crossModuleUnexpectedReturns = 0;
	private int abnormalReturnCount = 0;
	private int uibCount = 0;
	private int suibCount = 0;
	private int isBlackBox = 0;
	private int whiteBoxCount = 0;

	public final int moduleId;

	public ModuleEventFrequencies(int moduleId) {
		this.moduleId = moduleId;
	}

	public void extractStatistics(ModuleGraphCluster<ClusterNode<?>> dataset,
			ProgramEventFrequencies programEventFrequencies) {

		if (dataset == null)
			return;

		for (ClusterNode<?> node : dataset.getAllNodes()) {
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

		if (dataset.metadata.getRootSequence() != null) {
			ClusterMetadataExecution metadata = dataset.metadata.getRootSequence().getHeadExecution();
			for (ClusterUIB uib : metadata.uibs) {
				if (uib.isAdmitted)
					uibCount++;
				else
					suibCount++;
			}
		}
	}

	public void extractStatistics(AnonymousModule module, ProgramEventFrequencies programEventFrequencies) {
		if (module.isBlackBox()) {
			isBlackBox = 1;
			programEventFrequencies.incrementBlackBoxCount();
			whiteBoxCount = 0;
		} else {
			isBlackBox = 0;
			whiteBoxCount = module.subgraphs.size();
			programEventFrequencies.addWhiteBoxCount(whiteBoxCount);
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

	public void exportTo(int moduleId, Properties properties) {
		setInt(properties, moduleId, GENCODE_PERM_COUNT, gencodePermCount);
		setInt(properties, moduleId, GENCODE_WRITE_COUNT, gencodeWriteCount);
		setInt(properties, moduleId, INTRA_MODULE_UNEXPECTED_RETURNS, intraModuleUnexpectedReturns);
		setInt(properties, moduleId, CROSS_MODULE_UNEXPECTED_RETURNS, crossModuleUnexpectedReturns);
		setInt(properties, moduleId, ABNORMAL_RETURNS, abnormalReturnCount);
		setInt(properties, moduleId, UIB_COUNT, uibCount);
		setInt(properties, moduleId, SUIB_COUNT, suibCount);
		setInt(properties, moduleId, IS_BLACK_BOX, isBlackBox);
		setInt(properties, moduleId, WHITE_BOX_COUNT, whiteBoxCount);
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
