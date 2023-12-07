package pt.haslab.mulletbench.database;

public class FailedQueryException extends Exception {
    public FailedQueryException(String reason) {
        super(reason);
    }

}
