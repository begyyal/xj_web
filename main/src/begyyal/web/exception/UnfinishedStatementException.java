package begyyal.web.exception;

public class UnfinishedStatementException extends Exception {
    private static final long serialVersionUID = 1L;

    public UnfinishedStatementException(String msg) {
	super(msg);
    }

    public UnfinishedStatementException() {
	super("The source that was processed is not closed correct at grammarly.");
    }
}
