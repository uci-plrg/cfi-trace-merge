package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterUIB;

public class SuspiciousIndirectEdgeReport implements ReportEntry {

	private final ClusterUIB suib;
	
	private int programSuspiciousEdges = 0;
	private int moduleSuspiciousEdges = 0;

	SuspiciousIndirectEdgeReport(ClusterUIB suib) {
		this.suib = suib;
		
		// 1. # other targets on this node
	}

	@Override
	public void setEventFrequencies(ModuleEventFrequencies frequencies) {
		moduleSuspiciousEdges = frequencies.getSuibCount();
	}

	@Override
	public void setEventFrequencies(ProgramEventFrequencies frequencies) {
		programSuspiciousEdges = frequencies.getSuibCount();
	}

	@Override
	public void evaluateRisk() {
	}
	
	@Override
	public int getRiskIndex() {
		return 0;
	}

	@Override
	public void print(PrintStream out) {
		out.format("Suspicious indirect branch %s(0x%x) -%d-> %s(0x%x)",
				ExecutionReport.getModuleName(suib.edge.getFromNode()), ExecutionReport.getId(suib.edge.getFromNode()),
				suib.edge.getOrdinal(), ExecutionReport.getModuleName(suib.edge.getToNode()),
				ExecutionReport.getId(suib.edge.getFromNode()));
	}
}
