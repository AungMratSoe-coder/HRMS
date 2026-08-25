package com.ams.hrms.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import com.ams.hrms.exception.ValidationException;

/**
 * Profile-picture preprocessing: decodes an uploaded image, center-crops it
 * to a square and downscales it to a fixed-size JPEG thumbnail so stored
 * avatars stay tiny regardless of the source file (a 4000x3000 photo becomes
 * roughly 10-20 KB).
 */
public final class AvatarImages {

    /** Largest source file accepted for upload. */
    public static final int MAX_SOURCE_BYTES = 8 * 1024 * 1024;

    /** Edge length of the stored square JPEG thumbnail. */
    public static final int THUMBNAIL_SIZE = 256;

    private AvatarImages() {
    }

    /**
     * Validates arbitrary uploaded image bytes and converts them into a
     * square JPEG thumbnail of {@link #THUMBNAIL_SIZE}px.
     *
     * @throws ValidationException when the data is empty, oversized or not a
     *                             decodable image
     */
    public static byte[] squareThumbnail(byte[] source) {
        if (source == null || source.length == 0) {
            throw new ValidationException(List.of("The selected picture is empty."));
        }
        if (source.length > MAX_SOURCE_BYTES) {
            throw new ValidationException(List.of(
                    "The picture must be smaller than "
                            + (MAX_SOURCE_BYTES / (1024 * 1024)) + " MB."));
        }
        BufferedImage decoded = decode(source);
        if (decoded == null) {
            throw new ValidationException(List.of(
                    "That file is not a supported image (use JPG, PNG, GIF or BMP)."));
        }

        BufferedImage square = centerCropSquare(decoded);
        BufferedImage scaled = scaleTo(square, THUMBNAIL_SIZE);
        return encodeJpeg(scaled);
    }

    private static BufferedImage decode(byte[] source) {
        try {
            return ImageIO.read(new ByteArrayInputStream(source));
        } catch (IOException e) {
            return null;
        }
    }

    /** Largest centered square crop of the source image. */
    private static BufferedImage centerCropSquare(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        return source.getSubimage(x, y, side, side);
    }

    private static BufferedImage scaleTo(BufferedImage source, int size) {
        // TYPE_INT_RGB: JPEG has no alpha channel; avoids pink-fringe artifacts.
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG encoder available");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.85f);
            try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(image, null, null), params);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("JPEG encoding failed", e);
        } finally {
            writer.dispose();
        }
    }
}
