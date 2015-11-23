package edu.uci.eecs.crowdsafe.merge.graph.report;

import java.io.PrintStream;

import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterUIB;
import edu.uci.eecs.crowdsafe.merge.graph.report.ModuleEventFrequencies.ModulePropertyReader;
import edu.uci.eecs.crowdsafe.merge.graph.report.ProgramEventFrequencies.ProgramPropertyReader;

public class SuspiciousIndirectEdgeReport implements ReportEntry {

	private final ClusterUIB suib;

	private int programSuspiciousEdges = 0;
	private int moduleSuspiciousEdges = 0;

	private int riskIndex;

	SuspiciousIndirectEdgeReport(ClusterUIB suib) {
		this.suib = suib;

		// 1. # other targets on this node
	}

	@Override
	public void setEventFrequencies(ProgramPropertyReader programFrequencies, ModulePropertyReader moduleFrequencies) {
		if (moduleFrequencies != null)
			moduleSuspiciousEdges = moduleFrequencies.getProperty(ModuleEventFrequencies.SUIB_COUNT);
		programSuspiciousEdges = programFrequencies.getProperty(ProgramEventFrequencies.SUIB_COUNT);

		double riskScale;
		if (suib.edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT) {
			riskScale = 1.0; // should never happen
		} else {
			if (programSuspiciousEdges == 0) {
				riskScale = 1.0;
			} else {
				int targetCount = 0;
				OrdinalEdgeList<ClusterNode<?>> edgeList = suib.edge.getFromNode().getOutgoingEdges();
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
		riskIndex = (int) (riskScale * 1000.0);
	}

	@Override
	public int getRiskIndex() {
		return riskIndex;
	}

	@Override
	public void print(PrintStream out) {
		out.format("Suspicious indirect branch %s(0x%x) -%d-> %s(0x%x)",
				ExecutionReport.getModuleName(suib.edge.getFromNode()), ExecutionReport.getId(suib.edge.getFromNode()),
				suib.edge.getOrdinal(), ExecutionReport.getModuleName(suib.edge.getToNode()),
				ExecutionReport.getId(suib.edge.getFromNode()));
	}
}
