package edu.uci.eecs.crowdsafe.merge.graph.report;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSSC;

public class SuspiciousSyscallReport implements ReportEntry {

	private final ClusterSSC ssc;

	SuspiciousSyscallReport(ClusterSSC ssc) {
		this.ssc = ssc;
	}

	@Override
	public void print() {
		Log.log("Suspicious syscall #%d (stack suspicion raised by %s(0x%x) -%d-> %s(0x%x)", ssc.sysnum,
				ssc.suspicionRaisingEdge.getFromNode().getModule().unit.filename, ssc.suspicionRaisingEdge
						.getFromNode().getRelativeTag(), ssc.suspicionRaisingEdge.getOrdinal(),
				ssc.suspicionRaisingEdge.getToNode().getModule().unit.filename, ssc.suspicionRaisingEdge.getToNode()
						.getRelativeTag());
	}
}
