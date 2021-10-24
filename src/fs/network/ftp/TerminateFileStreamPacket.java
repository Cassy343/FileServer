package fs.network.ftp;

import fs.common.Utils;
import fs.network.packet.Packet;
import fs.network.packet.PacketHandler;
import io.vertx.core.net.NetSocket;
import stg.generic.ByteBuffer;
import stg.generic.ByteHelper;

public final class TerminateFileStreamPacket extends FTPPacket {
    private String name;
    
    public TerminateFileStreamPacket() { }
    
    public TerminateFileStreamPacket(String name) {
        this.name = name;
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        ByteHelper.writeString(name, buffer);
    }

    @Override
    public void deserialize(ByteBuffer buffer) {
        name = ByteHelper.readString(0, buffer);
    }
    
    public String getName() {
        return name;
    }
    
    public static final class Handler implements PacketHandler<TerminateFileStreamPacket> {
        @Override
        public Packet onMessage(TerminateFileStreamPacket packet, NetSocket socket) {
            Utils.log("Failed to transfer file \"" + packet.name + "\". Stream terminated.");
            return null;
        }
    }
}
