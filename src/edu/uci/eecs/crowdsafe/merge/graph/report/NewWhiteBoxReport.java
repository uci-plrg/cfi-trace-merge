package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousModule;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousSubgraph;
import edu.uci.eecs.crowdsafe.merge.graph.report.ModuleEventFrequencies.ModulePropertyReader;
import edu.uci.eecs.crowdsafe.merge.graph.report.ProgramEventFrequencies.ProgramPropertyReader;

public class NewWhiteBoxReport implements ReportEntry {

	private final AnonymousModule module;
	private final AnonymousSubgraph box;
	private final int expandFrom;

	private int programWhiteBoxes = 0;
	private int moduleWhiteBoxes = 0;

	private int riskIndex;

	NewWhiteBoxReport(AnonymousModule module, AnonymousSubgraph box) {
		this(module, box, 0);
	}

	NewWhiteBoxReport(AnonymousModule module, AnonymousSubgraph box, int expandFrom) {
		this.module = module;
		this.box = box;
		this.expandFrom = expandFrom;
	}

	@Override
	public void setEventFrequencies(ProgramPropertyReader programFrequencies, ModulePropertyReader moduleFrequencies) {
		programWhiteBoxes = programFrequencies.getProperty(ProgramEventFrequencies.WHITE_BOX_COUNT);
		if (moduleFrequencies != null) {
			moduleWhiteBoxes = moduleFrequencies.getProperty(ModuleEventFrequencies.WHITE_BOX_COUNT,
					programFrequencies.getModuleId(module.owningCluster.name));
		}

		double programPrecedence = ExecutionReport.calculatePrecedence(200, programWhiteBoxes);
		double modulePrecedence = 0.0;
		if (moduleWhiteBoxes > 0)
			modulePrecedence = ExecutionReport.calculatePrecedence(50, moduleWhiteBoxes);

		Log.log("Program precedence: %f (%d); module precedence: %f (%d)", programPrecedence, programWhiteBoxes,
				modulePrecedence, moduleWhiteBoxes);

		double riskScale = 1.0 - ((programPrecedence + modulePrecedence) / 2.0);
		riskScale = Math.max(0.02, riskScale);
		if (expandFrom > 0)
			riskScale /= 2.0;
		riskIndex = (int) (riskScale * 1000.0);
	}

	@Override
	public int getRiskIndex() {
		return riskIndex;
	}

	@Override
	public void print(PrintStream out) {
		// could add edge counts via analyze(false), then query the edge counter
		if (expandFrom > 0) {
			out.format("Dynamic standalone owned by %s expanded from %d to %d nodes", module.owningCluster.name,
					expandFrom, box.getNodeCount());
		} else {
			out.format("Dynamic standalone of size %d nodes owned by %s", box.getNodeCount(), module.owningCluster.name);
		}
	}
}
