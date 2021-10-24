package fs.network.ftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import stg.nbt.NbtTagCompound;

public final class AsyncFileFragmentAggregator {
    private final String name;
    private final String file;
    private final NbtTagCompound streamData;
    private final int numFragments;
    private int numFragmentsCounted;
    private final List<DataNode> data;
    private boolean finished;
    
    public AsyncFileFragmentAggregator(FileStreamStartPacket fssp) {
        this.name = fssp.getName();
        this.file = fssp.getFile();
        this.streamData = fssp.getStreamData();
        this.numFragments = fssp.getFragmentCount();
        this.data = new ArrayList<>();
        this.finished = false;
    }
    
    // write the data to a tmp file
    public boolean acceptFragment(FileFragmentPacket ffp) {
        if(finished) return false;
        if(!name.equals(ffp.getName())) return false;
        store(ffp);
        ++ numFragmentsCounted;
        return true;
    }
    
    public boolean finish(FileStreamClosePacket fscp) {
        if(finished || numFragments != numFragmentsCounted) return false;
        if(!name.equals(fscp.getName())) return false;
        finished = true;
        clump();
        fscp.setCollector(this);
        return true;
    }
    
    public boolean finished() {
        return finished;
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
    
    public void writeToStream(OutputStream ostream) throws IOException {
        FileInputStream ifstream = new FileInputStream(data.get(0).dumpFile);
        byte[] buffer = new byte[65535];
        int len;
        while((len = ifstream.read(buffer)) > 0)
            ostream.write(buffer, 0, len);
        ifstream.close();
        data.get(0).destructor();
    }
    
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        data.forEach(node -> node.destructor());
    }
    
    private void store(FileFragmentPacket ffp) {
        for(DataNode node : data) {
            if(node.canAccept(ffp)) {
                try {
                    node.accept(ffp);
                }catch(IOException ex) { }
                return;
            }
        }
        data.add(new DataNode(ffp));
    }
    
    private void clump() {
        data.stream().sorted((d0, d1) -> d0.startSection - d1.startSection).forEach(node -> {
            if(node.startSection == 0) return;
            try {
                data.get(0).absorb(node);
            }catch(IOException ex) { }
        });
    }
    
    private static final class DataNode {
        final File dumpFile;
        final FileOutputStream dumpStream;
        final int startSection;
        int lastSectionNum;
        
        public DataNode(FileFragmentPacket ffp) {
            try {
                this.dumpFile = new File(Files.createTempFile(ffp.getName().replaceAll("\\W", "_"), Integer.toString(ffp.getSectionNumber()) + ".tmp").toUri());
                this.dumpFile.deleteOnExit();
                this.dumpStream = new FileOutputStream(dumpFile, true);
                this.lastSectionNum = this.startSection = ffp.getSectionNumber();
                dumpStream.write(ffp.getData().toArray());
            }catch(IOException ex) {
                throw new InternalError(ex);
            }
        }
        
        public boolean canAccept(FileFragmentPacket ffp) {
            return ffp.getSectionNumber() == lastSectionNum + 1;
        }
        
        public void accept(FileFragmentPacket ffp) throws IOException {
            dumpStream.write(ffp.getData().toArray());
            ++ lastSectionNum;
        }
        
        public void absorb(DataNode other) throws IOException {
            other.dumpStream.flush();
            FileInputStream istream = new FileInputStream(other.dumpFile);
            byte[] buffer = new byte[65535];
            int len;
            while((len = istream.read(buffer)) > 0) {
                dumpStream.write(buffer, 0, len);
            }
            other.destructor();
        }
        
        public void destructor() {
            try {
                dumpStream.flush();
                dumpStream.close();
                dumpFile.delete();
            }catch(IOException ex) { }
        }
        
        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            destructor();
        }
    }
}
