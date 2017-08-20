package awesome.console;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AwesomeProjectFilesIterator implements ContentIterator {
	private final Map<String, List<VirtualFile>> fileCache;
	private final Map<String, List<VirtualFile>> fileBaseCache;
    public static String normalizePath(String s) {
        return s.replace("file://", "").replace( '/', File.separatorChar).replace('\\', File.separatorChar);
    }
	public AwesomeProjectFilesIterator(final Map<String, List<VirtualFile>> fileCache, final Map<String, List<VirtualFile>> fileBaseCache) {
		this.fileCache = fileCache;
		this.fileBaseCache = fileBaseCache;
	}

	@Override
	public boolean processFile(final VirtualFile file) {
		if (!file.isDirectory()) {
            /** cache for full file name */
			final String filename = normalizePath(file.getName());
			if (!fileCache.containsKey(filename)) {
				fileCache.put(filename, new ArrayList<VirtualFile>());
			}
			fileCache.get(filename).add(file);
			/** cache for basename (fully qualified class names) */
			final String basename = file.getNameWithoutExtension();
			if (0 >= basename.length()) {
				return true;
			}
			if (!fileBaseCache.containsKey(basename)) {
				fileBaseCache.put(basename, new ArrayList<VirtualFile>());
			}
			fileBaseCache.get(basename).add(file);
		}

		return true;
	}
}
