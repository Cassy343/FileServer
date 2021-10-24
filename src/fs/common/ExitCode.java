package fs.common;

public enum ExitCode {
    CONFIG_LOAD,
    NETWORK_ERROR,
    DATA_MANAGEMENT_ERROR,
    UNKNOWN_ERROR;
    
    public static final ExitCode[] VALUES = values();
    
    public int getErrorCode() {
        return ordinal() + 1;
    }
    
    public static ExitCode forErrorCode(int code) {
        return code < 1 || code > VALUES.length ? UNKNOWN_ERROR : VALUES[code - 1];
    }
}
