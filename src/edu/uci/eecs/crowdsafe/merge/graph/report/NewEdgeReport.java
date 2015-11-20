package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import javax.sound.midi.spi.MidiFileReader;

import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
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

	public NewEdgeReport(Edge<ClusterNode<?>> edge) {
		this.edge = edge;
	}

	private String reportEdgeType(EdgeType type) {
		switch (type) {
			case DIRECT:
				return "direct";
			case INDIRECT:
				// 1. # other targets on this node
				// 2. # UIB in this module
				// [ 3. # indirects from this module to similar targets { CM, IM } ]
				return "indirect";
			case UNEXPECTED_RETURN:
				return "incorrect return";
			case CALL_CONTINUATION:
				return "";
			case EXCEPTION_CONTINUATION:
				return "";
			case GENCODE_PERM:
				// 1. # gencode chmod edges from this node
				return "gencode chmod";
			case GENCODE_WRITE:
				// 1. # gencode write edges from this node
				return "gencode write";
		}
		throw new IllegalArgumentException("Unknown EdgeType " + type);
	}

	@Override
	public void setEventFrequencies(ProgramPropertyReader programFrequencies, ModulePropertyReader moduleFrequencies) {
		crossModuleUnexpectedReturns = moduleFrequencies.getProperty(ModuleEventFrequencies.CROSS_MODULE_UNEXPECTED_RETURNS);
		intraModuleUnexpectedReturns = moduleFrequencies.getProperty(ModuleEventFrequencies.INTRA_MODULE_UNEXPECTED_RETURNS);

		moduleGencodeWrite = moduleFrequencies.getProperty(ModuleEventFrequencies.GENCODE_WRITE_COUNT);
		moduleGencodePerm = moduleFrequencies.getProperty(ModuleEventFrequencies.GENCODE_PERM_COUNT);

		programGencodeWrite = programFrequencies.getProperty(ProgramEventFrequencies.GENCODE_WRITE_COUNT);
		programGencodePerm = programFrequencies.getProperty(ProgramEventFrequencies.GENCODE_PERM_COUNT);
	}

	@Override
	public int getRiskIndex() {
		return 0;
	}

	@Override
	public void print(PrintStream out) {
		out.format("Edge [%s] %s(0x%x) -%d-> %s(0x%x)", reportEdgeType(edge.getEdgeType()),
				ExecutionReport.getModuleName(edge.getFromNode()), ExecutionReport.getId(edge.getFromNode()),
				edge.getOrdinal(), ExecutionReport.getModuleName(edge.getToNode()),
				ExecutionReport.getId(edge.getToNode()));
	}
}
