package edu.uci.eecs.crowdsafe.analysis.set;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.nio.*;

import utils.AnalysisUtil;

public class GenerateStats {

	private String progName = null;

	private HashMap<Integer, Integer> newHashesCounter = new HashMap<Integer, Integer>();

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
			"yy-MM-dd_HH-mm-ss");

	Set<Long> totalPairHashes = new HashSet<Long>();
	Set<Long> totalBlockHashes = new HashSet<Long>();

	Set<Long> allIntersection;

	public static void main(String[] args) {
		// if (argvs.length > 2 || argvs.length == 0) {
		// System.out.println("Usage: java GenerateStats <Target_Hashlog_Directory>");
		// System.out.println("or     java GenerateStats <Target_Hashlog_Directory> <Output_Directory>");
		// return;
		// }
		GenerateStats generateStats = new GenerateStats();
		if (args.length == 1)
			generateStats.generateStats(new File(args[0]), new File("."));
		else if (args.length == 2) {
			generateStats.generateStats(new File(args[0]), new File(args[1]));
		} else {
			generateStats.generateStats(new File(args[0]), new File(args[1]),
					new File(args[2]), new File(args[3]));
		}
	}

	private void dumpCommand(PrintStream out, String commandFile) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(commandFile));
			String cmd;
			while ((cmd = br.readLine()) != null) {
				out.println(cmd);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void generateStats(File dir, File targetDir, File prePairSetFile,
			File preBlockSetFile) {
		totalPairHashes = AnalysisUtil.loadHashSet(prePairSetFile);
		totalBlockHashes = AnalysisUtil.loadHashSet(preBlockSetFile);
		generateStats(dir, targetDir);
	}

	public void generateStats(File dir, File targetDir) {
		if (dir == null) {
			return;
		}

		PrintStream outPlot = null, outInfo = null;

		// totalPairHashes = new HashSet<Long>();
		// totalBlockHashes = new HashSet<Long>();
		int countPair = 0, countBlock = 0, runIndex = 0;

		this.progName = "<program-name>";

		Date date = new Date();
		File outputDir = new File(targetDir, this.progName + "-"
				+ GenerateStats.DATE_FORMAT.format(date));
		if (!outputDir.mkdir()) {
			System.out.println("Error: It can't create the directory!");
			return;
		}

		try {
			outPlot = new PrintStream(new FileOutputStream(
					outputDir.getAbsolutePath() + "/" + "unique_hash.dat"));
			outInfo = new PrintStream(new FileOutputStream(
					outputDir.getAbsoluteFile() + "/" + "info.dat"));
			outPlot.println("#Index\tNew-Pair\tNew-Block\tTotal-Pairs4-RuntTotal-Pairs\tTotal-Blocks\n");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		String[] programs = dir.list();

		String program = programs[0];
		File progFile = new File(dir.getAbsolutePath(), program);
		String[] runs = progFile.list();

		for (String run : runs) {
			countPair = 0;
			countBlock = 0;
			runIndex++;

			int runNumber = 0;
			try {
				runNumber = Integer.parseInt(run.substring(3));
			} catch (Exception e) {
				continue;
			}

			HashSet<Long> totalPairHashes4Run = new HashSet<Long>();
			for (int i = 0; i < programs.length; i++) {
				File fRun = new File(dir.getAbsolutePath() + "/" + programs[i]
						+ "/" + run);
				for (File file : fRun.listFiles()) {
					if (file.getName().contains("pair-hash")) {
						Set<Long> set = AnalysisUtil.loadHashSet(file);
						totalPairHashes4Run.addAll(set);
						if (allIntersection == null) {
							allIntersection = new HashSet<Long>(set);
						}
						allIntersection = AnalysisUtil.intersection(set,
								allIntersection);

						for (Long l : set) {
							if (!totalPairHashes.contains(l)) {
								totalPairHashes.add(l);
								countPair++;
								if (countPair == 1) {
									outInfo.println("----------"
											+ fRun.getAbsolutePath()
											+ "----------");
								}
								if (countPair < 50) {
									outInfo.println(Long.toHexString(l));
								}
							}
						}
					} else if (file.getName().contains("block-hash")) {
						Set<Long> set = AnalysisUtil.loadHashSet(file);

						for (Long l : set) {
							if (!totalBlockHashes.contains(l)) {
								totalBlockHashes.add(l);
								countBlock++;
							}
						}
					}
				}
			}
			if (countPair > 0) {
				outInfo.println("Number of new hashes in this run: "
						+ countPair);
				this.dumpCommand(outInfo, progFile.getAbsolutePath() + "/run"
						+ runNumber + "/command.txt");
				outInfo.println();
			}

			this.newHashesCounter.put(runNumber, countPair);
			// outPlot.println(runIndex + "\t" + Math.log(count) + "\t" +
			// Math.log(totalHashes4Run.size()));
			// outPlot.println(runIndex + "\t" + countPair + "\t" +
			// totalPairHashes4Run.size());
			// // outPlot.println("#" + runNumber + "  " + runIndex + "\t" +
			// countPair + "\t" + countBlock + "\t" +
			// totalPairHashes4Run.size() + "\t" + totalPairHashes.size()+ "\t"
			// + totalBlockHashes.size());
			outPlot.println(runIndex + "\t" + countPair + "\t" + countBlock
					+ "\t" + totalPairHashes4Run.size() + "\t"
					+ totalPairHashes.size() + "\t" + totalBlockHashes.size());
		}

		AnalysisUtil.writeSetToFile(new File(outputDir, "total_hashes.dat"),
				totalPairHashes);
		AnalysisUtil.writeSetToFile(new File(outputDir,
				"total_block_hashes.dat"), totalBlockHashes);
		AnalysisUtil.writeSetToFile(new File(outputDir,
				"intersection_hashes.dat"), allIntersection);

		outPlot.flush();
		outPlot.close();
	}

}
