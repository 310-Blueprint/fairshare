package nz.ac.auckland.se310.fairshare.dto;

import nz.ac.auckland.se310.fairshare.service.ExportFormat;

/**
 * #12: a generated export ready to download. A class rather than a record, since a record's
 * equals would compare the byte array by reference.
 */
public final class ExportedFile {

    private final String filename;
    private final ExportFormat format;
    private final byte[] content;

    public ExportedFile(String filename, ExportFormat format, byte[] content) {
        this.filename = filename;
        this.format = format;
        this.content = content;
    }

    public String filename() { return filename; }
    public ExportFormat format() { return format; }
    public byte[] content() { return content; }
}
