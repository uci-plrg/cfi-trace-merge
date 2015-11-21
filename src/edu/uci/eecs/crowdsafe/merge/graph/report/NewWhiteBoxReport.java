package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousModule;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousSubgraph;
import edu.uci.eecs.crowdsafe.merge.graph.report.ModuleEventFrequencies.ModulePropertyReader;
import edu.uci.eecs.crowdsafe.merge.graph.report.ProgramEventFrequencies.ProgramPropertyReader;

public class NewWhiteBoxReport implements ReportEntry {

	private final AnonymousModule module;
	private final AnonymousSubgraph box;

	private int programWhiteBoxes = 0;
	private int moduleWhiteBoxes = 0;

	private int riskIndex;

	NewWhiteBoxReport(AnonymousModule module, AnonymousSubgraph box) {
		this.module = module;
		this.box = box;
	}

	@Override
	public void setEventFrequencies(ProgramPropertyReader programFrequencies, ModulePropertyReader moduleFrequencies) {
		programWhiteBoxes = programFrequencies.getProperty(ProgramEventFrequencies.WHITE_BOX_COUNT);
		moduleWhiteBoxes = moduleFrequencies.getProperty(ModuleEventFrequencies.WHITE_BOX_COUNT);

		double programPrecedence = ExecutionReport.calculatePrecedence(200, programWhiteBoxes);
		double modulePrecedence = 0.0;
		if (moduleWhiteBoxes > 0)
			modulePrecedence = ExecutionReport.calculatePrecedence(50, moduleWhiteBoxes);

		double riskScale = 2.0 / (programPrecedence + modulePrecedence);
		riskScale = Math.min(0.02, riskScale);
		riskIndex = (int) (riskScale * 1000.0);
	}

	@Override
	public int getRiskIndex() {
		return riskIndex;
	}

	@Override
	public void print(PrintStream out) {
		// could add edge counts via analyze(false), then query the edge counter
		out.format("Dynamic standalone of size %d nodes owned by %s", box.getExecutableNodeCount(),
				module.owningCluster.name);
	}
}
