package edu.uci.plrg.cfi.x86.merge.graph.report;

import java.io.PrintStream;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.x86.graph.data.graph.anonymous.AnonymousGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.anonymous.ModuleAnonymousGraphs;
import edu.uci.plrg.cfi.x86.merge.graph.report.ModuleEventFrequencies.ModulePropertyReader;
import edu.uci.plrg.cfi.x86.merge.graph.report.ProgramEventFrequencies.ProgramPropertyReader;

public class NewStandaloneReport implements ReportEntry {

	private final ModuleAnonymousGraphs module;
	private final AnonymousGraph box;
	private final int expandFrom;

	private int programStandalones = 0;
	private int moduleStandalones = 0;

	private int riskIndex;

	NewStandaloneReport(ModuleAnonymousGraphs module, AnonymousGraph box) {
		this(module, box, 0);
	}

	NewStandaloneReport(ModuleAnonymousGraphs module, AnonymousGraph box, int expandFrom) {
		this.module = module;
		this.box = box;
		this.expandFrom = expandFrom;
	}

	@Override
	public void setEventFrequencies(ProgramPropertyReader programFrequencies, ModulePropertyReader moduleFrequencies) {
		programStandalones = programFrequencies.getCount(ProgramEventFrequencies.SDR_COUNT);
		if (moduleFrequencies != null) {
			moduleStandalones = moduleFrequencies.getCount(ModuleEventFrequencies.SDR_COUNT,
					programFrequencies.getModuleId(module.owningModule.name));
		}

		double programPrecedence = ExecutionReport.calculatePrecedence(200, programStandalones);
		double modulePrecedence = 0.0;
		if (moduleStandalones > 0)
			modulePrecedence = ExecutionReport.calculatePrecedence(50, moduleStandalones);

		Log.log("Program precedence: %f (%d); module precedence: %f (%d)", programPrecedence, programStandalones,
				modulePrecedence, moduleStandalones);

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
			out.format("Dynamic standalone owned by %s expanded from %d to %d nodes", module.owningModule.name,
					expandFrom, box.getNodeCount());
		} else {
			out.format("Dynamic standalone of size %d nodes owned by %s", box.getNodeCount(), module.owningModule.name);
		}
	}
}
