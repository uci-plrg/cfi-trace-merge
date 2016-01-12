package edu.uci.eecs.crowdsafe.merge.graph.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.log.LogFile;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.main.CommonMergeOptions;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeCandidate;
import edu.uci.eecs.crowdsafe.merge.graph.GraphMergeSource;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousModule;
import edu.uci.eecs.crowdsafe.merge.graph.anonymous.AnonymousModuleSet;
import edu.uci.eecs.crowdsafe.merge.graph.hash.ClusterHashMergeDebugLog;
import edu.uci.eecs.crowdsafe.merge.graph.report.ModuleEventFrequencies;
import edu.uci.eecs.crowdsafe.merge.graph.report.ProgramEventFrequencies;

public class DatasetStatisticsExporter {

	private static final OptionArgumentMap.StringOption datasetOption = OptionArgumentMap.createStringOption('d');
	private static final OptionArgumentMap.StringOption logFilenameOption = OptionArgumentMap.createStringOption('l',
			"stats-exporter.log"); // or the app name?
	private static final OptionArgumentMap.StringOption exportFilenameOption = OptionArgumentMap.createStringOption(
			'f', "dataset.statistics.properties"); // or the app name?

	private final CommonMergeOptions options;

	private long start;

	private final ClusterHashMergeDebugLog debugLog = new ClusterHashMergeDebugLog();

	private final Map<AutonomousSoftwareDistribution, Properties> moduleProperties = new HashMap<AutonomousSoftwareDistribution, Properties>();
	private final ProgramEventFrequencies programEventFrequencies = new ProgramEventFrequencies();
	Properties moduleStatistics = new Properties();

	public DatasetStatisticsExporter(CommonMergeOptions options) {
		this.options = options;
	}

	void run(ArgumentStack args, int iterationCount) {
		try {
			options.parseOptions();

			File logFile = LogFile.create(logFilenameOption.getValue(), LogFile.CollisionMode.OVERWRITE,
					LogFile.NoSuchPathMode.SKIP);
			if (logFile != null)
				Log.addOutput(logFile);

			File statisticsFile = LogFile.create(exportFilenameOption.getValue(), LogFile.CollisionMode.AVOID,
					LogFile.NoSuchPathMode.ERROR);
			System.out.println("Generating statistics file " + statisticsFile.getAbsolutePath());

			String datasetPath = datasetOption.getValue();

			if (args.size() > 0)
				Log.log("Ignoring %d extraneous command-line arguments", args.size());

			Log.log("Exporting dataset statistics for dataset %s", datasetPath);

			options.initializeGraphEnvironment();

			start = System.currentTimeMillis();

			extractProperties(loadMergeCandidate(datasetPath));

			FileOutputStream out = new FileOutputStream(statisticsFile);
			moduleStatistics.save(out, "");
		} catch (Log.OutputException e) {
			e.printStackTrace();
		} catch (Throwable t) {
			Log.log("\t@@@@ Dataset statistics export failed with %s @@@@", t.getClass().getSimpleName());
			Log.log(t);
			System.err.println(String.format("!! Dataset statistics export failed with %s !!", t.getClass()
					.getSimpleName()));
		}
	}

	private void extractProperties(GraphMergeCandidate dataset) throws IOException {
		int moduleId = 0;
		List<ModuleGraphCluster<ClusterNode<?>>> anonymousGraphs = new ArrayList<ModuleGraphCluster<ClusterNode<?>>>();
		Map<AutonomousSoftwareDistribution, ModuleEventFrequencies> moduleEventMap = new HashMap<AutonomousSoftwareDistribution, ModuleEventFrequencies>();
		for (AutonomousSoftwareDistribution cluster : dataset.getRepresentedClusters()) {
			if (cluster.isAnonymous()) {
				anonymousGraphs.add((ModuleGraphCluster<ClusterNode<?>>) dataset.getClusterGraph(cluster));
				continue;
			}

			ModuleGraphCluster<ClusterNode<?>> graph = (ModuleGraphCluster<ClusterNode<?>>) dataset
					.getClusterGraph(cluster);
			ModuleEventFrequencies moduleEventFrequencies = new ModuleEventFrequencies(moduleId++);
			moduleEventMap.put(cluster, moduleEventFrequencies);
			moduleEventFrequencies.extractStatistics(graph, programEventFrequencies);
			programEventFrequencies.countMetadataEvents(graph.metadata);
			moduleStatistics.setProperty(cluster.name, String.valueOf(moduleEventFrequencies.moduleId));
			System.gc();
		}

		Log.log("Dataset has %d anonymous modules", anonymousGraphs.size());
		if (!anonymousGraphs.isEmpty()) {
			AnonymousModuleSet anonymousModuleParser = new AnonymousModuleSet("<dataset>", dataset);
			AnonymousModule.initialize();
			anonymousModuleParser.installSubgraphs(GraphMergeSource.DATASET, anonymousGraphs);
			anonymousModuleParser.analyzeModules();

			for (AnonymousModule.OwnerKey owner : anonymousModuleParser.getModuleOwners()) {
				AnonymousModule module = anonymousModuleParser.getModule(owner);
				ModuleEventFrequencies moduleFrequencies = moduleEventMap.get(module.owningCluster);
				Log.log("Extract stats for anonymous module owned by %s with %d subgraphs",
						owner.cluster.getUnitFilename(), module.subgraphs.size());
				moduleFrequencies.extractStatistics(module, programEventFrequencies);
			}
		}

		for (ModuleEventFrequencies moduleEventFrequencies : moduleEventMap.values()) {
			moduleEventFrequencies.exportTo(moduleEventFrequencies.moduleId, moduleStatistics);
		}
		programEventFrequencies.exportTo(moduleStatistics);
	}

	private GraphMergeCandidate loadMergeCandidate(String path) throws IOException {
		File directory = new File(path);
		if (!(directory.exists() && directory.isDirectory())) {
			Log.log("Illegal argument '" + directory + "'; no such directory.");
			printUsageAndExit();
		}

		GraphMergeCandidate candidate = new GraphMergeCandidate.Cluster(directory, debugLog);
		candidate.loadData();
		return candidate;
	}

	private void printUsageAndExit() {
		System.out.println("Usage:");
		System.out.println(String.format("%s: -e <execution-graph> -d <dataset> -f <report-file>",
				DatasetStatisticsExporter.class.getSimpleName()));
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		DatasetStatisticsExporter main = new DatasetStatisticsExporter(new CommonMergeOptions(stack,
				CommonMergeOptions.crowdSafeCommonDir, datasetOption, logFilenameOption, exportFilenameOption));
		main.run(stack, 1);
		main.toString();
	}
}
