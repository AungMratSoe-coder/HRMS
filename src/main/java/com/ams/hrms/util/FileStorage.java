package com.ams.hrms.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.DataAccessException;

/**
 * Employee-document storage (spec section 25): files live under
 * {@code documentsRoot/documents/<employeeId>/}, the database stores the
 * relative path plus metadata. Allowed: pdf, doc(x), jpg/png, xls(x); max
 * 10 MB per file.
 */
public final class FileStorage {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "jpg", "jpeg", "png", "xls", "xlsx");
    private static final long MAX_SIZE_BYTES = 10 * 1024 * 1024;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private FileStorage() {
    }

    /**
     * Copies {@code sourceFile} into the document store and returns the path
     * relative to the documents root (what goes into the database).
     */
    public static String storeDocument(Path sourceFile, long employeeId) {
        if (!Files.isRegularFile(sourceFile)) {
            throw new BusinessException("File not found: " + sourceFile,
                    "The selected file could not be read.");
        }
        String original = sourceFile.getFileName().toString();
        String extension = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    "Unsupported file type: " + extension,
                    "Allowed document types: PDF, Word, Excel, JPG, PNG.");
        }
        try {
            if (Files.size(sourceFile) > MAX_SIZE_BYTES) {
                throw new BusinessException("File exceeds 10 MB",
                        "Documents must be smaller than 10 MB.");
            }
            Path directory = Path.of(AppConfig.get().documentsRoot(),
                    "documents", String.valueOf(employeeId));
            Files.createDirectories(directory);
            String safeName = original.replaceAll("[^A-Za-z0-9._-]", "_");
            String storedName = LocalDateTime.now().format(STAMP) + "_" + safeName;
            Path target = directory.resolve(storedName);
            Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
            return "documents/" + employeeId + "/" + storedName;
        } catch (IOException e) {
            throw new DataAccessException("Could not store document: " + e.getMessage(), e);
        }
    }

    /**
     * Copies a candidate resume into the document store under
     * {@code resumes/<candidateId>/} and returns the relative path.
     */
    public static String storeResume(Path sourceFile, long candidateId) {
        if (!Files.isRegularFile(sourceFile)) {
            throw new BusinessException("File not found: " + sourceFile,
                    "The selected file could not be read.");
        }
        String original = sourceFile.getFileName().toString();
        String extension = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    "Unsupported file type: " + extension,
                    "Allowed resume types: PDF, Word, JPG, PNG.");
        }
        try {
            if (Files.size(sourceFile) > MAX_SIZE_BYTES) {
                throw new BusinessException("File exceeds 10 MB",
                        "Resumes must be smaller than 10 MB.");
            }
            Path directory = Path.of(AppConfig.get().documentsRoot(),
                    "resumes", String.valueOf(candidateId));
            Files.createDirectories(directory);
            String safeName = original.replaceAll("[^A-Za-z0-9._-]", "_");
            String storedName = LocalDateTime.now().format(STAMP) + "_" + safeName;
            Files.copy(sourceFile, directory.resolve(storedName),
                    StandardCopyOption.REPLACE_EXISTING);
            return "resumes/" + candidateId + "/" + storedName;
        } catch (IOException e) {
            throw new DataAccessException("Could not store resume: " + e.getMessage(), e);
        }
    }

    /**
     * Stores generated offer-letter bytes under {@code offers/<offerId>/}
     * and returns the relative path; the letter is archived when an offer
     * is sent so a permanent record of what was sent exists.
     */
    public static String storeOfferLetter(byte[] pdfBytes, long offerId, String fileName) {
        try {
            Path directory = Path.of(AppConfig.get().documentsRoot(),
                    "offers", String.valueOf(offerId));
            Files.createDirectories(directory);
            String safeName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
            String storedName = LocalDateTime.now().format(STAMP) + "_" + safeName;
            Files.write(directory.resolve(storedName), pdfBytes);
            return "offers/" + offerId + "/" + storedName;
        } catch (IOException e) {
            throw new DataAccessException(
                    "Could not store offer letter: " + e.getMessage(), e);
        }
    }

    /** Resolves a stored relative path to an absolute file path. */
    public static Path resolve(String storedRelativePath) {
        if (storedRelativePath == null || storedRelativePath.isBlank()) {
            return null;
        }
        return Path.of(AppConfig.get().documentsRoot(), storedRelativePath);
    }

    /** Best-effort MIME type from extension (stored as metadata). */
    public static String mimeOf(String fileName) {
        String extension = extensionOf(fileName);
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "application/octet-stream";
        };
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
