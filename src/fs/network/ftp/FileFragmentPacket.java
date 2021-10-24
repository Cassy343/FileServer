package fs.network.ftp;

import stg.generic.ByteBuffer;
import stg.generic.ByteHelper;

public final class FileFragmentPacket extends FTPPacket {
    private String name;
    private int section;
    private int len;
    private ByteBuffer data;
    
    public FileFragmentPacket() { }
    
    public FileFragmentPacket(String name, int section, int len, ByteBuffer data) {
        this.name = name;
        this.section = section;
        this.len = len;
        this.data = data;
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        ByteHelper.writeString(name, buffer);
        ByteHelper.writeInt(section, buffer);
        ByteHelper.writeInt(len, buffer);
        buffer.appendAll(data.toArray());
    }

    @Override
    public void deserialize(ByteBuffer buffer) {
        name = ByteHelper.readString(0, buffer);
        section = ByteHelper.readInt(name.length() + 1, buffer);
        len = ByteHelper.readInt(name.length() + 5, buffer);
        data = buffer.subBuffer(name.length() + 9, len);
    }
    
    public String getName() {
        return name;
    }
    
    public int getSectionNumber() {
        return section;
    }
    
    public ByteBuffer getData() {
        return data;
    }
}
