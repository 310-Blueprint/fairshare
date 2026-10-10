package nz.ac.auckland.se310.fairshare.exception;

public class InvalidExportFormatException extends RuntimeException {

    public InvalidExportFormatException(String format) {
        super(format == null || format.isBlank()
                ? "An export format is required: csv or pdf"
                : "Unsupported export format: " + format + ". Use csv or pdf");
    }
}
