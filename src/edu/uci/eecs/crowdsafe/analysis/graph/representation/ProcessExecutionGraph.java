package edu.uci.eecs.crowdsafe.analysis.graph.representation;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import utils.AnalysisUtil;

import com.google.common.io.LittleEndianDataInputStream;

import edu.uci.eecs.crowdsafe.analysis.datasource.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.analysis.datasource.ExecutionTraceDirectory;
import edu.uci.eecs.crowdsafe.analysis.datasource.ExecutionTraceStreamType;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.InvalidGraphException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.InvalidTagException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.MultipleEdgeException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.OverlapModuleException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.TagNotFoundException;
import edu.uci.eecs.crowdsafe.analysis.graph.debug.DebugUtils;
import edu.uci.eecs.crowdsafe.analysis.loader.ExecutionGraphDataLoader;

/**
 * <p>
 * This class abstracts the binary-level labeled control flow graph of any execution of a binary executable.
 * </p>
 * 
 * <p>
 * There are a few assumptions: 1. Within one execution, the tag, which is address of the block of code in the code
 * cache of DynamoRIO, can uniquely represents an actual block of code in run-time memory. This might not be true if the
 * same address has different pieces of code at different time. 2. In windows, we already have a list of known core
 * utility DLL's, which means we will match modules according to the module names plus its version number. This might
 * not be a universally true assumption, but it's still reasonable at this point. We will treat unknown modules as
 * inline code, which is part of the main graph.
 * </p>
 * 
 * <p>
 * This class will have a list of its subclass, ModuleGraph, which is the graph representation of each run-time module.
 * </p>
 * 
 * <p>
 * This class should have the signature2Node filed which maps the signature hash to the bogus signature node. The basic
 * matching strategy separates the main module and all other kernel modules. All these separate graphs have a list of
 * callbacks or export functions from other modules, which have a corresponding signature hash. For those nodes, we try
 * to match them according to their signature hash.
 * </p>
 * 
 * @author peizhaoo
 * 
 */

public class ProcessExecutionGraph {
	private final Set<Long> totalBlockHashes = new HashSet<Long>();

	// Represents the list of core modules
	private final Map<String, ModuleGraph> moduleGraphs = new HashMap<String, ModuleGraph>();

	// Used to normalize the tag in a single graph
	protected final List<ModuleDescriptor> modules;

	public final ExecutionTraceDataSource dataSource;

	/**
	 * The construction constructs the ExecutionGraph from a variety of files located in the run directory
	 * 
	 * @param intraModuleEdgeFiles
	 *            <p>
	 *            The files containing all the intra-module edges in the format of (tag1-->tag) entry, which takes 16
	 *            bytes
	 *            </p>
	 * @param lookupFiles
	 *            <p>
	 *            The files containing the mapping entry from a tag value to the hash code of the basic block
	 * @param crossModuleEdgeFile
	 *            <p>
	 *            The files containing all the cross-module edges in the format of (tag-->tag, Signiture Hash) entry,
	 *            which takes 24 bytes
	 *            </p>
	 */
	public ProcessExecutionGraph(ExecutionTraceDataSource dataSource) {
		this.dataSource = dataSource;

		try {
			modules = ExecutionGraphDataLoader.loadModules(dataSource);
		} catch (OverlapModuleException e) {
			e.printStackTrace();
		}

		Map<VersionedTag, Node> hashLookupTable;
		try {
			// Construct the tag--hash lookup table
			hashLookupTable = loadGraphNodes(dataSource);
			// Then read both intra- and cross- module edges from files
			readIntraModuleEdges(dataSource, hashLookupTable);
			readCrossModuleEdges(dataSource, hashLookupTable);
		} catch (InvalidTagException e) {
			throw new InvalidGraphException(e);
		} catch (TagNotFoundException e) {
			throw new InvalidGraphException(e);
		} catch (MultipleEdgeException e) {
			throw new InvalidGraphException(e);
		}

		// Some other initialization and sanity checks
		validate();
		// Produce some analysis result for the graph
		analyzeGraph();
	}

	public List<ModuleDescriptor> getModules() {
		return modules;
	}

	public Map<String, ModuleGraph> getModuleGraphs() {
		return moduleGraphs;
	}

	public void addEdge(Node from, Edge e) {
		from.addOutgoingEdge(e);
	}

	public Set<Long> getTotalBlockHashes() {
		return totalBlockHashes;
	}
	
	public void normalizeTags() {
		for (ModuleGraph module : moduleGraphs.values()) {
			module.getGraphData().normalizeTags();
		}
	}

	/**
	 * Given a full module name, which is the module name with the version number, e.g. comctl32.dll-1db1446a0006000a,
	 * this function will check if this module is a core module
	 * 
	 * @param fullModuleName
	 * @return
	 */
	private boolean isCoreModule(String fullModuleName) {
		if (fullModuleName.equals("Unknown")) {
			return false;
		}
		int idx = fullModuleName.lastIndexOf('-');
		String modName = fullModuleName.substring(0, idx);
		return ModuleDescriptor.coreModuleNames.contains(modName);
	}

	public boolean isTailNode(Node n) {
		return isTailNode(n, 10);
	}

	public boolean isTailNode(Node n, int level) {
		if (level == 0) {
			return false;
		}
		ArrayList<Edge> outgoingEdges = n.getOutgoingEdges();
		if (outgoingEdges.size() == 0) {
			return true;
		}
		for (int i = 0; i < outgoingEdges.size(); i++) {
			if (!isTailNode(outgoingEdges.get(i).getToNode(), level - 1)) {
				return false;
			}
		}
		return true;
	}

	public static ProcessExecutionGraph buildGraphFromRunDir(File dir) {
		ExecutionTraceDirectory traceDirectory = new ExecutionTraceDirectory(
				dir);
		ProcessExecutionGraph graph = new ProcessExecutionGraph(traceDirectory);


		return graph;
	}

	public String toString() {
		return dataSource.getProcessName() + "-" + dataSource.getProcessId();
	}
}