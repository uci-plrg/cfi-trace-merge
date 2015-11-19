package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;

class NewEdgeReport implements ReportEntry {

	final Edge<ClusterNode<?>> edge;

	private int moduleSameTarget = 0;
	private int programSameTarget = 0;
	
	private int crossModuleUnexpectedReturns = 0;
	private int intraModuleUnexpectedReturns = 0;
	
	private int moduleGencodeWrite = 0;
	private int programGencodeWrite = 0;

	private int moduleGencodePerm = 0;
	private int programGencodePerm = 0;

	public NewEdgeReport(Edge<ClusterNode<?>> edge) {
		this.edge = edge;
	}

	private String reportEdgeType(EdgeType type) {
		switch (type) {
			case DIRECT:
				return "direct";
			case INDIRECT:
				// 1. # other targets on this node
				// [ 3. # indirects from this module to similar targets { CM, IM } ]
				return "indirect";
			case UNEXPECTED_RETURN:
				return "incorrect return";
			case CALL_CONTINUATION:
				return "";
			case EXCEPTION_CONTINUATION:
				return "";
			case GENCODE_PERM:
				// 1. # gencode chmod edges from this node
				return "gencode chmod";
			case GENCODE_WRITE:
				// 1. # gencode write edges from this node
				return "gencode write";
		}
		throw new IllegalArgumentException("Unknown EdgeType " + type);
	}

	@Override
	public void setEventFrequencies(ModuleEventFrequencies frequencies) {
		if (edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT)
			moduleSameTarget = frequencies.getIndirectEdgeTargetCount(edge.getToNode().getHash());
		else
			moduleSameTarget = frequencies.getIndirectEdgeTargetCount((long) edge.getToNode().getRelativeTag());
		
		crossModuleUnexpectedReturns = frequencies.getCrossModuleUnexpectedReturns();
		intraModuleUnexpectedReturns = frequencies.getIntraModuleUnexpectedReturns();
		
		moduleGencodeWrite = frequencies.getGencodeWriteCount();
		moduleGencodePerm = frequencies.getGencodePermCount();
	}

	@Override
	public void setEventFrequencies(ProgramEventFrequencies frequencies) {
		if (edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT)
			programSameTarget = frequencies.getIndirectEdgeTargetCount(edge.getToNode().getHash());
		else
			programSameTarget = frequencies.getIndirectEdgeTargetCount((long) edge.getToNode().getRelativeTag());
		
		programGencodeWrite = frequencies.getGencodeWriteCount();
		programGencodePerm = frequencies.getGencodePermCount();
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
		out.format("Edge [%s] %s(0x%x) -%d-> %s(0x%x)", reportEdgeType(edge.getEdgeType()),
				ExecutionReport.getModuleName(edge.getFromNode()), ExecutionReport.getId(edge.getFromNode()),
				edge.getOrdinal(), ExecutionReport.getModuleName(edge.getToNode()),
				ExecutionReport.getId(edge.getToNode()));
	}
}
