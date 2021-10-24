package fs.network.packet;

import io.vertx.core.net.NetSocket;

public interface PacketHandler<P extends Packet> {
    Packet onMessage(P packet, NetSocket socket);
}
