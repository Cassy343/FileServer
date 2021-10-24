package fs.server;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import stg.generic.ByteHelper;
import stg.generic.ObjectProtocol;
import stg.nbt.NbtIO;

public final class FilePermission {
    private final File file;
    private final List<String> downloaders;
    private final String uploader;
    
    public static void registerObjectProtocol() {
        NbtIO.<FilePermission>defineObjectProtocol((fp, buffer) -> {
            ByteHelper.writeString(fp.file.getAbsolutePath(), buffer);
            ByteHelper.writeInt(fp.downloaders.size(), buffer);
            for(String s : fp.downloaders) ByteHelper.writeString(s, buffer);
            ByteHelper.writeString(fp.uploader, buffer);
        }, (index, buffer) -> {
            String file = ByteHelper.readString(index, buffer);
            int numDownloaders = ByteHelper.readInt(index + file.length() + 1, buffer);
            index += file.length() + 5;
            List<String> downloaders = new ArrayList<>();
            for(int i = 0;i < numDownloaders;++ i) {
                String s = ByteHelper.readString(index, buffer);
                index += s.length() + 1;
                downloaders.add(s);
            }
            return new FilePermission(new File(file), downloaders, ByteHelper.readString(index, buffer));
        }, FilePermission.class, ObjectProtocol.createSafeProtocolUID(FilePermission.class, -239568));
    }
    
    public FilePermission(File file, List<String> downloaders, String uploader) {
        this.file = file;
        this.downloaders = downloaders;
        this.uploader = uploader;
    }
    
    public FilePermission(File file, String uploader) {
        this(file, new ArrayList<>(), uploader);
    }
    
    public void addDownloaders(String... accounts) {
        downloaders.addAll(Arrays.asList(accounts));
    }
    
    public void addDownloaders(Collection<String> accounts) {
        downloaders.addAll(accounts);
    }
    
    public File getFile() {
        return file;
    }
    
    public List<String> getDownloaders() {
        return downloaders;
    }
    
    public String getUploader() {
        return uploader;
    }
}
