package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleUIB;
import edu.uci.eecs.crowdsafe.merge.graph.report.ModuleEventFrequencies.ModulePropertyReader;
import edu.uci.eecs.crowdsafe.merge.graph.report.ProgramEventFrequencies.ProgramPropertyReader;

public class SuspiciousIndirectEdgeReport implements ReportEntry {

	private static int executionSuibCount = 0; // hazard? multiple reports in a reporter run?

	private final ModuleUIB suib;

	private int programSuspiciousEdges = 0;
	private int moduleSuspiciousEdges = 0;
	private double alpha = 0.0;

	private double riskScale;

	SuspiciousIndirectEdgeReport(ModuleUIB suib) {
		this.suib = suib;
	}

	@Override
	public void setEventFrequencies(ProgramPropertyReader programFrequencies, ModulePropertyReader moduleFrequencies) {
		if (moduleFrequencies != null)
			moduleSuspiciousEdges = moduleFrequencies.getCount(ModuleEventFrequencies.SUIB_COUNT);
		programSuspiciousEdges = programFrequencies.getCount(ProgramEventFrequencies.SUIB_COUNT);

		executionSuibCount++;

		if (suib.edge.getToNode().getType() == MetaNodeType.MODULE_EXIT) {
			riskScale = 1.0; // should never happen
		} else {
			double alpha = moduleFrequencies.getAlpha(ModuleEventFrequencies.SUIB_COUNT);
			if (alpha < 2.0) {
				Log.log("suib: alpha is %f", alpha);
				this.alpha = alpha;
			} else {
				if (programSuspiciousEdges == 0) {
					riskScale = 1.0;
				} else {
					int targetCount = 0;
					OrdinalEdgeList<ModuleNode<?>> edgeList = suib.edge.getFromNode().getOutgoingEdges();
					try {
						targetCount = edgeList.size();
					} finally {
						edgeList.release();
					}

					if (targetCount > 2) {
						riskScale = 0.0; // wonky branch
					} else {
						double programScale = 1.0 - ExecutionReport.calculatePrecedence(200, programSuspiciousEdges);
						double moduleScale = 0.7;
						if (moduleSuspiciousEdges > 0)
							moduleScale = 1.0 - ExecutionReport.calculatePrecedence(40, moduleSuspiciousEdges);
						riskScale = Math.min(programScale, moduleScale);
					}
				}
				riskScale = Math.min(0.8, riskScale);
			}
		}
	}

	@Override
	public int getRiskIndex() {
		if (alpha > 0.0) {
			double ccdf = 1.0 - Math.pow(executionSuibCount, 1.0 - alpha);
			riskScale = 0.1 + (ccdf * 0.7);
		}
		return (int) (riskScale * 1000.0);
	}

	@Override
	public void print(PrintStream out) {
		out.format("Suspicious indirect branch %s(0x%x) -%d-> %s(0x%x)",
				ExecutionReport.getModuleName(suib.edge.getFromNode()), ExecutionReport.getId(suib.edge.getFromNode()),
				suib.edge.getOrdinal(), ExecutionReport.getModuleName(suib.edge.getToNode()),
				ExecutionReport.getId(suib.edge.getToNode()));
	}
}
