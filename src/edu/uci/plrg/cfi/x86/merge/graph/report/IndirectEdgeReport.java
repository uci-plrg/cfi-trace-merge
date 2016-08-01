package edu.uci.plrg.cfi.x86.merge.graph.report;

import java.io.PrintStream;

import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.MetaNodeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.OrdinalEdgeList;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.metadata.ModuleUIB;
import edu.uci.plrg.cfi.x86.merge.graph.report.ModuleEventFrequencies.ModulePropertyReader;
import edu.uci.plrg.cfi.x86.merge.graph.report.ProgramEventFrequencies.ProgramPropertyReader;

public class IndirectEdgeReport implements ReportEntry {

	private static int executionUibCount = 0;
	
	private final ModuleUIB uib;

	private double alpha = 0.0;

	private double riskScale;

	IndirectEdgeReport(ModuleUIB uib) {
		this.uib = uib;
	}

	@Override
	public void setEventFrequencies(ProgramPropertyReader programFrequencies, ModulePropertyReader moduleFrequencies) {

		executionUibCount++;

		double alpha = moduleFrequencies.getAlpha(ModuleEventFrequencies.UIB_COUNT);
		if (alpha < 2.0) {
			this.alpha = alpha;
		} else {
			riskScale = 0.3;
		}
	}

	@Override
	public int getRiskIndex() {
		if (alpha > 0.0) {
			double ccdf = 1 - Math.pow(executionUibCount, 1 - alpha);
			riskScale = ccdf * 0.3;
		}
		return (int) (riskScale * 1000.0);
	}

	@Override
	public void print(PrintStream out) {
		out.format("Structural indirect branch %s(0x%x) -%d-> %s(0x%x)",
				ExecutionReport.getModuleName(uib.edge.getFromNode()), ExecutionReport.getId(uib.edge.getFromNode()),
				uib.edge.getOrdinal(), ExecutionReport.getModuleName(uib.edge.getToNode()),
				ExecutionReport.getId(uib.edge.getToNode()));
	}
}
