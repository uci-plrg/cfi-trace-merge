package edu.uci.eecs.crowdsafe.analysis.datasource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ProcessTraceDirectory implements ProcessTraceDataSource {
	private static class FilePatterns {
		final Map<ProcessTraceStreamType, String> patterns = new EnumMap<ProcessTraceStreamType, String>(ProcessTraceStreamType.class);

		public FilePatterns() {
			for (ProcessTraceStreamType streamType : STREAM_TYPES) {
				patterns.put(streamType, ".*\\." + streamType.id + "\\..*");
			}
		}
	}

	private static final EnumSet<ProcessTraceStreamType> STREAM_TYPES = EnumSet
			.allOf(ProcessTraceStreamType.class);

	private static final FilePatterns FILE_PATTERNS = new FilePatterns();

	private final int processId;
	private final String processName;
	private final Map<ProcessTraceStreamType, File> files = new EnumMap<ProcessTraceStreamType, File>(
			ProcessTraceStreamType.class);

	public ProcessTraceDirectory(File dir)
			throws ProcessTraceDataSourceException {
		for (File file : dir.listFiles()) {
			if (file.isDirectory())
				continue;

			for (Map.Entry<ProcessTraceStreamType, String> entry : FILE_PATTERNS.patterns
					.entrySet()) {
				if (Pattern.matches(entry.getValue(), file.getName())) {
					if (files.containsKey(entry.getKey()))
						throw new ProcessTraceDataSourceException(
								String.format(
										"Directory %s contains multiple files of type %s: %s and %s",
										dir.getAbsolutePath(), entry.getKey(),
										file.getName(),
										files.get(entry.getKey()).getName()));
					files.put(entry.getKey(), file);
				}
			}
		}

		if (files.size() != STREAM_TYPES.size()) {
			Set<ProcessTraceStreamType> requiredTypes = STREAM_TYPES.clone();
			requiredTypes.removeAll(files.keySet());
			throw new ProcessTraceDataSourceException(String.format(
					"Required data files are missing from directory %s: %s",
					dir.getAbsolutePath(), requiredTypes));
		}

		String runSignature = files.get(ProcessTraceStreamType.BLOCK_HASH)
				.getName();
		processName = runSignature
				.substring(0, runSignature
						.indexOf(ProcessTraceStreamType.BLOCK_HASH.id) - 1);
		runSignature = runSignature.substring(runSignature.indexOf('.',
				runSignature.indexOf(ProcessTraceStreamType.BLOCK_HASH.id)));

		int lastDash = runSignature.lastIndexOf('-');
		int lastDot = runSignature.lastIndexOf('.');

		processId = Integer.parseInt(runSignature.substring(lastDash + 1,
				lastDot));
	}

	@Override
	public int getProcessId() {
		return processId;
	}

	@Override
	public String getProcessName() {
		return processName;
	}

	@Override
	public InputStream getDataInputStream(ProcessTraceStreamType streamType)
			throws IOException {
		return new FileInputStream(files.get(streamType));
	}
	
	@Override
	public String toString() {
		return processName + "-" + processId;
	}
}
