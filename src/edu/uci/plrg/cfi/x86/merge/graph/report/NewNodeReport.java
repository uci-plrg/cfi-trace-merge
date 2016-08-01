package edu.uci.plrg.cfi.x86.merge.graph.report;

import java.io.PrintStream;

import edu.uci.plrg.cfi.x86.graph.data.graph.MetaNodeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;
import edu.uci.plrg.cfi.x86.merge.graph.report.ModuleEventFrequencies.ModulePropertyReader;
import edu.uci.plrg.cfi.x86.merge.graph.report.ProgramEventFrequencies.ProgramPropertyReader;

class NewNodeReport implements ReportEntry {

	enum Type {
		JIT_SINGLETON,
		ABNORMAL_RETURN
	}

	private final Type type;
	private final ModuleNode<?> node;

	private int moduleAbnormalReturnCount = 0;
	private int programAbnormalReturnCount = 0;

	private int riskIndex;

	NewNodeReport(Type type, ModuleNode<?> node) {
		this.type = type;
		this.node = node;
	}

	@Override
	public void setEventFrequencies(ProgramPropertyReader programFrequencies, ModulePropertyReader moduleFrequencies) {
		if (moduleFrequencies != null)
			moduleAbnormalReturnCount = moduleFrequencies.getCount(ModuleEventFrequencies.ABNORMAL_RETURNS);

		double riskScale;
		if (node.getType() == MetaNodeType.RETURN) {
			double programPrecedence = ExecutionReport.calculatePrecedence(8, programAbnormalReturnCount);
			double modulePrecedence = 0.0;
			if (moduleAbnormalReturnCount > 0)
				modulePrecedence = ExecutionReport.calculatePrecedence(3, moduleAbnormalReturnCount);

			riskScale = 2.0 / (programPrecedence + modulePrecedence);
		} else {
			riskScale = 1.0; // this generally seems like a problem
		}
		riskIndex = (int) (riskScale * 1000.0);
	}

	@Override
	public int getRiskIndex() {
		return riskIndex;
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
