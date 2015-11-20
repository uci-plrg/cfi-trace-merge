package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSGE;
import edu.uci.eecs.crowdsafe.merge.graph.report.ModuleEventFrequencies.ModulePropertyReader;
import edu.uci.eecs.crowdsafe.merge.graph.report.ProgramEventFrequencies.ProgramPropertyReader;

// 1. # sge where suspicion is raised in this module
// [ 2. # sge from this program ] // not really available since it's relatively new
public class SuspiciousGencodeReport implements ReportEntry {

	private final ClusterSGE sge;
	
	// [ private int moduleProgramSGEs = 0; ]
	private int totalProgramSGEs = 0;

	SuspiciousGencodeReport(ClusterSGE sge) {
		this.sge = sge;
	}
	
	@Override
	public void setEventFrequencies(ProgramPropertyReader programFrequencies, ModulePropertyReader moduleFrequencies) {
		totalProgramSGEs = programFrequencies.getProperty(ProgramEventFrequencies.SGE_COUNT);
	}

	@Override
	public int getRiskIndex() {
		return 0;
	}

	@Override
	public void print(PrintStream out) {
		out.format("Suspicious entry into dynamic code. Stack suspicion raised by %s(0x%x) -%d-> %s(0x%x).",
				ExecutionReport.getModuleName(sge.edge.getFromNode()), ExecutionReport.getId(sge.edge.getFromNode()),
				sge.edge.getOrdinal(), ExecutionReport.getModuleName(sge.edge.getToNode()),
				ExecutionReport.getId(sge.edge.getToNode()));
	}
}
