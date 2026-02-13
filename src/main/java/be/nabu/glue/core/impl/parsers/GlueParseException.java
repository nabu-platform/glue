package be.nabu.glue.core.impl.parsers;

import java.text.ParseException;
import java.util.List;

public class GlueParseException extends ParseException {

    private static final long serialVersionUID = 1L;

    private final int startLine;
    private final int startColumn;
    private final int endLine;
    private final int endColumn;

    public GlueParseException(String message, int errorOffset, int startLine, int startColumn, int endLine, int endColumn) {
        super(message, errorOffset);
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
    }

    public GlueParseException(ParseException original, int startLine, int startColumn) {
        super(original.getMessage(), original.getErrorOffset());
        initCause(original);
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = startLine;
        this.endColumn = startColumn + 1;
    }

    /**
     * Constructor for creating a container exception for multiple parsing errors.
     */
    public GlueParseException(String message, List<GlueParseException> suppressedErrors) {
        super(message, -1); // No single error offset for a multi-error exception
        this.startLine = -1;
        this.startColumn = -1;
        this.endLine = -1;
        this.endColumn = -1;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

}