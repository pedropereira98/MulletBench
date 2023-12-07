package pt.haslab.mulletbench.database;

public class DatabaseConnectionFailedException extends Throwable {
    public DatabaseConnectionFailedException(String reason) {
        super(reason);
    }
}
