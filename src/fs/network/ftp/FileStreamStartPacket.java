package fs.network.ftp;

import stg.generic.ByteBuffer;
import stg.generic.ByteHelper;
import stg.nbt.NbtTagCompound;

public final class FileStreamStartPacket extends FTPPacket {
    private String name;
    private String file;
    private int numFragments;
    private long fileLength;
    private NbtTagCompound streamData;
    
    public FileStreamStartPacket() { }
    
    public FileStreamStartPacket(String file, String name, int numFragments, long fileLength, NbtTagCompound streamData) {
        this.name = name;
        this.file = file;
        this.numFragments = numFragments;
        this.fileLength = fileLength;
        this.streamData = streamData;
    }
    
    public FileStreamStartPacket(String file, String name, int numFragments, long fileLength) {
        this(file, name, numFragments, fileLength, new NbtTagCompound());
    }
    
    @Override
    public void serialize(ByteBuffer buffer) {
        ByteHelper.writeString(name, buffer);
        ByteHelper.writeString(file, buffer);
        ByteHelper.writeInt(numFragments, buffer);
        ByteHelper.writeLong(fileLength, buffer);
        if(streamData.size() != 0) streamData.writeToBuffer(buffer);
    }

    @Override
    public void deserialize(ByteBuffer buffer) {
        name = ByteHelper.readString(0, buffer);
        file = ByteHelper.readString(name.length() + 1, buffer);
        numFragments = ByteHelper.readInt(name.length() + file.length() + 2, buffer);
        fileLength = ByteHelper.readLong(name.length() + file.length() + 6, buffer);
        streamData = new NbtTagCompound();
        if(buffer.size() > name.length() + file.length() + 14) streamData.readFromBuffer(buffer, name.length() + file.length() + 14);
    }
    
    public String getName() {
        return name;
    }
    
    public String getFile() {
        return file;
    }
    
    public NbtTagCompound getStreamData() {
        return streamData;
    }
    
    public int getFragmentCount() {
        return numFragments;
    }
    
    public long getFileSize() {
        return fileLength;
    }
}
