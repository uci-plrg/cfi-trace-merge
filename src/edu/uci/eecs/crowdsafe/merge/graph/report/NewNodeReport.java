package edu.uci.eecs.crowdsafe.merge.graph.report;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;

class NewNodeReport implements ReportEntry {

	private final ClusterNode<?> node;

	NewNodeReport(ClusterNode<?> node) {
		this.node = node;
	}

	@Override
	public void print() {
		Log.log("Node %s(0x%x)", node.getModule().unit.name, node.getRelativeTag());
	}
}
