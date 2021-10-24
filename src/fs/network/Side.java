package fs.network;

public enum Side {
    SERVER, CLIENT;
    
    public Side opposite() {
        return this == SERVER ? CLIENT : SERVER;
    }
}
