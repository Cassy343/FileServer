package fs.network.packet;

import fs.common.InstanceHandler;
import fs.common.Utils;
import fs.network.NetworkHandler;
import fs.server.Account;
import fs.server.Server;
import io.vertx.core.net.NetSocket;
import java.io.File;
import stg.generic.ByteBuffer;
import stg.generic.ByteHelper;

public final class DownloadRequestPacket implements Packet {
    private String file;
    private int accountID;
    
    public DownloadRequestPacket() { }
    
    public DownloadRequestPacket(String file, int accountID) {
        this.file = file;
        this.accountID = accountID;
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        ByteHelper.writeString(file, buffer);
        ByteHelper.writeInt(accountID, buffer);
    }

    @Override
    public void deserialize(ByteBuffer buffer) {
        file = ByteHelper.readString(0, buffer);
        accountID = ByteHelper.readInt(file.length() + 1, buffer);
    }
    
    public static final class Handler implements PacketHandler<DownloadRequestPacket> {
        @Override
        public Packet onMessage(DownloadRequestPacket packet, NetSocket socket) {
            boolean bypassChecks = false;
            String path;
            if("*software_latest".equalsIgnoreCase(packet.file)) {
                bypassChecks = true;
                path = "./file-server.jar";
            }else
                path = Utils.combinePathElements(InstanceHandler.config.getString("fileStorageDir"), packet.file);
            File file = new File(path);
            Server server = InstanceHandler.server;
            Account sender = server.getAccount(packet.accountID);
            if(!bypassChecks && !server.checkDownload(file, sender.username))
                return new InfoLogPacket("You do not have access to that file.");
            if(!file.exists())
                return new InfoLogPacket("That file does not exist.");
            if(sender.data.containsKey("sfs")) {
                int index = -1;
                for(int i = 0;i < sender.data.getTagList("sfs").size();++ i) {
                    if(sender.data.getTagList("sfs").getTagCompound(i).getString("filename").equals(file.getName())) {
                        index = i;
                        break;
                    }
                }
                if(index != -1) {
                    sender.data.getTagList("sfs").remove(index);
                    server.saveAccountData(sender);
                }
            }
            if(file.length() > 6525000L)
                InstanceHandler.NETWORK_HANDLER.sendFileAsync(path, socket, (int)NetworkHandler.MAX_FTP_PACKET_SIZE, null);
            else
                InstanceHandler.NETWORK_HANDLER.sendFile(path, socket, null);
            return new InfoLogPacket("Downloading file...");
        }
    }
}
