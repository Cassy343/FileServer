package fs.network.packet;

import fs.common.Utils;
import io.vertx.core.net.NetSocket;
import stg.generic.ByteBuffer;
import stg.generic.ByteHelper;

public final class InfoLogPacket implements Packet {
    private String msg;
    
    public InfoLogPacket() { }
    
    public InfoLogPacket(String msg) {
        this.msg = msg;
    }
    
    @Override
    public void serialize(ByteBuffer buffer) {
        ByteHelper.writeString(msg, buffer);
    }

    @Override
    public void deserialize(ByteBuffer buffer) {
        msg = ByteHelper.readString(0, buffer);
    }
    
    public static final class Handler implements PacketHandler<InfoLogPacket> {
        @Override
        public Packet onMessage(InfoLogPacket packet, NetSocket socket) {
            Utils.log(packet.msg);
            return null;
        }
    }
}
