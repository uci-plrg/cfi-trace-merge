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

	private int riskIndex;

	SuspiciousGencodeReport(ClusterSGE sge) {
		this.sge = sge;
	}

	@Override
	public void setEventFrequencies(ProgramPropertyReader programFrequencies, ModulePropertyReader moduleFrequencies) {
		totalProgramSGEs = programFrequencies.getCount(ProgramEventFrequencies.SGE_COUNT);
		double riskScale;
		if (totalProgramSGEs == 0)
			riskScale = 1.0;
		else
			riskScale = 1.0 - ExecutionReport.calculatePrecedence(20, totalProgramSGEs);
		riskIndex = (int) (riskScale * 1000.0);
	}

	@Override
	public int getRiskIndex() {
		return riskIndex;
	}

	@Override
	public void print(PrintStream out) {
		if (sge.edge == null) {
			out.format("Suspicious entry into dynamic code.");
		} else {
			out.format("Suspicious entry into dynamic code. Stack suspicion raised by %s(0x%x) -%d-> %s(0x%x).",
					ExecutionReport.getModuleName(sge.edge.getFromNode()),
					ExecutionReport.getId(sge.edge.getFromNode()), sge.edge.getOrdinal(),
					ExecutionReport.getModuleName(sge.edge.getToNode()), ExecutionReport.getId(sge.edge.getToNode()));
		}
	}
}
