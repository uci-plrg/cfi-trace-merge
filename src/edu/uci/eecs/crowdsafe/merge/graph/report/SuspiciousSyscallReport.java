package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSSC;

public class SuspiciousSyscallReport implements ReportEntry {

	private final ClusterSSC ssc;

	SuspiciousSyscallReport(ClusterSSC ssc) {
		this.ssc = ssc;
	}

	@Override
	public void print(PrintStream out) {
		if (ssc.suspicionRaisingEdge == null) {
			out.format("Suspicious syscall #%d", ssc.sysnum);
		} else {
			out.format("Suspicious syscall #%d (stack suspicion raised by %s(0x%x) -%d-> %s(0x%x)", ssc.sysnum,
					ExecutionReport.getModuleName(ssc.suspicionRaisingEdge.getFromNode()),
					ExecutionReport.getId(ssc.suspicionRaisingEdge.getFromNode()),
					ssc.suspicionRaisingEdge.getOrdinal(),
					ExecutionReport.getModuleName(ssc.suspicionRaisingEdge.getToNode()),
					ExecutionReport.getId(ssc.suspicionRaisingEdge.getToNode()));
		}
	}
}
