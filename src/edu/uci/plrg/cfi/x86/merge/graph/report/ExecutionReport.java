package edu.uci.plrg.cfi.x86.merge.graph.report;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.EdgeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;
import edu.uci.plrg.cfi.x86.merge.graph.report.ProgramEventFrequencies.ProgramPropertyReader;

public class ExecutionReport {

	private static class RiskSorter implements Comparator<ReportEntry> {

		private static final RiskSorter INSTANCE = new RiskSorter();

		@Override
		public int compare(ReportEntry first, ReportEntry second) {
			if (first == second)
				return 0;

			int result = second.getRiskIndex() - first.getRiskIndex();
			if (result != 0)
				return result;

			if (first.hashCode() > second.hashCode())
				return 1;
			else
				return -1;
		}
	}

	static String getModuleName(ModuleNode<?> node) {
		switch (node.getType()) {
			case MODULE_ENTRY:
				return "Module entry from ";
			case MODULE_EXIT:
				return "Module exit to ";
			case SINGLETON:
				return "JIT singleton ";
			case TRAMPOLINE:
				return "Dynamic standalone ";
			default:
				return node.getModule().filename;
		}

	}

	static long getId(ModuleNode<?> node) {
		switch (node.getType()) {
			case MODULE_ENTRY:
			case MODULE_EXIT:
				// ideally show hash source: { <module>!export, <module>!callback, <module>!main }
				return node.getHash();
			default:
				return node.getRelativeTag();
		}
	}

	static boolean isReportedEdgeType(EdgeType type) {
		switch (type) {
			case UNEXPECTED_RETURN:
			case GENCODE_PERM:
			case GENCODE_WRITE:
				return true;
			case INDIRECT:
			case DIRECT:
			case CALL_CONTINUATION:
			case EXCEPTION_CONTINUATION:
				return false;
		}
		throw new IllegalArgumentException("Unknown EdgeType " + type);
	}

	static double calculatePrecedence(int median, int observed) {
		double medianScale = Math.log10(median);
		double observedScale = (observed < 2) ? 0.01 : Math.log10(observed);
		double observedFrequency = Math.min(1.0, observedScale / (medianScale * 2.0));
		return observedFrequency;
	}

	private List<ReportEntry> entries = new ArrayList<ReportEntry>();
	// private Set<Edge<ModuleNode<?>>> filteredEdges = new HashSet<Edge<ModuleNode<?>>>();

	private final ProgramEventFrequencies.ProgramPropertyReader programEventFrequencies;
	private ModuleEventFrequencies.ModulePropertyReader currentModuleEventFrequencies = null;

	public ExecutionReport(ProgramPropertyReader programEventFrequencies) {
		this.programEventFrequencies = programEventFrequencies;
	}

	void setCurrentModule(String moduleName) {
		currentModuleEventFrequencies = programEventFrequencies.getModule(moduleName);
		if (currentModuleEventFrequencies == null)
			Log.log("No module event frequencies for %s", moduleName);
		else
			Log.log("Found module event frequencies for %s", moduleName);
	}

	public void sort() {
		Collections.sort(entries, RiskSorter.INSTANCE);
	}

	public void print(File outputFile) throws FileNotFoundException {
		PrintStream out = new PrintStream(outputFile);
		for (ReportEntry entry : entries) {
			// if (entry instanceof NewEdgeReport && filteredEdges.contains(((NewEdgeReport) entry).edge))
			// continue;
			out.format("%04d ", entry.getRiskIndex());
			entry.print(out);
			out.println();
		}
	}

	void addEntry(ReportEntry entry) {
		entries.add(entry);

		entry.setEventFrequencies(programEventFrequencies, currentModuleEventFrequencies);
	}

	// void filterEdgeReport(Edge<ModuleNode<?>> edge) {
	// filteredEdges.add(edge);
	// }
}
