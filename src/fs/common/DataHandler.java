package fs.common;

import fs.server.FilePermission;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import stg.nbt.NbtException;
import stg.nbt.NbtIO;
import stg.nbt.NbtTagCompound;

public final class DataHandler {
    private final String baseDir;
    private final HashMap<String, NbtTagCompound> data;
    
    static {
        FilePermission.registerObjectProtocol();
    }
    
    public DataHandler(String baseDir, String... files) {
        this.baseDir = baseDir;
        this.data = new HashMap<>();
        for(String file : files)
            this.data.put(file, new NbtTagCompound());
    }
    
    public static void enableCompression() {
        NbtIO.enableCompression();
    }
    
    public static void disableCompression() {
        NbtIO.disableCompression();
    }
    
    public void addFile(String dir) {
        if(!data.containsKey(dir))
            data.put(dir, new NbtTagCompound());
    }
    
    public NbtTagCompound getFileData(String file) {
        return data.get(file);
    }
    
    public void saveData() throws IOException, NbtException {
        for(Map.Entry<String, NbtTagCompound> e : data.entrySet()) {
            String sfile = baseDir + File.separator + e.getKey();
            File file = wrap(sfile);
            if(!file.exists())
                createFile(sfile);
            //NbtIO.updateNBT(file);
            NbtIO.writeNbt(file, e.getValue());
        }
    }
    
    public void loadData() throws IOException, NbtException {
        for(String key : data.keySet()) {
            String sfile = baseDir + File.separator + key;
            File file = wrap(sfile);
            if(!file.exists()) {
                createFile(sfile);
                NbtIO.writeNbt(file, new NbtTagCompound());
            }
            data.put(key, NbtIO.readTagCompound(file));
        }
    }
    
    private static File wrap(String file) {
        return new File(file);
    }
    
    private static File createFile(String f) {
        try {
            File file = wrap(f);
            if(!file.getParentFile().exists())
                file.getParentFile().mkdirs();
            file.createNewFile();
            return file;
        }catch(IOException ex) {
            throw new RuntimeException("Failed to create file " + f, ex);
        }
    }
}
