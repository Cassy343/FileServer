package fs.network.packet;

import fs.common.InstanceHandler;
import io.vertx.core.net.NetSocket;
import stg.generic.ByteBuffer;
import stg.generic.ByteHelper;

public final class AccountIDAssignmentPacket implements Packet {
    private int accountID;
    
    public AccountIDAssignmentPacket() { }
    
    public AccountIDAssignmentPacket(int id) {
        this.accountID = id;
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        ByteHelper.writeInt(accountID, buffer);
    }

    @Override
    public void deserialize(ByteBuffer buffer) {
        accountID = ByteHelper.readInt(0, buffer);
    }
    
    public static final class Handler implements PacketHandler<AccountIDAssignmentPacket> {
        @Override
        public Packet onMessage(AccountIDAssignmentPacket packet, NetSocket socket) {
            InstanceHandler.client.setAccountID(packet.accountID);
            return null;
        }
    }
}
