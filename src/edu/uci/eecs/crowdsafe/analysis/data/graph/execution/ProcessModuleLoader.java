package edu.uci.eecs.crowdsafe.analysis.data.graph.execution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import edu.uci.eecs.crowdsafe.analysis.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.analysis.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.analysis.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.analysis.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.analysis.datasource.ProcessTraceStreamType;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.OverlapModuleException;

public class ProcessModuleLoader {
	/**
	 * Assume the module file is organized in the follwoing way: Module USERENV.dll: 0x722a0000 - 0x722b7000
	 * 
	 * @param fileName
	 * @return
	 * @throws OverlapModuleException
	 */
	public static ProcessExecutionModuleSet loadModules(
			ProcessTraceDataSource dataSource)
			throws IOException, OverlapModuleException {
		ProcessExecutionModuleSet modules = new ProcessExecutionModuleSet();
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				dataSource.getDataInputStream(ProcessTraceStreamType.MODULE)));
		String line;
		while ((line = reader.readLine()) != null) {
			int beginIdx, endIdx;
			String name;
			long beginAddr, endAddr;

			if (!line.startsWith("Loaded")) {
				continue;
			}

			// Should change the index correspondingly if the
			// module file format is changed
			beginIdx = line.indexOf(" ");
			beginIdx = line.indexOf(" ", beginIdx + 1);
			endIdx = line.indexOf(":", 0);
			name = line.substring(beginIdx + 1, endIdx);
			SoftwareDistributionUnit unit = ConfiguredSoftwareDistributions
					.getInstance().establishUnit(name.toLowerCase());

			beginIdx = line.indexOf("x", endIdx);
			endIdx = line.indexOf(" ", beginIdx);
			beginAddr = Long
					.parseLong(line.substring(beginIdx + 1, endIdx), 16);

			beginIdx = line.indexOf("x", endIdx);
			endAddr = Long.parseLong(line.substring(beginIdx + 1), 16);

			ModuleDescriptor module = new ModuleDescriptor(unit, beginAddr,
					endAddr);
			modules.add(module);
		}
		return modules;
	}
}
