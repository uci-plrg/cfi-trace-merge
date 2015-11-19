package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSSC;

// 1. # SSC in the entire program having this sysnum
// [ 2. # SSC where suspicion is raised from this module ] // not really available since it's relatively new
public class SuspiciousSyscallReport implements ReportEntry {

	private final ClusterSSC ssc;

	private int sameSuspiciousSysnumCount = 0;

	SuspiciousSyscallReport(ClusterSSC ssc) {
		this.ssc = ssc;
	}

	@Override
	public void setEventFrequencies(ModuleEventFrequencies frequencies) {
	}

	@Override
	public void setEventFrequencies(ProgramEventFrequencies frequencies) {
		sameSuspiciousSysnumCount = frequencies.getSuspiciousSysnumCount(ssc.sysnum);
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
		if (ssc.suspicionRaisingEdge == null) {
			out.format("Suspicious syscall #%d", ssc.sysnum);
		} else {
			out.format("Suspicious syscall #%d. Stack suspicion raised by %s(0x%x) -%d-> %s(0x%x).", ssc.sysnum,
					ExecutionReport.getModuleName(ssc.suspicionRaisingEdge.getFromNode()),
					ExecutionReport.getId(ssc.suspicionRaisingEdge.getFromNode()),
					ssc.suspicionRaisingEdge.getOrdinal(),
					ExecutionReport.getModuleName(ssc.suspicionRaisingEdge.getToNode()),
					ExecutionReport.getId(ssc.suspicionRaisingEdge.getToNode()));
		}
	}
}
