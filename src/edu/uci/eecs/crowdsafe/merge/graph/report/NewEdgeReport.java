package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import javax.sound.midi.spi.MidiFileReader;

import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.merge.graph.report.ModuleEventFrequencies.ModulePropertyReader;
import edu.uci.eecs.crowdsafe.merge.graph.report.ProgramEventFrequencies.ProgramPropertyReader;

class NewEdgeReport implements ReportEntry {

	final Edge<ClusterNode<?>> edge;

	private int moduleSameTarget = 0;
	private int programSameTarget = 0;

	private int crossModuleUnexpectedReturns = 0;
	private int intraModuleUnexpectedReturns = 0;

	private int moduleGencodeWrite = 0;
	private int programGencodeWrite = 0;

	private int moduleGencodePerm = 0;
	private int programGencodePerm = 0;

	private int riskIndex;

	public NewEdgeReport(Edge<ClusterNode<?>> edge) {
		this.edge = edge;
	}

	private String reportEdgeType(EdgeType type) {
		switch (type) {
			case DIRECT:
				return "Direct branch";
			case INDIRECT:
				// 1. # other targets on this node
				// 2. # UIB in this module
				// [ 3. # indirects from this module to similar targets { CM, IM } ]
				return "Structural indirect branch";
			case UNEXPECTED_RETURN:
				return "Incorrect return";
			case CALL_CONTINUATION:
				return "";
			case EXCEPTION_CONTINUATION:
				return "";
			case GENCODE_PERM:
				// 1. # gencode chmod edges from this node
				return "Gencode chmod";
			case GENCODE_WRITE:
				// 1. # gencode write edges from this node
				return "Gencode write";
		}
		throw new IllegalArgumentException("Unknown EdgeType " + type);
	}

	private double calculateGencodeRiskScale(int moduleCount, int programCount) {
		if (moduleCount == 0 && programCount == 0) {
			return 1.0;
		}

		// 50% at ~100, saturate at ~10000
		// double normalizedFrequency = Math.min(1.0, 4.0 / Math.log10((double) programCount));
		// double programScale = 1.0 - (1.0 / normalizedFrequency);
		double programScale = 1.0 - ExecutionReport.calculatePrecedence(100, programCount);

		// 50% at ~25, saturate at ~1500
		double moduleScale = 0;
		if (moduleCount > 0) {
			// normalizedFrequency = Math.min(1.0, 3.0 / Math.log10((double) moduleCount));
			// moduleScale = 1.0 - (1.0 / normalizedFrequency);
			moduleScale = 1.0 - ExecutionReport.calculatePrecedence(25, moduleCount);
		}
		return Math.min(moduleScale, programScale);
	}

	private double calculateUnexpectedReturnPrecedenceScale(int moduleCount, int nodeCount) {
		// 50% at 20, saturate at 300
		// double normalizedFrequency = Math.min(1.0, 2.5 / Math.log10((double) moduleCount));
		// double modulePrecedence = 1.0 / normalizedFrequency;
		double modulePrecedence = ExecutionReport.calculatePrecedence(20, moduleCount);

		// 50% at 5, saturate at 25
		// normalizedFrequency = Math.min(1.0, 1.4 / Math.log10((double) nodeCount));
		// double nodePrecedence = 1.0 / normalizedFrequency;
		double nodePrecedence = ExecutionReport.calculatePrecedence(5, nodeCount);

		return Math.max(modulePrecedence, nodePrecedence);
	}

	private double calculateUnexpectedReturnRiskScale(int intraModule, int crossModule) {
		if (intraModule == 0 || crossModule == 0)
			return 1.0;

		int nodeCrossModuleCount = 0;
		int nodeIntraModuleCount = 0;
		OrdinalEdgeList<ClusterNode<?>> edgeList = edge.getFromNode().getOutgoingEdges();
		try {
			for (Edge<ClusterNode<?>> walk : edgeList) {
				if (walk.getToNode() == edge.getToNode())
					continue;
				if (walk.getToNode().getType() == MetaNodeType.CLUSTER_EXIT)
					nodeCrossModuleCount++;
				else
					nodeIntraModuleCount++;
			}
		} finally {
			edgeList.release();
		}

		if (edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT)
			return 1.0 - calculateUnexpectedReturnPrecedenceScale(crossModule, nodeCrossModuleCount);
		else
			return 1.0 - calculateUnexpectedReturnPrecedenceScale(intraModule, nodeIntraModuleCount);
	}

	@Override
	public void setEventFrequencies(ProgramPropertyReader programFrequencies, ModulePropertyReader moduleFrequencies) {
		if (moduleFrequencies != null) {
			crossModuleUnexpectedReturns = moduleFrequencies
					.getProperty(ModuleEventFrequencies.CROSS_MODULE_UNEXPECTED_RETURNS);
			intraModuleUnexpectedReturns = moduleFrequencies
					.getProperty(ModuleEventFrequencies.INTRA_MODULE_UNEXPECTED_RETURNS);

			moduleGencodeWrite = moduleFrequencies.getProperty(ModuleEventFrequencies.GENCODE_WRITE_COUNT);
			moduleGencodePerm = moduleFrequencies.getProperty(ModuleEventFrequencies.GENCODE_PERM_COUNT);
		}

		programGencodeWrite = programFrequencies.getProperty(ProgramEventFrequencies.GENCODE_WRITE_COUNT);
		programGencodePerm = programFrequencies.getProperty(ProgramEventFrequencies.GENCODE_PERM_COUNT);

		double riskScale = 0.0;
		switch (edge.getEdgeType()) {
			case INDIRECT:
				riskScale = 0.01; // missing good data for this
				break;
			case UNEXPECTED_RETURN:
				break;
			case GENCODE_PERM:
				riskScale = calculateGencodeRiskScale(moduleGencodePerm, programGencodePerm);
				break;
			case GENCODE_WRITE:
				riskScale = calculateGencodeRiskScale(moduleGencodeWrite, programGencodeWrite);
				break;
		}
		riskIndex = (int) (riskScale * 1000.0);
	}

	@Override
	public int getRiskIndex() {
		return riskIndex;
	}

	@Override
	public void print(PrintStream out) {
		out.format("%s: %s(0x%x) -%d-> %s(0x%x)", reportEdgeType(edge.getEdgeType()),
				ExecutionReport.getModuleName(edge.getFromNode()), ExecutionReport.getId(edge.getFromNode()),
				edge.getOrdinal(), ExecutionReport.getModuleName(edge.getToNode()),
				ExecutionReport.getId(edge.getToNode()));
	}
}
