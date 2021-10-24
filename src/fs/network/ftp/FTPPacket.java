package fs.network.ftp;

import fs.network.packet.Packet;
import io.vertx.core.net.NetSocket;

public abstract class FTPPacket implements Packet {
    protected NetSocket socket;
    
    public void attachSocket(NetSocket socket) {
        this.socket = socket;
    }
    
    public NetSocket getSocket() {
        return socket;
    }
}
