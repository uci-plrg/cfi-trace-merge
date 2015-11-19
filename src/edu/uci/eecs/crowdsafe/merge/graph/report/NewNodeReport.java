package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;

class NewNodeReport implements ReportEntry {

	enum Type {
		BLACK_BOX_SINGLETON,
		ABNORMAL_RETURN
	}

	private final Type type;
	private final ClusterNode<?> node;

	private int moduleAbnormalReturnCount;
	private int programAbnormalReturnCount;

	NewNodeReport(Type type, ClusterNode<?> node) {
		this.type = type;
		this.node = node;
	}

	@Override
	public void setEventFrequencies(ModuleEventFrequencies frequencies) {
		moduleAbnormalReturnCount = frequencies.getAbnormalReturnCount();
	}

	@Override
	public void setEventFrequencies(ProgramEventFrequencies frequencies) {
	}

	@Override
	public void evaluateRisk() {
		// if (node.getType() == MetaNodeType.RETURN) {
	}
	
	@Override
	public int getRiskIndex() {
		return 0;
	}

	@Override
	public void print(PrintStream out) {
		switch (node.getType()) {
			case RETURN:
				// # abnormal returns in this module (always high priority)
				out.format("Abnormal return %s(0x%x)", ExecutionReport.getModuleName(node), ExecutionReport.getId(node));
				break;
			case SINGLETON:
				// always top priority
				out.format("JIT singleton owned by module %s", ExecutionReport.getModuleName(node));
				break;
			default:
		}
	}
}
