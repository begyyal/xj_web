package begyyal.web.exception;

public class IllegalFormatException extends Exception {
    private static final long serialVersionUID = 1L;

    public IllegalFormatException(String msg) {
	super(msg);
    }

    public IllegalFormatException() {
	super("Unexpected format is found.");
    }
}
