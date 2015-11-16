package edu.uci.eecs.crowdsafe.merge.graph.report;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;

class NewEdgeReport implements ReportEntry {

	private Edge<ClusterNode<?>> edge;

	public NewEdgeReport(Edge<ClusterNode<?>> edge) {
		this.edge = edge;
	}

	private boolean isReported(EdgeType type) {
		switch (type) {
			case DIRECT:
			case INDIRECT:
			case UNEXPECTED_RETURN:
			case GENCODE_PERM:
			case GENCODE_WRITE:
				return true;
			case CALL_CONTINUATION:
			case EXCEPTION_CONTINUATION:
				return false;
		}
		throw new IllegalArgumentException("Unknown EdgeType " + type);
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
	public void print() {
		if (isReported(edge.getEdgeType())) {
			Log.log("Edge [%s] %s(0x%x) -%d-> %s(0x%x)", reportEdgeType(edge.getEdgeType()), edge.getFromNode()
					.getModule().unit.filename, edge.getFromNode().getRelativeTag(), edge.getOrdinal(), edge
					.getToNode().getModule().unit.filename, edge.getToNode().getRelativeTag());
		}
	}
}
