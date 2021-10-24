package fs.network.ftp;

import fs.common.InstanceHandler;
import fs.network.NetworkHandler;
import io.vertx.core.net.NetSocket;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import stg.generic.ByteBuffer;
import stg.nbt.NbtTagCompound;

public final class AsyncFileStream extends Thread {
    private final String name;
    private final String file;
    private long fileSize;
    private final NbtTagCompound streamData;
    private final FileInputStream source;
    private final NetSocket socket;
    private final int packetSize;
    
    private static final NetworkHandler NET = InstanceHandler.NETWORK_HANDLER;
    
    public AsyncFileStream(String file, NetSocket socket, int packetSize, NbtTagCompound data) {
        this.file = file;
        File f = new File(file);
        this.name = f.getName();
        this.fileSize = f.length();
        this.streamData = data;
        try {
            this.source = new FileInputStream(file);
        }catch(FileNotFoundException fnfe) {
            throw new RuntimeException("Could not read file " + file, fnfe);
        }
        this.socket = socket;
        this.packetSize = packetSize;
    }
    
    @Override
    public void run() {
        try {
            int numFragments = (int)(fileSize / packetSize);
            numFragments += fileSize % packetSize != 0 || numFragments == 0 ? 1 : 0;
            NET.sendPacket(new FileStreamStartPacket(file, name, numFragments, fileSize, streamData == null ? new NbtTagCompound() : streamData), socket);
            byte[] buffer = new byte[packetSize];
            for(int i = 0;i < numFragments;++ i) {
                int len = fileSize < packetSize ? (int)fileSize : packetSize;
                fileSize -= len;
                source.read(buffer, 0, len);
                NET.sendPacket(new FileFragmentPacket(name, i, len, new ByteBuffer(buffer, 0, len)), socket);
                sleep0(50L);
            }
            NET.sendPacket(new FileStreamClosePacket(name), socket);
            source.close();
        }catch(IOException ex) {
            interrupt();
        }
    }
    
    @Override
    public void interrupt() {
        super.interrupt();
        NET.sendPacket(new TerminateFileStreamPacket(name), socket);
    }
    
    private static void sleep0(long time) {
        try {
            sleep(time);
        }catch(InterruptedException ex) { }
    }
}
