package edu.uci.eecs.crowdsafe.merge.set;

import java.io.File;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.merge.util.AnalysisUtil;

public class Diff {

	public void diff(File file1, File file2, int showNewHashes) {
		Set<Long> set1 = AnalysisUtil.loadHashSet(file1), set2 = AnalysisUtil.loadHashSet(file2);

		if (set1.containsAll(set2) && set2.containsAll(set1))
			Log.log("No new hash codes found in set " + file1 + "!");
		else {
			Log.log("The two sets are not the same!");
			Set<Long> s1_s2 = AnalysisUtil.minus(set1, set2), s2_s1 = AnalysisUtil.minus(set2, set1);
			Log.log("size of set1: " + set1.size());
			Log.log("size of set2: " + set2.size());
			Log.log("intersection size = " + AnalysisUtil.intersection(set1, set2).size());
			Log.log("Number of new hash codes: " + s1_s2.size());
			if (showNewHashes == 1) {
				for (Long l : s1_s2) {
					Log.log(Long.toHexString(l));
				}
			}
		}
	}

	static public void printDiffUsage() {
		Log.log("Usage: 1. java Diff <from_hashset> <to_hashset>");
		Log.log("       2. java Diff -a <from_hashset> <to_hashset>");
		Log.log("       3. java Diff -s <from_hashset> <to_hashset>");
	}

	public void addToSet(File from, File to) {
		Set<Long> fromSet = AnalysisUtil.loadHashSet(from), toSet = AnalysisUtil.loadHashSet(to);
		toSet.addAll(fromSet);
		AnalysisUtil.writeSetToFile(to, toSet);
	}

	public static void main(String[] args) {
		Diff d = new Diff();

		File from = null, to = null;
		if (args.length == 3) {
			from = new File(args[1]);
			to = new File(args[2]);
			if (args[0].equals("-a")) {
				d.addToSet(from, to);
				Log.log("Successfully add " + from + " to " + to + ".");
			} else if (args[0].equals("-s")) {
				d.diff(from, to, 1);
			} else {
				Diff.printDiffUsage();
				return;
			}
		} else if (args.length == 2) {
			from = new File(args[0]);
			to = new File(args[1]);
			d.diff(from, to, 0);
		} else {
			Diff.printDiffUsage();
		}
		return;
	}

}
