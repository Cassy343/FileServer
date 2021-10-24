package fs.server;

import fs.common.Security;
import java.util.Arrays;
import java.util.Objects;
import stg.generic.ByteBuffer;
import stg.generic.ByteHelper;
import stg.nbt.NbtIO;
import stg.nbt.NbtTagCompound;

public final class Account {
    public final int id;
    public final String username;
    public final byte[] passwordHash;
    public final NbtTagCompound data;
    
    public Account(NbtTagCompound data) {
        this.id = data.getInteger("id");
        this.username = data.getString("username");
        this.passwordHash = data.getByteArray("password");
        this.data = data.getTagCompound("data");
    }
    
    private Account(int id, String username, byte[] passwordHash, NbtTagCompound data) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.data = data;
    }
    
    public void serialize(ByteBuffer buffer) {
        ByteHelper.writeInt(id, buffer);
        ByteHelper.writeString(username, buffer);
        buffer.appendAll(passwordHash);
        data.writeToBuffer(buffer);
    }
    
    public static Account deserialize(ByteBuffer buffer, int index) {
        int id = ByteHelper.readInt(index, buffer);
        String username = ByteHelper.readString(index + 4, buffer);
        byte[] passwordHash = buffer.getRange(index + username.length() + 5, 32);
        return new Account(id, username, passwordHash, (NbtTagCompound)NbtIO.readNbtTag((byte)9, index + username.length() + 37, buffer));
    }
    
    public int getByteLength() {
        return username.length() + 37 + data.getByteLength();
    }
    
    public NbtTagCompound toTagCompound() {
        NbtTagCompound tag = new NbtTagCompound();
        tag.setInteger("id", id);
        tag.setString("username", username);
        tag.setByteArray("password", passwordHash);
        tag.setTag("data", data);
        return tag;
    }
    
    @Override
    public boolean equals(Object other) {
        if(other == this)
            return true;
        if(!Account.class.equals(other.getClass()))
            return false;
        Account account = (Account)other;
        return account.id == id && account.username.equals(username) && Arrays.equals(account.passwordHash, passwordHash);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 61 * hash + this.id;
        hash = 61 * hash + Objects.hashCode(this.username);
        hash = 61 * hash + Arrays.hashCode(this.passwordHash);
        return hash;
    }
    
    public boolean credentialsMatch(String username, byte[] password) {
        return this.username.equalsIgnoreCase(username) && Arrays.equals(Security.salt(password, Security.CLIENT_SALT), Security.salt(passwordHash, Security.SERVER_SALT));
    }
}
