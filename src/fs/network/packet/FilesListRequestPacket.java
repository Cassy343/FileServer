package fs.network.packet;

import fs.common.InstanceHandler;
import io.vertx.core.net.NetSocket;
import java.io.File;
import stg.generic.ByteBuffer;

public final class FilesListRequestPacket implements Packet {
    public FilesListRequestPacket() { }

    @Override
    public void serialize(ByteBuffer buffer) { }

    @Override
    public void deserialize(ByteBuffer buffer) { }
    
    public static final class Handler implements PacketHandler<FilesListRequestPacket> {
        @Override
        public Packet onMessage(FilesListRequestPacket packet, NetSocket socket) {
            StringBuilder sb = new StringBuilder();
            sb.append('\n');
            File[] files = (new File(InstanceHandler.config.getString("fileStorageDir"))).listFiles();
            for(int i = 0;i < files.length;++ i) {
                if(files[i].isDirectory()) continue;
                sb.append(files[i].getName()).append('\n');
                if(sb.length() > 65000) {
                    InstanceHandler.NETWORK_HANDLER.sendPacket(new InfoLogPacket(sb.toString().substring(0, sb.length() - 1)), socket);
                    sb.setLength(0);
                }
            }
            InstanceHandler.NETWORK_HANDLER.sendPacket(new InfoLogPacket(sb.toString().substring(0, sb.length() - 1)), socket);
            return null;
        }
    }
}
