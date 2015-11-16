package edu.uci.eecs.crowdsafe.merge.graph.report;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSGE;

public class SuspiciousGencodeReport implements ReportEntry {

	private final ClusterSGE sge;

	SuspiciousGencodeReport(ClusterSGE sge) {
		this.sge = sge;
	}

	@Override
	public void print() {
		Log.log("Suspicious entry into dynamic code %s(0x%x) -%d-> %s(0x%x)",
				sge.edge.getFromNode().getModule().unit.filename, sge.edge.getFromNode().getRelativeTag(), sge.edge
						.getOrdinal(), sge.edge.getToNode().getModule().unit.filename, sge.edge.getFromNode()
						.getRelativeTag());
	}
}
