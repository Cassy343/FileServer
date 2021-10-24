package fs.network;

import fs.common.InstanceHandler;
import fs.common.Utils;
import fs.network.ftp.AsyncFileStream;
import fs.network.ftp.FTPPacket;
import fs.network.ftp.AsyncFileFragmentAggregator;
import fs.network.ftp.FileFragmentPacket;
import fs.network.ftp.FileStreamClosePacket;
import fs.network.ftp.FileStreamStartPacket;
import fs.network.ftp.TerminateFileStreamPacket;
import fs.network.packet.AccountIDAssignmentPacket;
import fs.network.packet.DownloadRequestPacket;
import fs.network.packet.FilesListRequestPacket;
import fs.network.packet.InfoLogPacket;
import fs.network.packet.LoginRequestPacket;
import fs.network.packet.Packet;
import fs.network.packet.PacketHandler;
import fs.network.packet.VsfRequestPacket;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiPredicate;
import stg.generic.ByteBuffer;
import stg.generic.ByteHelper;
import stg.nbt.NbtTagCompound;
import stg.reflect.ReflectionHelper;

public final class NetworkHandler {
    private final List<PacketData> packetRegistry;
    private final AsyncFTPHandler ftpHandler;
    private final BufferParser bufferParser;
    private byte descriminator;
    private BiPredicate<NetSocket, Packet> packetValidator;
    public static final long MAX_FTP_PACKET_SIZE = 65250L;
    
    private void init() {
        // File transfer protocol stuff
        registerFTPP(FileStreamStartPacket.class, null, null);
        registerFTPP(FileFragmentPacket.class, null, null);
        registerFTPP(FileStreamClosePacket.class, new FileStreamClosePacket.ClientHandler(), Side.CLIENT);
        registerFTPP(FileStreamClosePacket.class, new FileStreamClosePacket.ServerHandler(), Side.SERVER);
        registerFTPP(TerminateFileStreamPacket.class, new TerminateFileStreamPacket.Handler(), Side.CLIENT);
        registerFTPP(TerminateFileStreamPacket.class, new TerminateFileStreamPacket.Handler(), Side.SERVER);
        
        // Other packets
        registerBidirectional(InfoLogPacket.class, new InfoLogPacket.Handler(), null);
        register(LoginRequestPacket.class, new LoginRequestPacket.Handler(), Side.SERVER);
        register(AccountIDAssignmentPacket.class, new AccountIDAssignmentPacket.Handler(), Side.CLIENT);
        register(DownloadRequestPacket.class, new DownloadRequestPacket.Handler(), Side.SERVER);
        register(FilesListRequestPacket.class, new FilesListRequestPacket.Handler(), Side.SERVER);
        register(VsfRequestPacket.class, new VsfRequestPacket.Handler(), Side.SERVER);
    }
    
    public NetworkHandler() {
        this.packetRegistry = new LinkedList<>();
        this.ftpHandler = new AsyncFTPHandler(this);
        this.bufferParser = new BufferParser(this);
        this.descriminator = 0;
        init();
    }
    
    public <P extends Packet> void register(Class<P> packetClass, PacketHandler<P> handler, Side recievingSide) {
        packetRegistry.add(new PacketData<>(packetClass, descriminator++, handler, recievingSide));
    }
    
    public <P extends Packet> void registerBidirectional(Class<P> packetClass, PacketHandler<P> clientHandler,
            PacketHandler<P> serverHandler) {
        register(packetClass, clientHandler, Side.CLIENT);
        register(packetClass, serverHandler == null ? clientHandler : serverHandler, Side.SERVER);
    }
    
    private <P extends Packet> void registerFTPP(Class<P> packetClass, PacketHandler<P> handler, Side recievingSide) {
        packetRegistry.add(new PacketData<>(packetClass, descriminator++, handler, recievingSide, true));
    }
    
    private PacketData getPacketData(Packet packet, Side dest) {
        for(PacketData pd : packetRegistry) {
            if(packet.getClass().equals(pd.packetClass) && (pd.handler == null || pd.side == dest))
                return pd;
        }
        return null;
    }
    
    public void packetValidationHandler(BiPredicate<NetSocket, Packet> validator) {
        packetValidator = validator;
    }
    
    public void bindNetworkHandlers(NetSocket socket) {
        socket.handler(buf -> {
            ByteBuffer buffer = new ByteBuffer(buf.getBytes());
            bufferParser.digest(buffer, socket);
        });
        
        socket.exceptionHandler(throwable -> Utils.logError(throwable));
    }
    
    private void handlePacket(ByteBuffer buffer, NetSocket socket) {
        int index = buffer.get(0);
        if(index < 0 || index >= packetRegistry.size())
            return; // most likely an internal error
        PacketData<Packet> pd = packetRegistry.get(index);
        if(pd == null)
            return; // most likely an internal error (again)
        Packet packet = (Packet)ReflectionHelper.instantiate(pd.packetClass);
        packet.deserialize(buffer.subBuffer(5, buffer.size() - 5)); // skip over the len
        if(packetValidator != null && !packetValidator.test(socket, packet)) {
            Utils.log("A client sent an invalid packet.");
            socket.close();
            return;
        }
        if(pd.isFTPP) {
            handleFTPP(packet, socket);
            return;
        }
        Packet response = pd.handler.onMessage(packet, socket);
        if(response != null)
            sendPacket(response, socket);
    }
    
    private void handleFTPP(Packet ftpp, NetSocket socket) {
        ((FTPPacket)ftpp).attachSocket(socket);
        if(ftpp instanceof FileFragmentPacket)
            ftpHandler.handleFileFragment((FileFragmentPacket)ftpp);
        else if(ftpp instanceof FileStreamClosePacket)
            ftpHandler.handleFileStreamClose((FileStreamClosePacket)ftpp);
        else if(ftpp instanceof FileStreamStartPacket)
            ftpHandler.handleFileStreamStart((FileStreamStartPacket)ftpp);
        else if(ftpp instanceof TerminateFileStreamPacket)
            ftpHandler.handleStreamTermination((TerminateFileStreamPacket)ftpp);
    }
    
    public void sendPacket(Packet packet, NetSocket socket) {
        ByteBuffer buffer = new ByteBuffer();
        buffer.append(getPacketData(packet, InstanceHandler.side().opposite()).descriminator);
        ByteBuffer tempBuff = new ByteBuffer();
        packet.serialize(tempBuff);
        ByteHelper.writeInt(tempBuff.size(), buffer);
        buffer.appendAll(tempBuff.toArray());
        socket.write(Buffer.buffer(buffer.toArray()));
    }
    
    public void sendFile(String file, NetSocket socket, NbtTagCompound streamData) {
        File f = new File(file);
        if(!f.exists())
            throw new IllegalArgumentException("The file specified does not exist: " + f.toString());
        int numFragments = (int)(f.length() / MAX_FTP_PACKET_SIZE);
        long fs = f.length();
        numFragments += f.length() % MAX_FTP_PACKET_SIZE != 0 || numFragments == 0 ? 1 : 0;
        sendPacket(new FileStreamStartPacket(file, f.getName(), numFragments, f.length(), streamData == null ? new NbtTagCompound() : streamData), socket);
        
        try {
            FileInputStream ifstream = new FileInputStream(f);
            byte[] buffer = new byte[(int)MAX_FTP_PACKET_SIZE];
            for(int i = 0;i < numFragments;++ i) {
                int len = fs < MAX_FTP_PACKET_SIZE ? (int)fs : (int)MAX_FTP_PACKET_SIZE;
                fs -= len;
                ifstream.read(buffer, 0, len);
                sendPacket(new FileFragmentPacket(f.getName(), i, len, new ByteBuffer(buffer, 0, len)), socket);
            }
            ifstream.close();
        }catch(IOException ex) {
            Utils.logError(ex);
            sendPacket(new TerminateFileStreamPacket(file), socket);
        }
        
        sendPacket(new FileStreamClosePacket(f.getName()), socket);
    }
    
    public void sendFileAsync(String file, NetSocket socket, int packetSize, NbtTagCompound streamData) {
        (new AsyncFileStream(file, socket, Utils.constrain(packetSize, 1024, (int)MAX_FTP_PACKET_SIZE), streamData)).start();
    }
    
    public void destructor() {
        ftpHandler.forceClear();
    }
    
    private static final class BufferParser {
        private final NetworkHandler net;
        ByteBuffer buffer;
        
        public BufferParser(NetworkHandler net) {
            this.net = net;
            this.buffer = new ByteBuffer();
        }
        
        public void digest(ByteBuffer buf, NetSocket socket) {
            buffer.appendAll(buf.toArray());
            int index = 0;
            while(index < buffer.size() - 4) {
                ++ index;
                int len = ByteHelper.readInt(index, buffer);
                index += 4;
                if(index + len > buffer.size()) {
                    buffer = buffer.subBuffer(index - 5, buffer.size() - (index - 5));
                    return;
                }
                net.handlePacket(buffer.subBuffer(index - 5, len + 5), socket);
                index += len;
            }
            buffer = buffer.subBuffer(index, buffer.size() - index);
        }
    }
    
    // Note: the AsyncFileFragmentAggregator is not prefixed by "Async" for a reason
    private static final class AsyncFTPHandler {
        private final NetworkHandler net;
        final List<FileFragmentPacket> fragmentQueue;
        final List<FileStreamClosePacket> streamCloseQueue;
        final List<AsyncFileFragmentAggregator> aggregators;
        
        public AsyncFTPHandler(NetworkHandler net) {
            this.net = net;
            this.fragmentQueue = new ArrayList<>();
            this.streamCloseQueue = new ArrayList<>();
            this.aggregators = new ArrayList<>();
        }
        
        public void handleFileStreamStart(FileStreamStartPacket fssp) {
            AsyncFileFragmentAggregator ffa = new AsyncFileFragmentAggregator(fssp);
            aggregators.add(ffa);
            if(!fragmentQueue.isEmpty()) tryFlushFragmentQueue(ffa);
            if(!streamCloseQueue.isEmpty()) tryAggregatorClose(ffa);
        }
        
        public void handleFileFragment(FileFragmentPacket ffp) {
            Iterator<AsyncFileFragmentAggregator> itr = aggregators.iterator();
            while(itr.hasNext()) {
                AsyncFileFragmentAggregator ffa = itr.next();
                if(ffa.acceptFragment(ffp)) {
                    if(!streamCloseQueue.isEmpty() && tryAggregatorClose(ffa))
                        itr.remove();
                    return;
                }
            }
            fragmentQueue.add(ffp);
        }
        
        public void handleFileStreamClose(FileStreamClosePacket fscp) {
            Iterator<AsyncFileFragmentAggregator> itr = aggregators.iterator();
            while(itr.hasNext()) {
                AsyncFileFragmentAggregator ffa = itr.next();
                if(!fragmentQueue.isEmpty()) tryFlushFragmentQueue(ffa);
                if(ffa.finish(fscp)) {
                    itr.remove();
                    Packet response = net.getPacketData(fscp, InstanceHandler.side()).handler.onMessage(fscp, fscp.getSocket());
                    if(response != null)
                        net.sendPacket(response, fscp.getSocket());
                    return;
                }
            }
            streamCloseQueue.add(fscp);
        }
        
        public void handleStreamTermination(TerminateFileStreamPacket tfsp) {
            fragmentQueue.removeIf(ffp -> tfsp.getName().equals(ffp.getName()));
            streamCloseQueue.removeIf(fscp -> tfsp.getName().equals(fscp.getName()));
            aggregators.removeIf(ffa -> ffa.getName().equals(tfsp.getName()));
            System.gc();
        }
        
        private void tryFlushFragmentQueue(AsyncFileFragmentAggregator ffa) {
            Iterator<FileFragmentPacket> itr = fragmentQueue.iterator();
            while(itr.hasNext()) {
                if(ffa.acceptFragment(itr.next()))
                    itr.remove();
            }
        }
        
        private boolean tryAggregatorClose(AsyncFileFragmentAggregator ffa) {
            Iterator<FileStreamClosePacket> itr = streamCloseQueue.iterator();
            while(itr.hasNext()) {
                FileStreamClosePacket fscp = itr.next();
                if(ffa.finish(fscp)) {
                    itr.remove();
                    Packet response = net.getPacketData(fscp, InstanceHandler.side()).handler.onMessage(fscp, fscp.getSocket());
                    if(response != null)
                        net.sendPacket(response, fscp.getSocket());
                    return true;
                }
            }
            return false;
        }
        
        public void forceClear() {
            aggregators.forEach(ffa -> {
                tryFlushFragmentQueue(ffa);
                tryAggregatorClose(ffa);
            });
            fragmentQueue.clear();
            streamCloseQueue.clear();
            aggregators.clear();
        }
    }
    
    private static final class PacketData<P extends Packet> {
        final byte descriminator;
        final Class<? extends Packet> packetClass;
        final PacketHandler<P> handler;
        final Side side;
        final boolean isFTPP;
        
        public PacketData(Class<? extends Packet> packetClass, byte descriminator, PacketHandler<P> handler, Side recievingSide, boolean isFTPP) {
            this.packetClass = packetClass;
            this.descriminator = descriminator;
            this.handler = handler;
            this.side = recievingSide;
            this.isFTPP = isFTPP;
        }
        
        public PacketData(Class<? extends Packet> packetClass, byte descriminator, PacketHandler<P> handler, Side recievingSide) {
            this(packetClass, descriminator, handler, recievingSide, false);
        }
    }
}
