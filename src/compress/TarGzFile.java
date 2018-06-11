package it.menzani.backup.compress;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public final class TarGzFile implements AutoCloseable {
    private static final PathMatcher TAR_GZ_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**.tar.gz");

    private final ArchiveOutputStream out;

    public TarGzFile(Path path) throws IOException {
        if (path == null) throw new NullPointerException("path must not be null.");
        if (!TAR_GZ_MATCHER.matches(path)) throw new IllegalArgumentException("path must be a *.tar.gz file.");

        Files.createDirectories(path.getParent());
        out = new TarArchiveOutputStream(new GzipCompressorOutputStream(new BufferedOutputStream(
                Files.newOutputStream(path))));
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    public void bundleFile(Path path) throws IOException {
        if (path == null) throw new NullPointerException("path must not be null.");
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("path must be an existing file.");

        doBundleFile(path, path.getFileName().toString());
    }

    private void doBundleFile(Path path, String fileName) throws IOException {
        ArchiveEntry entry = new TarArchiveEntry(path.toFile(), fileName);
        out.putArchiveEntry(entry);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            IOUtils.copy(in, out);
        }
        out.closeArchiveEntry();
    }

    public void bundleDirectory(Path path) throws IOException {
        if (path == null) throw new NullPointerException("path must not be null.");
        if (!Files.isDirectory(path)) throw new IllegalArgumentException("path must be an existing directory.");

        FileVisitor<Path> visitor = new DirectoryBundler(path);
        Files.walkFileTree(path, visitor);
    }

    private final class DirectoryBundler extends SimpleFileVisitor<Path> {
        private final Path path, name;

        private DirectoryBundler(Path path) {
            this.path = path;
            name = path.getFileName();
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
            String fileName = entryName(directory);

            if (!fileName.equals("")) {
                ArchiveEntry entry = new TarArchiveEntry(directory.toFile(), fileName);
                out.putArchiveEntry(entry);
                out.closeArchiveEntry();
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
            String fileName = entryName(file);

            doBundleFile(file, fileName);

            return FileVisitResult.CONTINUE;
        }

        private String entryName(Path path) {
            return name.resolve(this.path.relativize(path)).toString();
        }
    }
}
