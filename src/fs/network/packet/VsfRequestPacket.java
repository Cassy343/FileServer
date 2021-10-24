package fs.network.packet;

import fs.common.InstanceHandler;
import fs.server.Account;
import io.vertx.core.net.NetSocket;
import stg.generic.ByteBuffer;
import stg.generic.ByteHelper;
import stg.nbt.NbtTagCompound;
import stg.nbt.NbtTagList;

public final class VsfRequestPacket implements Packet {
    private int accountID;
    
    public VsfRequestPacket() { }
    
    public VsfRequestPacket(int accountID) {
        this.accountID = accountID;
    }
    
    @Override
    public void serialize(ByteBuffer buffer) {
        ByteHelper.writeInt(accountID, buffer);
    }

    @Override
    public void deserialize(ByteBuffer buffer) {
        accountID = ByteHelper.readInt(0, buffer);
    }
    
    public static final class Handler implements PacketHandler<VsfRequestPacket> {
        @Override
        public Packet onMessage(VsfRequestPacket packet, NetSocket socket) {
            StringBuilder sb = new StringBuilder();
            Account account = InstanceHandler.server.getAccount(packet.accountID);
            if(!account.data.containsKey("sfs") || account.data.getTagList("sfs").size() == 0)
                return new InfoLogPacket("No files have been shared with you.");
            sb.append('\n');
            for(int i = 0;i < account.data.getTagList("sfs").size();++ i) {
                NbtTagCompound sf = account.data.getTagList("sfs").getTagCompound(i);
                sb.append('\"').append(sf.getString("filename")).append("\" from ").append(sf.getString("sender"));
                if(i != account.data.getTagList("sfs").size() - 1) sb.append('\n');
            }
            account.data.setTag("sfs", new NbtTagList());
            InstanceHandler.server.saveAccountData(account);
            return new InfoLogPacket(sb.toString());
        }
    }
}
