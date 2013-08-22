package edu.uci.eecs.crowdsafe.analysis.merge.set;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import edu.uci.eecs.crowdsafe.analysis.util.AnalysisUtil;
import edu.uci.eecs.crowdsafe.util.log.Log;

public class ProgOverlap {

	public Map<Vector<Integer>, HashSet<Long>> setMap = new HashMap<Vector<Integer>, HashSet<Long>>();

	public Set<Long>[] hashes;

	public Map<Long, Integer> freqTable;
	public Set<Long> union;
	public int progNum = 0;

	public Set<Long>[] transHashes;
	public Set<Long> transUnion;
	// public Hashtable<Long, Points> transTable;

	public String[] progNames;
	public File[] hashsetFiles;

	// private static String execFileDir =
	// "/home/peizhaoo/crowd-safe-dynamorio-launcher/crowd-safe-dynamorio-launcher/stats/";
	// private static String execFile = execFileDir +
	// "tar.hashlog.2013-01-15.19-08-35.dat";

	public ProgOverlap(int length) {
		progNames = new String[length];
		hashsetFiles = new File[length];
		hashes = (HashSet<Long>[]) new HashSet[length];
	}

	public static void main(String[] args) {

		ProgOverlap po = new ProgOverlap(args.length);

		for (int i = 0; i < args.length; i++) {
			// File f = new File(args[i]);
			// po.progNames[i] = AnalysisUtil.getProgName(f.getName());
			// po.hashsetFilenames[i] = args[i] + "/total_hashes.dat";

			po.progNames[i] = args[i].substring(0, args[i].indexOf('.')) + i;
			po.hashsetFiles[i] = new File(args[i]);
		}

		// File outputDir = new File(args[args.length - 1]);
		po.loadHashSets(po.hashsetFiles);
		po.outputOverlapInfo();
		// po.outputTransGraph();

		// HashSet<Long> runSet =
		// AnalysisUtil.initSetFromFile(ProgOverlap.execFile);
		// po.classifyProg(runSet);
	}

	public void outputOverlapInfo() {
		Log.log("total hashes of each program : ");
		for (int i = 0; i < this.hashes.length; i++) {
			Log.log(this.progNames[i] + " : " + this.hashes[i].size());
		}

		Log.log();
		Log.log("total hashes unoutputSetGraphion : " + union.size());
		for (Vector<Integer> vi : setMap.keySet()) {
			for (int i = 0; i < vi.size(); i++) {
				if (i != vi.size() - 1)
					System.out.print(progNames[vi.get(i)] + " & ");
				else
					System.out.print(progNames[vi.get(i)] + " : ");
			}
			Log.log(setMap.get(vi).size());
		}

		Log.log("Mutual overlap:");
		outputMutualOverlap();
	}

	// public void outputTransGraph() {
	// for (int i = 0; i < hashes.length; i++) {
	// for (Long l : hashes[i]) {
	// Log.log(transTable.get(l).x + "\t" + transTable.get(l).y);
	// }
	// Log.log();
	// }
	// }

	public void outputMutualOverlap() {
		for (int i = 0; i < hashes.length; i++) {
			for (int j = i + 1; j < hashes.length; j++) {
				Log.log(AnalysisUtil.intersection(hashes[i], hashes[j]).size());
			}
		}
	}

	public HashMap<Vector<Integer>, Integer> classifyProg(HashSet<Long> set) {

		Log.log("Hashes for this run: " + set.size());

		HashMap<Vector<Integer>, Integer> distributionMap = new HashMap<Vector<Integer>, Integer>();

		Vector<Long> newHash = new Vector<Long>();
		for (Long l : set) {

			int newFlag = 1;
			for (Vector<Integer> iv : this.setMap.keySet()) {
				if (this.setMap.get(iv).contains(l)) {
					if (!distributionMap.containsKey(iv))
						distributionMap.put(iv, 0);
					distributionMap.put(iv, distributionMap.get(iv) + 1);
					newFlag = 0;
				}
			}

			if (newFlag == 1) {
				Vector<Integer> vi = new Vector<Integer>();
				if (!distributionMap.containsKey(vi))
					distributionMap.put(vi, 0);
				distributionMap.put(vi, distributionMap.get(vi) + 1);
				newHash.add(l);
			}
		}

		for (Vector<Integer> vi : distributionMap.keySet()) {
			if (vi.size() == 0) {
				Log.log("New hashes found : " + distributionMap.get(vi));
				continue;
			}
			for (int i = 0; i < vi.size(); i++) {
				if (i != vi.size() - 1)
					System.out.print(progNames[vi.get(i)] + " & ");
				else
					System.out.print(progNames[vi.get(i)] + " : ");
			}
			Log.log(distributionMap.get(vi));
		}

		return distributionMap;
	}

	public void loadFreqTable(File[] files) {
		freqTable = new HashMap<Long, Integer>();

		for (int i = 0; i < files.length; i++) {
			hashes[i] = AnalysisUtil.loadHashSet(files[i]);
			for (Long l : hashes[i]) {
				if (freqTable.keySet().contains(l)) {
					freqTable.put(l, freqTable.get(l) + 1);
				}

			}
		}
	}

	public void loadHashSets(File[] files) {

		progNum = files.length;

		union = new HashSet<Long>();

		for (int i = 0; i < files.length; i++) {
			hashes[i] = AnalysisUtil.loadHashSet(files[i]);
			union.addAll(hashes[i]);
		}

		for (Long l : union) {
			Vector<Integer> intvector = new Vector<Integer>();

			for (int i = 0; i < files.length; i++) {
				if (hashes[i].contains(l)) {
					intvector.add(i);

				}
			}
			if (!setMap.containsKey(intvector)) {
				setMap.put(intvector, new HashSet<Long>());
			}
			setMap.get(intvector).add(l);

		}
	}

}
