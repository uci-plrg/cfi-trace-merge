package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;

class NewNodeReport implements ReportEntry {

	private final ClusterNode<?> node;

	NewNodeReport(ClusterNode<?> node) {
		this.node = node;
	}

	@Override
	public void print(PrintStream out) {
		out.format("Node %s(0x%x)", ExecutionReport.getModuleName(node), ExecutionReport.getId(node));
	}
}
