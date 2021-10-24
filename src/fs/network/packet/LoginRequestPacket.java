package fs.network.packet;

import fs.common.InstanceHandler;
import fs.common.Utils;
import fs.server.Account;
import fs.server.Server;
import io.vertx.core.net.NetSocket;
import stg.generic.ByteBuffer;
import stg.generic.ByteHelper;
import stg.nbt.NbtTagList;

public final class LoginRequestPacket implements Packet {
    private int accountID;
    private String username;
    private byte[] password;
    private short version;
    
    public LoginRequestPacket() { }
    
    public LoginRequestPacket(int accountID, String username, byte[] password) {
        this.accountID = accountID;
        this.username = username;
        this.password = password;
        this.version = InstanceHandler.VERSION;
    }
    
    @Override
    public void serialize(ByteBuffer buffer) {
        ByteHelper.writeInt(accountID, buffer);
        ByteHelper.writeString(username, buffer);
        buffer.appendAll(password);
        ByteHelper.writeShort(version, buffer);
    }

    @Override
    public void deserialize(ByteBuffer buffer) {
        accountID = ByteHelper.readInt(0, buffer);
        username = ByteHelper.readString(4, buffer);
        password = buffer.getRange(username.length() + 5, 32);
        version = ByteHelper.readShort(username.length() + 37, buffer);
    }
    
    public static final class Handler implements PacketHandler<LoginRequestPacket> {
        @Override
        public Packet onMessage(LoginRequestPacket packet, NetSocket socket) {
            if(packet.version != InstanceHandler.VERSION)
                InstanceHandler.NETWORK_HANDLER.sendPacket(new InfoLogPacket("A newer version of this software can be downloaded. Enter the command \"download *software_latest\" to get the latest jar."), socket);
            Server server = InstanceHandler.server;
            if(packet.accountID == -1) {
                Account account = server.createAccount(packet.username, packet.password);
                if(account != null) {
                    server.validateAccount(socket.remoteAddress(), account);
                    InstanceHandler.NETWORK_HANDLER.sendPacket(new AccountIDAssignmentPacket(account.id), socket);
                    return new InfoLogPacket("Your account has been created.");
                }else{
                    Utils.log("A client requested to login, but their account (" + packet.username + ") was not registered.");
                    socket.close(); // this is not okay
                    return null;
                }
            }else{
                NbtTagList accounts = InstanceHandler.dataHandler.getFileData("caches.nbt").getTagList("accounts");
                Account account;
                if(packet.accountID < 0 || packet.accountID >= accounts.size()) {
                    account = server.getAccount(packet.username);
                    if(account == null) {
                        socket.close();
                        return null;
                    }else{
                        if(account.id != packet.accountID)
                            InstanceHandler.NETWORK_HANDLER.sendPacket(new AccountIDAssignmentPacket(account.id), socket);
                    }
                }else{
                    account = server.getAccount(packet.accountID);
                    if(!packet.username.equalsIgnoreCase(account.username)) {
                        account = server.getAccount(packet.username);
                        if(account == null) {
                            socket.close();
                            return null;
                        }else{
                            if(account.id != packet.accountID)
                                InstanceHandler.NETWORK_HANDLER.sendPacket(new AccountIDAssignmentPacket(account.id), socket);
                        }
                    }
                }
                if(account.data.containsKey("sfs") && account.data.getTagList("sfs").size() > 0)
                    InstanceHandler.NETWORK_HANDLER.sendPacket(new InfoLogPacket("Files have been shared with you. Type \"vsf\" to view the files."), socket);
                if(account.credentialsMatch(packet.username, packet.password)) {
                    server.validateAccount(socket.remoteAddress(), account);
                    return new InfoLogPacket("Successfully connected to server.");
                }else
                    return new InfoLogPacket("Invalid login credentials.");
            }
        }
    }
}
