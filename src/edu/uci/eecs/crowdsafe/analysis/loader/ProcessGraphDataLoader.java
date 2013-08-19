package edu.uci.eecs.crowdsafe.analysis.loader;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import utils.AnalysisUtil;

import com.google.common.io.LittleEndianDataInputStream;

import edu.uci.eecs.crowdsafe.analysis.data.graph.Edge;
import edu.uci.eecs.crowdsafe.analysis.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.analysis.data.graph.ModuleDescriptor;
import edu.uci.eecs.crowdsafe.analysis.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.analysis.data.graph.Node;
import edu.uci.eecs.crowdsafe.analysis.data.graph.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.analysis.data.graph.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.analysis.data.graph.VersionedTag;
import edu.uci.eecs.crowdsafe.analysis.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.analysis.datasource.ProcessTraceDirectory;
import edu.uci.eecs.crowdsafe.analysis.datasource.ProcessTraceStreamType;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.InvalidGraphException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.InvalidTagException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.MultipleEdgeException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.OverlapModuleException;
import edu.uci.eecs.crowdsafe.analysis.exception.graph.TagNotFoundException;
import edu.uci.eecs.crowdsafe.analysis.merge.graph.debug.DebugUtils;

public class ProcessGraphDataLoader {

	public static ProcessExecutionGraph loadProcessGraph(File dir) {
		ProcessTraceDataSource dataSource = new ProcessTraceDirectory(dir);
		ProcessGraphLoadSession session = new ProcessGraphLoadSession(dataSource);
		return session.loadGraph();
	}
}
