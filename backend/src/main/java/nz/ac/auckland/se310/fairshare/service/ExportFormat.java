package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.exception.InvalidExportFormatException;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** #12: the file formats a group's expense data can be exported as. */
public enum ExportFormat {
    CSV(new MediaType("text", "csv", StandardCharsets.UTF_8), "csv"),
    PDF(MediaType.APPLICATION_PDF, "pdf");

    private final MediaType mediaType;
    private final String extension;

    ExportFormat(MediaType mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public MediaType mediaType() { return mediaType; }
    public String extension() { return extension; }

    /** Parses the {@code format} query parameter case-insensitively, rejecting anything else with a 400. */
    public static ExportFormat fromParameter(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidExportFormatException(value);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidExportFormatException(value);
        }
    }
}
