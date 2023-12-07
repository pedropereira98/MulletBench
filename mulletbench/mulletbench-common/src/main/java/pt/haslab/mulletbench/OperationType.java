package pt.haslab.mulletbench;

public enum OperationType {
    INSERT,
    FAILED_INSERT,
    FILTER,
    FAILED_FILTER,
    OUTLIER_FILTER,
    FAILED_OUTLIER_FILTER,
    AGGREGATION,
    FAILED_AGGREGATION,
    DOWNSAMPLING,
    FAILED_DOWNSAMPLING;

    public boolean isValidInsert(){
        return this.equals(INSERT);
    }

    public boolean isFailedInsert(){
        return this.equals(FAILED_INSERT);
    }

    public boolean isValidQuery(){
        return this.equals(OUTLIER_FILTER) || this.equals(FILTER) || this.equals(DOWNSAMPLING) || this.equals(AGGREGATION);
    }

    public boolean isFailedQuery(){
        return this.equals(FAILED_OUTLIER_FILTER) || this.equals(FAILED_FILTER) || this.equals(FAILED_DOWNSAMPLING) || this.equals(FAILED_AGGREGATION);
    }
}
