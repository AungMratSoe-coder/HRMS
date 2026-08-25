package com.ams.hrms.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.exception.BusinessException;

/**
 * Profile-photo storage (spec section 10/25): files live under
 * {@code documentsRoot/photos}, the database stores the relative path.
 * Only jpg/png up to 5 MB are accepted; originals are kept as-is.
 */
public final class ImageUtils {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;

    private ImageUtils() {
    }

    /**
     * Copies {@code sourceFile} into the photo store as
     * {@code photos/employee_<id>.<ext>} and returns the path relative to the
     * documents root (what goes into the database).
     */
    public static String storeProfilePhoto(Path sourceFile, long employeeId) {
        if (!Files.isRegularFile(sourceFile)) {
            throw new BusinessException("Photo file not found: " + sourceFile,
                    "The selected photo file could not be read.");
        }
        String fileName = sourceFile.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    "Unsupported photo type: " + extension,
                    "Profile photos must be JPG or PNG.");
        }
        try {
            if (Files.size(sourceFile) > MAX_SIZE_BYTES) {
                throw new BusinessException("Photo exceeds 5 MB",
                        "Profile photos must be smaller than 5 MB.");
            }
            Path photosDir = Path.of(AppConfig.get().documentsRoot(), "photos");
            Files.createDirectories(photosDir);
            Path target = photosDir.resolve("employee_" + employeeId + "." + extension);
            Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
            return "photos/" + target.getFileName();
        } catch (IOException e) {
            throw new com.ams.hrms.exception.DataAccessException(
                    "Could not store profile photo: " + e.getMessage(), e);
        }
    }

    /** Resolves a stored relative photo path to an absolute file path. */
    public static Path resolveStoredPath(String storedRelativePath) {
        if (storedRelativePath == null || storedRelativePath.isBlank()) {
            return null;
        }
        return Path.of(AppConfig.get().documentsRoot(), storedRelativePath);
    }

    /** Reads a stored photo as bytes, or null when missing/unreadable. */
    public static byte[] readStored(String storedRelativePath) {
        Path path = resolveStoredPath(storedRelativePath);
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            return null;
        }
    }

    /** True when bytes decode as a readable image. */
    public static boolean isImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        try {
            javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
