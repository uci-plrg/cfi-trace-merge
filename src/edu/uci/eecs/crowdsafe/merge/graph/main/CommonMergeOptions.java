package edu.uci.eecs.crowdsafe.merge.graph.main;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import edu.uci.eecs.crowdsafe.common.config.CrowdSafeConfiguration;
import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;

class CommonMergeOptions {

	static final OptionArgumentMap.StringOption crowdSafeCommonDir = OptionArgumentMap.createStringOption('d');
	static final OptionArgumentMap.StringOption restrictedClusterOption = OptionArgumentMap.createStringOption('c');
	static final OptionArgumentMap.BooleanOption unitClusterOption = OptionArgumentMap.createBooleanOption('u', true);
	static final OptionArgumentMap.StringOption excludeClusterOption = OptionArgumentMap.createStringOption('x');

	private final OptionArgumentMap map;

	private final Set<String> explicitClusterNames = new HashSet<String>();
	private final Set<String> excludedClusterNames = new HashSet<String>();

	CommonMergeOptions(ArgumentStack args, OptionArgumentMap.Option<?>... options) {
		List<OptionArgumentMap.Option<?>> allOptions = new ArrayList<OptionArgumentMap.Option<?>>();
		for (OptionArgumentMap.Option<?> option : options) {
			allOptions.add(option);
		}
		map = new OptionArgumentMap(args, allOptions);
	}

	void parseOptions() {
		map.parseOptions();

		if (restrictedClusterOption.hasValue() && excludeClusterOption.hasValue()) {
			Log.log("Option 'cluster inclusion' (-c) and 'cluster exclusion' (-x) may not be used together. Exiting now.");
			System.exit(1);
		}
	}

	void initializeMerge() {
		CrowdSafeConfiguration.initialize(EnumSet.of(CrowdSafeConfiguration.Environment.CROWD_SAFE_COMMON_DIR));

		ConfiguredSoftwareDistributions.ClusterMode clusterMode;
		if (unitClusterOption.hasValue())
			clusterMode = ConfiguredSoftwareDistributions.ClusterMode.UNIT;
		else
			clusterMode = ConfiguredSoftwareDistributions.ClusterMode.GROUP;

		if (crowdSafeCommonDir.getValue() == null) {
			ConfiguredSoftwareDistributions.initialize(clusterMode);
		} else {
			ConfiguredSoftwareDistributions.initialize(clusterMode, new File(crowdSafeCommonDir.getValue()));
		}

		if (restrictedClusterOption.hasValue()) {
			StringTokenizer clusterNames = new StringTokenizer(restrictedClusterOption.getValue(), ",");
			while (clusterNames.hasMoreTokens()) {
				explicitClusterNames.add(clusterNames.nextToken());
			}
		} else {
			if (excludeClusterOption.hasValue()) {
				StringTokenizer clusterNames = new StringTokenizer(excludeClusterOption.getValue(), ",");
				while (clusterNames.hasMoreTokens()) {
					excludedClusterNames.add(clusterNames.nextToken());
				}
			}
		}
	}

	boolean includeCluster(AutonomousSoftwareDistribution cluster) {
		if (explicitClusterNames.isEmpty()) {
			return !excludedClusterNames.contains(cluster.name);
		}

		return explicitClusterNames.contains(cluster.name);
	}
}
