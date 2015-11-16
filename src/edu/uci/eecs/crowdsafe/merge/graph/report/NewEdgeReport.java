package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;

class NewEdgeReport implements ReportEntry {

	private Edge<ClusterNode<?>> edge;

	public NewEdgeReport(Edge<ClusterNode<?>> edge) {
		this.edge = edge;
	}

	private String reportEdgeType(EdgeType type) {
		switch (type) {
			case DIRECT:
				return "direct";
			case INDIRECT:
				return "indirect";
			case UNEXPECTED_RETURN:
				return "incorrect return";
			case CALL_CONTINUATION:
				return "";
			case EXCEPTION_CONTINUATION:
				return "";
			case GENCODE_PERM:
				return "gencode chmod";
			case GENCODE_WRITE:
				return "gencode write";
		}
		throw new IllegalArgumentException("Unknown EdgeType " + type);
	}

	@Override
	public void print(PrintStream out) {
		out.format("Edge [%s] %s(0x%x) -%d-> %s(0x%x)", reportEdgeType(edge.getEdgeType()),
				ExecutionReport.getModuleName(edge.getFromNode()), ExecutionReport.getId(edge.getFromNode()),
				edge.getOrdinal(), ExecutionReport.getModuleName(edge.getToNode()),
				ExecutionReport.getId(edge.getToNode()));
	}
}
