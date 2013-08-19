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

public class ExecutionTraceDirectory implements ExecutionTraceDataSource {
	private static class FilePatterns {
		final Map<ExecutionTraceStreamType, String> patterns = new EnumMap<ExecutionTraceStreamType, String>(ExecutionTraceStreamType.class);

		public FilePatterns() {
			for (ExecutionTraceStreamType streamType : STREAM_TYPES) {
				patterns.put(streamType, ".*\\." + streamType.id + "\\..*");
			}
		}
	}

	private static final EnumSet<ExecutionTraceStreamType> STREAM_TYPES = EnumSet
			.allOf(ExecutionTraceStreamType.class);

	private static final FilePatterns FILE_PATTERNS = new FilePatterns();

	private final int processId;
	private final String processName;
	private final Map<ExecutionTraceStreamType, File> files = new EnumMap<ExecutionTraceStreamType, File>(
			ExecutionTraceStreamType.class);

	public ExecutionTraceDirectory(File dir)
			throws ExecutionTraceDataSourceException {
		for (File file : dir.listFiles()) {
			if (file.isDirectory())
				continue;

			for (Map.Entry<ExecutionTraceStreamType, String> entry : FILE_PATTERNS.patterns
					.entrySet()) {
				if (Pattern.matches(entry.getValue(), file.getName())) {
					if (files.containsKey(entry.getKey()))
						throw new ExecutionTraceDataSourceException(
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
			Set<ExecutionTraceStreamType> requiredTypes = STREAM_TYPES.clone();
			requiredTypes.removeAll(files.keySet());
			throw new ExecutionTraceDataSourceException(String.format(
					"Required data files are missing from directory %s: %s",
					dir.getAbsolutePath(), requiredTypes));
		}

		String runSignature = files.get(ExecutionTraceStreamType.BLOCK_HASH)
				.getName();
		processName = runSignature
				.substring(0, runSignature
						.indexOf(ExecutionTraceStreamType.BLOCK_HASH.id) - 1);
		runSignature = runSignature.substring(runSignature.indexOf('.',
				runSignature.indexOf(ExecutionTraceStreamType.BLOCK_HASH.id)));

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
	public InputStream getDataInputStream(ExecutionTraceStreamType streamType)
			throws IOException {
		return new FileInputStream(files.get(streamType));
	}
	
	@Override
	public String toString() {
		return processName + "-" + processId;
	}
}
