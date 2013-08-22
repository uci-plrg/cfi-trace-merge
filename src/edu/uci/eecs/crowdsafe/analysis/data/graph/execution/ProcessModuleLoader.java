package edu.uci.eecs.crowdsafe.analysis.data.graph.execution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.uci.eecs.crowdsafe.analysis.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.analysis.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.analysis.datasource.ProcessTraceStreamType;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.InvalidGraphException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.OverlapModuleException;

public class ProcessModuleLoader {
	private static final Pattern MODULE_PARSER = Pattern
			.compile("\\(([0-9]+),([0-9]+),([0-9]+)\\) Loaded module ([a-zA-Z_0-9<>\\-\\.\\+]+): 0x([0-9A-Fa-f]+) - 0x([0-9A-Fa-f]+)");

	/**
	 * Assume the module file is organized in the follwoing way: Module USERENV.dll: 0x722a0000 - 0x722b7000
	 * 
	 * @param fileName
	 * @return
	 * @throws OverlapModuleException
	 */
	public static ProcessExecutionModuleSet loadModules(
			ProcessTraceDataSource dataSource) throws IOException {
		ProcessExecutionModuleSet modules = new ProcessExecutionModuleSet();
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				dataSource.getDataInputStream(ProcessTraceStreamType.MODULE)));

		String line;
		while ((line = reader.readLine()) != null) {
			if (line.startsWith("Unloaded"))
				continue;
			Matcher matcher = MODULE_PARSER.matcher(line);
			if (!matcher.matches()) {
				throw new InvalidGraphException(
						"Failed to match line '%s' against the pattern--exiting now!",
						line);
			}
			long blockTimestamp = Long.parseLong(matcher.group(1));
			long edgeTimestamp = Long.parseLong(matcher.group(2));
			long crossModuleEdgeTimestamp = Long.parseLong(matcher.group(3));
			String moduleName = matcher.group(4);
			long start = Long.parseLong(matcher.group(5), 16);
			long end = Long.parseLong(matcher.group(6), 16);

			SoftwareDistributionUnit unit = ConfiguredSoftwareDistributions
					.getInstance().establishUnit(moduleName.toLowerCase());

			ModuleInstance module = new ModuleInstance(unit, start, end,
					blockTimestamp, edgeTimestamp, crossModuleEdgeTimestamp);
			modules.add(module);
		}
		return modules;
	}
}
