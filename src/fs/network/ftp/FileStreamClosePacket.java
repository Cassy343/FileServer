package fs.network.ftp;

import fs.common.InstanceHandler;
import fs.common.Utils;
import fs.network.packet.InfoLogPacket;
import fs.network.packet.Packet;
import fs.network.packet.PacketHandler;
import fs.server.Account;
import fs.server.Server;
import io.vertx.core.net.NetSocket;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import stg.generic.ByteBuffer;
import stg.generic.ByteHelper;
import stg.nbt.NbtTagCompound;
import stg.nbt.NbtTagList;

public final class FileStreamClosePacket extends FTPPacket {
    private String name;
    private AsyncFileFragmentAggregator collector;
    
    public FileStreamClosePacket() { }
    
    public FileStreamClosePacket(String name) {
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
    
    public void setCollector(AsyncFileFragmentAggregator ffa) {
        collector = ffa;
    }
    
    public AsyncFileFragmentAggregator getCollector() {
        return collector;
    }
    
    public static final class ServerHandler implements PacketHandler<FileStreamClosePacket> {
        @Override
        public Packet onMessage(FileStreamClosePacket packet, NetSocket socket) {
            NbtTagCompound streamData = packet.collector.getStreamData();
            File file = new File(Utils.combinePathElements(InstanceHandler.config.getString("fileStorageDir"),
                    streamData.containsKey("fileOut") ? streamData.getString("fileOut") : packet.name));
            if(file.exists() && !InstanceHandler.server.checkDelete(file, streamData.getString("username")))
                return new InfoLogPacket("A file with that name already exists, and you do not have permnission to overwrite it.");
            List<String> downloaders = new ArrayList<>();
            Server server = InstanceHandler.server;
            String username = streamData.getString("username");
            if(streamData.getBoolean("shared")) {
                for(String s : streamData.getStringArray("downloaders")) {
                    Account a = server.getAccount(s);
                    if(a == null) {
                        InstanceHandler.NETWORK_HANDLER.sendPacket(new InfoLogPacket(s + " is not a valid account."), socket);
                        continue;
                    }
                    if(!a.data.containsKey("sfs"))
                        a.data.setTag("sfs", new NbtTagList());
                    NbtTagCompound sf = new NbtTagCompound();
                    sf.setString("filename", file.getName());
                    sf.setString("sender", username);
                    a.data.getTagList("sfs").appendTag(sf);
                    server.saveAccountData(a);
                    downloaders.add(s);
                }
            }
            server.setFilePermissions(file, username, downloaders);
            try {
                FileOutputStream ostream = new FileOutputStream(file);
                packet.collector.writeToStream(ostream);
                ostream.flush();
                ostream.close();
            }catch(IOException ex) {
                Utils.log("Failed to store file in drive.");
                Utils.logError(ex);
            }
            return null;
        }
    }
    
    public static final class ClientHandler implements PacketHandler<FileStreamClosePacket> {
        @Override
        public Packet onMessage(FileStreamClosePacket packet, NetSocket socket) {
            File file = new File(Utils.combinePathElements(InstanceHandler.config.getString("downloadsDir"), packet.name));
            try {
                FileOutputStream ostream = new FileOutputStream(file);
                packet.collector.writeToStream(ostream);
                ostream.flush();
                ostream.close();
            }catch(IOException ex) {
                Utils.log("Failed to store file in drive.");
                Utils.logError(ex);
            }
            return null;
        }
    }
}
