package awesome.console;

import awesome.console.config.AwesomeConsoleConfig;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfoFactory;
import com.intellij.ide.browsers.OpenUrlHyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AwesomeLinkFilter implements Filter {
    // Unescaped pattern: (?<filename>(?<drive>[a-z]:[\\/])?(?<folder>[\w][\w\\/.() -]*[\\/])(?<name>[\w.() -]+)\.(?<extension>[\w\\/() -]+))(?:(:|"?, line )(?<lineno>\d+))?
    private static final Pattern FILE_PATTERN = Pattern.compile("(?<filename>(?<drive>[a-z]:[\\\\/])?(?<folder>[\\w][\\w\\\\/.() -]*[\\\\/])(?<name>[\\w.() -]+)\\.(?<extension>[\\w\\\\/() -]+))(?:(:|\"?, line )(?<lineno>\\d+))?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final Pattern URL_PATTERN = Pattern.compile("((((ftp)|(file)|(https?)):/)?/[-_.!~*\\\\'()a-zA-Z0-9;\\\\/?:\\\\@&=+\\\\$,%#]+)");
	private final AwesomeConsoleConfig config;
	private final Map<String, List<VirtualFile>> fileCache;
	private final Map<String, List<VirtualFile>> fileBaseCache;
	private final Project project;
	private final List<String> srcRoots;
	private final Matcher fileMatcher;
	private final Matcher urlMatcher;
	private ProjectRootManager projectRootManager;
    private static final Logger log = Logger.getInstance("AwesomeConsoleGeneral");
    private static final Logger logCache = Logger.getInstance("AwesomeConsoleCache");
    private static final LocalFileSystem lfs = LocalFileSystem.getInstance();

    public static String padRight(String s, int n) {
        return String.format("%1$-" + n + "s", s);
    }

    public static String normalizePath(String s) {
        return s.replace("file://", "").replace( '/', File.separatorChar).replace('\\', File.separatorChar);
    }

    public AwesomeLinkFilter(final Project project) {
		this.project = project;
		this.fileCache = new HashMap<>();
		this.fileBaseCache = new HashMap<>();
		projectRootManager = ProjectRootManager.getInstance(project);
		srcRoots = getSourceRoots();
		config = AwesomeConsoleConfig.getInstance();
		fileMatcher = FILE_PATTERN.matcher("");
		urlMatcher = URL_PATTERN.matcher("");

		createFileCache();
	}



	@Override
	public Result applyFilter(final String line, final int endPoint) {
		final List<ResultItem> results = new ArrayList<>();
		final int startPoint = endPoint - line.length();
		final List<String> chunks = splitLine(line);
		int offset = 0;
		for (final String chunk : chunks) {
			if (config.SEARCH_URLS) {
				results.addAll(getResultItemsUrl(chunk, startPoint + offset));
			}
			results.addAll(getResultItemsFile(chunk, startPoint + offset));
			offset += chunk.length();
		}
		return new Result(results);
	}

	public List<String> splitLine(final String line) {
		final List<String> chunks = new ArrayList<>();
		final int length = line.length();
		if (!config.LIMIT_LINE_LENGTH || config.LINE_MAX_LENGTH >= length) {
			chunks.add(line);
			return chunks;
		}
		if (!config.SPLIT_ON_LIMIT) {
			chunks.add(line.substring(0, config.LINE_MAX_LENGTH));
			return chunks;
		}
		int offset = 0;
		do {
			final String chunk = line.substring(offset, Math.min(length, offset + config.LINE_MAX_LENGTH));
			chunks.add(chunk);
			offset += config.LINE_MAX_LENGTH;
		} while (offset < length - 1);
		return chunks;
	}

	public List<ResultItem> getResultItemsUrl(final String line, final int startPoint) {
		final List<ResultItem> results = new ArrayList<>();
		urlMatcher.reset(line);
		while (urlMatcher.find()) {
			final String match = urlMatcher.group(1);
			final String file = getFileFromUrl(match);
			if (null != file && !new File(file).exists()) {
				continue;
			}
			results.add(
					new Result(
							startPoint + urlMatcher.start(),
							startPoint + urlMatcher.end(),
							new OpenUrlHyperlinkInfo(match))
			);
		}
		return results;
	}

	public String getFileFromUrl(final String url) {
		if (url.startsWith("/")) {
			return url;
		}
		final String fileUrl = "file://";
		if (url.startsWith(fileUrl)) {
			return url.substring(fileUrl.length());
		}
		return null;
	}

	public List<ResultItem> getResultItemsFile(final String line, final int startPoint) {
		final List<ResultItem> results = new ArrayList<>();
		final boolean debug = (line.contains(".js") || line.contains(".ts")) && line.contains("apEMP") && line.contains("error");
		fileMatcher.reset(line);
		final HyperlinkInfoFactory hyperlinkInfoFactory = HyperlinkInfoFactory.getInstance();
		while (fileMatcher.find()) {
			final String match =  normalizePath(fileMatcher.group("filename"));
			List<VirtualFile> matchingFiles = fileCache.get(match);
            final String matchName = fileMatcher.group("name");
            final String matchExtension = fileMatcher.group("extension");
			if (null == matchingFiles || 0 >= matchingFiles.size()) {
				matchingFiles = getResultItemsFileFromBasename(matchName);
                if (0 >= matchingFiles.size()) {
                    if (!config.alwaysMatchExtension(matchExtension)) {
                        continue;
                    }
                    VirtualFile newFile = lfs.findFileByPath(match);
                    if (newFile == null) {
                        continue;
                    }
                    matchingFiles.add(newFile);
				}
			}
			final HyperlinkInfo linkInfo = hyperlinkInfoFactory.createMultipleFilesHyperlinkInfo(
					matchingFiles,
					fileMatcher.group("lineno") == null ? 0 : Integer.parseInt(fileMatcher.group("lineno")) - 1,
					project
			);
			final Result newResult = new Result(
                    startPoint + fileMatcher.start(),
                    startPoint + fileMatcher.end(),
                    linkInfo);
            results.add(newResult);
		}
		return results;
	}



	public List<VirtualFile> getResultItemsFileFromBasename(String matchFileName) {
        final ArrayList<VirtualFile> matches = new ArrayList<>();
		final char packageSeparator = '.';
        String basename = matchFileName;
        final int index = matchFileName.lastIndexOf(packageSeparator);
		if (-1 < index) {
			basename = matchFileName.substring(index + 1);
		}
		if (0 >= basename.length()) {
			return matches;
		}
		if (!fileBaseCache.containsKey(basename)) {
			return matches;
		}
		final String path = (index == -1 ? matchFileName : matchFileName.substring(0, index)).replace(packageSeparator, File.separatorChar);
		for (final VirtualFile file : fileBaseCache.get(basename)) {
			final VirtualFile parent = file.getParent();
			if (null == parent) {
				continue;
			}
			if (!matchSource(parent.getPath(), path)) {
				continue;
			}
			matches.add(file);
		}
		return matches;
	}

	private void createFileCache() {
		projectRootManager.getFileIndex().iterateContent(new AwesomeProjectFilesIterator(fileCache, fileBaseCache));
	}

	private List<String> getSourceRoots() {
		final VirtualFile[] contentSourceRoots = projectRootManager.getContentSourceRoots();
		final List<String> roots = new ArrayList<>();
		for (final VirtualFile root : contentSourceRoots) {
            final String path = normalizePath(root.getPath());
			log.error("Source Root: " + path);
			roots.add(path);
		}
		return roots;
	}

	private boolean matchSource(String parent, String path) {
		parent = normalizePath(parent);
        path = normalizePath(path);
        for (final String srcRoot : srcRoots) {
            if (config.RELAXED_SOURCE_MATCHING) {
                if (parent.startsWith(srcRoot + File.separatorChar + path)) {
                    return true;
                }
            }
            else {
                if ((srcRoot + File.separatorChar + path).equals(parent)) {
                    return true;
                }
            }
		}
		return false;
	}
}
