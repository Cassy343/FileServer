package fs.server;

import fs.common.Command;
import fs.common.InstanceHandler;
import fs.common.Utils;
import fs.common.CommandHandler;
import fs.common.DataHandler;
import fs.common.ExitCode;
import fs.common.Security;
import fs.network.packet.LoginRequestPacket;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.SocketAddress;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.TERMINATE;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiConsumer;
import stg.config.ConfigFormatException;
import stg.config.ini.IniFile;
import stg.nbt.NbtException;
import stg.nbt.NbtTagCompound;
import stg.nbt.NbtTagList;

public final class Server extends Thread {
    private final NetServer tcpServer;
    private final IniFile config;
    private DataHandler dat;
    private HashMap<SocketAddress, Account> verifiedClients;
    
    public Server(String configDir) {
        this.tcpServer = Utils.VERTX.createNetServer();
        initCommandHandler();
        this.config = new IniFile(configDir);
        this.verifiedClients = new HashMap<>();
        try {
            initConfig();
            InstanceHandler.config = config;
            initDataHandler();
        }catch(IOException ioe) {
            Utils.failOnError("An I/O error occurred during data aggregation.", ioe, ExitCode.DATA_MANAGEMENT_ERROR);
        }catch(ConfigFormatException cfe) {
            throw new RuntimeException("Invalid config format.", cfe);
        }catch(NbtException nbte) {
            throw new RuntimeException("A private data file was corrupted.", nbte);
        }
    }
    
    private void initCommandHandler() {
        InstanceHandler.commandHandler = new CommandHandler(System.in);
        InstanceHandler.commandHandler.disableCommands(
            Command.DOWNLOAD, Command.UPLOAD, Command.VIEW_SHARED_FILES
        );
        InstanceHandler.commandHandler.setProcessingCallback(new CommandImpl(this));
    }
    
    private void initConfig() throws IOException, ConfigFormatException {
        if(!config.getFile().exists())
            config.getFile().createNewFile();
        
        config.putNumber("tcpPort", 5001);
        config.putString("fileStorageDir", "files");
        config.putString("dataDir", "metadata");
        
        config.sync(false);
        config.save();
        
        // includes the default dir
        File fileStorageDir = new File(config.getString("fileStorageDir"));
        if(!fileStorageDir.exists())
            fileStorageDir.mkdirs();
    }
    
    private void initDataHandler() throws IOException, NbtException {
        dat = (InstanceHandler.dataHandler = new DataHandler(config.getString("dataDir"), "caches.nbt", "fperms.nbt"));
        dat.loadData();
        NbtTagCompound caches = dat.getFileData("caches.nbt");
        if(!caches.containsKey("pendingAccounts"))
            caches.setStringArray("pendingAccounts", new String[0]);
        if(!caches.containsKey("accounts"))
            caches.setTag("accounts", new NbtTagList());
        NbtTagCompound fperms = dat.getFileData("fperms.nbt");
        if(!fperms.containsKey("main"))
            fperms.setTag("main", new NbtTagList());
        dat.saveData();
    }
    
    @Override
    public void run() {
        startServer();
    }
    
    public void startServer() {
        InstanceHandler.commandHandler.start();
        InstanceHandler.NETWORK_HANDLER.packetValidationHandler(
            (socket, packet) -> verifiedClients.containsKey(socket.remoteAddress()) || LoginRequestPacket.class.equals(packet.getClass())
        );
        tcpServer.connectHandler(socket -> {
            InstanceHandler.NETWORK_HANDLER.bindNetworkHandlers(socket);
            socket.closeHandler(unused -> verifiedClients.remove(socket.remoteAddress()));
        }).listen(config.getNumberAsInteger("tcpPort"));
    }
    
    @Override
    public void interrupt() {
        stopServer();
        super.interrupt();
    }
    
    public void stopServer() {
        tcpServer.close();
    }
    
    public void validateAccount(SocketAddress addr, Account account) {
        verifiedClients.put(addr, account);
    }
    
    public Account createAccount(String username, byte[] password) {
        NbtTagCompound caches = dat.getFileData("caches.nbt");
        List<String> pendingAccounts = caches.getStringArrayAsList("pendingAccounts");
        if(!pendingAccounts.contains(username.toLowerCase()))
            return null;
        pendingAccounts.remove(username.toLowerCase());
        caches.setStringArray("pendingAccounts", pendingAccounts.toArray(new String[pendingAccounts.size()]));
        
        NbtTagCompound account = new NbtTagCompound();
        account.setInteger("id", caches.getTagList("accounts").size());
        account.setString("username", username.toLowerCase());
        account.setByteArray("password", Security.salt(Security.salt(password, Security.CLIENT_SALT), Security.SERVER_SALT));
        account.setTag("data", new NbtTagCompound());
        caches.getTagList("accounts").appendTag(account);
        
        InstanceHandler.saveData();
        
        return new Account(account);
    }
    
    public void saveAccountData(Account a) {
        dat.getFileData("caches.nbt").getTagList("accounts").setTag(a.id, a.toTagCompound());
    }
    
    public Account getAccount(int accountID) {
        NbtTagCompound nbt = dat.getFileData("caches.nbt").getTagList("accounts").getTagCompound(accountID);
        if(!nbt.containsKey("data"))
            nbt.setTag("data", new NbtTagCompound());
        return new Account(nbt);
    }
    
    public Account getAccount(String username) {
        NbtTagList accounts = dat.getFileData("caches.nbt").getTagList("accounts");
        for(int i = 0;i < accounts.size();++ i) {
            NbtTagCompound nbt = accounts.getTagCompound(i);
            if(username.equalsIgnoreCase(nbt.getString("username"))) {
                if(nbt.getInteger("id") != i)
                    nbt.setInteger("id", i);
                if(!nbt.containsKey("data"))
                    nbt.setTag("data", new NbtTagCompound());
                return new Account(nbt);
            }
        }
        return null;
    }
    
    public void setFilePermissions(File file, String uploader, List<String> downloaders) {
        NbtTagList fps = dat.getFileData("fperms.nbt").getTagList("main");
        fps.appendObject(new FilePermission(file, downloaders, uploader));
        InstanceHandler.saveData();
    }
    
    public boolean checkDelete(File file, String account) {
        NbtTagList fps = dat.getFileData("fperms.nbt").getTagList("main");
        for(int i = 0;i < fps.size();++ i) {
            FilePermission fp = (FilePermission)fps.getObject(i);
            if(file.getAbsolutePath().equals(fp.getFile().getAbsolutePath()) && account.equals(fp.getUploader()))
                return true;
        }
        return !file.exists();
    }
    
    public boolean checkDownload(File file, String account) {
        NbtTagList fps = dat.getFileData("fperms.nbt").getTagList("main");
        for(int i = 0;i < fps.size();++ i) {
            FilePermission fp = (FilePermission)fps.getObject(i);
            if(file.getAbsolutePath().equals(fp.getFile().getAbsolutePath())) {
                return fp.getDownloaders().contains(account) || fp.getDownloaders().isEmpty() || account.equals(fp.getUploader());
            }
        }
        return file.getParent().equals(config.getString("fileStorageDir"));
    }
    
    private static final class CommandImpl implements BiConsumer<Command, String[]> {
        final Server server;
        
        public CommandImpl(Server server) {
            this.server = server;
        }
        
        @Override
        public void accept(Command cmd, String[] args) {
            switch(cmd) {
                case LISTFILES:
                {
                    try {
                        Files.walkFileTree((new File(server.config.getString("fileStorageDir"))).toPath(), new SimpleFileVisitor<Path>(){
                            @Override
                            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                                    throws IOException {
                                String fs = file.toString();
                                Utils.log(fs.substring(fs.indexOf(File.separator) + 1));
                                return CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFileFailed(final Path file, final IOException e) {
                                return handleException(e);
                            }

                            private FileVisitResult handleException(final IOException e) {
                                return TERMINATE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(final Path dir, final IOException e)
                                    throws IOException {
                                if(e != null) return handleException(e);
                                return CONTINUE;
                            }
                        });
                    }catch(Throwable t) {
                        Utils.logError(t);
                    }
                    break;
                }
                case CACHE_ACCOUNT:
                {
                    if(args.length < 1) {
                        Utils.log("Usage: cache-account <username>");
                        return;
                    }
                    if(!args[0].matches("(\\w+)")) {
                        Utils.log("Invalid username.");
                        return;
                    }
                    List<String> cachedAccounts = server.dat.getFileData("caches.nbt").getStringArrayAsList("pendingAccounts");
                    cachedAccounts.add(args[0]);
                    Utils.log("Cached account.");
                    String[] caa = cachedAccounts.toArray(new String[cachedAccounts.size()]);
                    server.dat.getFileData("caches.nbt").setStringArray("pendingAccounts", caa);
                    Utils.log("Cached accounts: " + Arrays.toString(server.dat.getFileData("caches.nbt").getStringArray("pendingAccounts")));
                    InstanceHandler.saveData();
                    break;
                }
                case UNCACHE_ACCOUNT:
                {
                    if(args.length < 1) {
                        Utils.log("Usage: uncache-account <username>");
                        return;
                    }
                    List<String> cachedAccounts = server.dat.getFileData("caches.nbt").getStringArrayAsList("pendingAccounts");
                    cachedAccounts.remove(args[0]);
                    Utils.log("Uncached account.");
                    String[] caa = cachedAccounts.toArray(new String[cachedAccounts.size()]);
                    server.dat.getFileData("caches.nbt").setStringArray("pendingAccounts", caa);
                    Utils.log("Cached accounts: " + Arrays.toString(server.dat.getFileData("caches.nbt").getStringArray("pendingAccounts")));
                    InstanceHandler.saveData();
                    break;
                }
                case DEL_ACCOUNT:
                {
                    if(args.length < 1) {
                        Utils.log("Usage: delete-account <username>");
                        return;
                    }
                    NbtTagList accounts = server.dat.getFileData("caches.nbt").getTagList("accounts");
                    for(int i = 0;i < accounts.size();++ i) {
                        if(accounts.getTagCompound(i).getString("username").equalsIgnoreCase(args[0])) {
                            accounts.remove(i);
                            Utils.log("Removed account.");
                            return;
                        }
                    }
                    Utils.log("An account with that username does not exist.");
                    break;
                }
                case ACCOUNTS:
                {
                    NbtTagList accounts = server.dat.getFileData("caches.nbt").getTagList("accounts");
                    if(accounts.size() == 0) {
                        Utils.log("There are no accounts.");
                        return;
                    }
                    for(int i = 0;i < accounts.size();++ i)
                        Utils.log("Account#" + i + ": " + accounts.getTagCompound(i).getString("username"));
                    break;
                }
                case DELETE_FILE:
                {
                    if(args.length < 1) {
                        Utils.log("Usage: delete <file>");
                        return;
                    }
                    File file = new File(Utils.combinePathElements(server.config.getString("fileStorageDir"), args[0]));
                    if(!file.exists()) {
                        Utils.log("That file does not exist.");
                        return;
                    }
                    NbtTagList fps = server.dat.getFileData("fperms.nbt").getTagList("main");
                    int index = -1;
                    for(int i = 0;i < fps.size();++ i) {
                        FilePermission fp = (FilePermission)fps.getObject(i);
                        if(file.getAbsolutePath().equals(fp.getFile().getAbsolutePath()))
                            index = i;
                    }
                    Utils.log("Deleted file.");
                    if(index != -1) fps.remove(index);
                    file.delete();
                    break;
                }
                default: Utils.log(cmd + " is not supported.");
            }
        }
    }
}
