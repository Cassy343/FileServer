package fs.client;

import fs.common.Command;
import fs.common.CommandHandler;
import fs.common.DataHandler;
import fs.common.ExitCode;
import fs.common.InstanceHandler;
import fs.common.Security;
import fs.common.Utils;
import fs.network.NetworkHandler;
import fs.network.packet.DownloadRequestPacket;
import fs.network.packet.FilesListRequestPacket;
import fs.network.packet.LoginRequestPacket;
import fs.network.packet.VsfRequestPacket;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.BiConsumer;
import stg.config.ConfigFormatException;
import stg.config.ini.IniFile;
import stg.generic.CompressionHelper;
import stg.nbt.NbtException;
import stg.nbt.NbtTagCompound;

public final class Client extends Thread {
    private final NetClient tcpClient;
    private NetSocket socket;
    private final IniFile config;
    private DataHandler dat;
    
    public Client(String configDir) {
        this.tcpClient = Utils.VERTX.createNetClient();
        initCommandHandler();
        this.config = new IniFile(configDir);
        try {
            initConfig();
            InstanceHandler.config = config;
            initDataHandler();
        }catch(IOException ioe) {
            throw new RuntimeException("Failed to read config.", ioe);
        }catch(ConfigFormatException cfe) {
            throw new RuntimeException("Invalid config format.", cfe);
        }catch(NbtException nbte) {
            throw new RuntimeException("A private data file was corrupted.", nbte);
        }
    }
    
    private void initCommandHandler() {
        InstanceHandler.commandHandler = new CommandHandler(System.in);
        InstanceHandler.commandHandler.disableCommands(
            Command.CACHE_ACCOUNT, Command.UNCACHE_ACCOUNT, Command.DEL_ACCOUNT,
            Command.ACCOUNTS, Command.DELETE_FILE // impl. dlt later
        );
        InstanceHandler.commandHandler.setProcessingCallback(new CommandImpl(this));
    }
    
    private void initConfig() throws IOException, ConfigFormatException {
        if(!config.getFile().exists())
            config.getFile().createNewFile();
        
        config.createSection("connect");
        config.putNumber("tcpPort", 5001);
        config.putString("tcpHost", "127.0.0.1");
        config.createSection("login");
        config.setComment("Account username (letters, numbers, and underscores)").putString("username", "");
        config.setComment("Your personal account's password").putString("password", "");
        config.createSection("other");
        config.putString("dataDir", "metadata");
        config.putString("downloadsDir", "downloads");
        
        config.sync(true);
        config.save();
        
        File downloadsDir = new File(config.getString("downloadsDir"));
        if(!downloadsDir.exists())
            downloadsDir.mkdirs();
    }
    
    private void initDataHandler() throws IOException, NbtException {
        dat = (InstanceHandler.dataHandler = new DataHandler(config.getString("dataDir"), "cd.nbt"));
        dat.loadData();
        NbtTagCompound cd = dat.getFileData("cd.nbt");
        if(!cd.containsKey("accountID"))
            cd.setInteger("accountID", -1);
        dat.saveData();
    }
    
    @Override
    public void run() {
        startServer();
    }
    
    public void startServer() {
        InstanceHandler.commandHandler.start();
        tcpClient.connect(config.getNumberAsInteger("connect", "tcpPort"), config.getString("connect", "tcpHost"), result -> {
            if(result.succeeded()) {
                socket = result.result();
                InstanceHandler.NETWORK_HANDLER.bindNetworkHandlers(socket);
                InstanceHandler.NETWORK_HANDLER.sendPacket(new LoginRequestPacket(
                    dat.getFileData("cd.nbt").getInteger("accountID"),
                    config.getString("login", "username"),
                    Security.hash(config.getString("login", "password").getBytes(), Security.CLIENT_SALT)
                ), socket);
                socket.closeHandler(unused -> {
                    InstanceHandler.stopThreads();
                    System.exit(0);
                });
            }else
                Utils.failOnError("Failed to connect to remote server.", ExitCode.NETWORK_ERROR);
        });
    }
    
    @Override
    public void interrupt() {
        stopClient();
        super.interrupt();
    }
    
    public void stopClient() {
        tcpClient.close();
    }
    
    public void setAccountID(int id) {
        dat.getFileData("cd.nbt").setInteger("accountID", id);
        InstanceHandler.saveData();
    }
    
    private static final class CommandImpl implements BiConsumer<Command, String[]> {
        final Client client;
        
        public CommandImpl(Client client) {
            this.client = client;
        }

        @Override
        public void accept(Command cmd, String[] args) {
            switch(cmd) {
                case UPLOAD:
                {
                    String f = null, fo = null;
                    boolean async = false, shared = false, zip = false;
                    int psize = (int)NetworkHandler.MAX_FTP_PACKET_SIZE;
                    String[] downloaders = {};
                    for(int i = 0;i < args.length;++ i) {
                        if(!args[i].startsWith("-")) {
                            if(f == null)
                                f = args[i];
                            else
                                fo = args[i];
                        }else if(args[i].toLowerCase().startsWith("-async")) {
                            async = true;
                            if(args[i].length() > 6 && args[i].contains(":")) {
                                String psizes = args[i].substring(args[i].indexOf(':') + 1);
                                try {
                                    psize = Integer.parseInt(psizes);
                                }catch(NumberFormatException ex) {
                                    Utils.log("Invalid sub-argument: " + psizes);
                                }
                            }
                        }else if(args[i].toLowerCase().startsWith("-shared")) {
                            shared = true;
                            if(args[i].length() > 7 && args[i].contains(":"))
                                downloaders = args[i].substring(args[i].indexOf(':') + 1).split(",");
                            else{
                                Utils.log("Invalid argument format. Use: -shared:usernames,...");
                                shared = false;
                            }
                        }else if(args[i].toLowerCase().startsWith("-zip"))
                            zip = true;
                        else
                            Utils.log("Invalid argument: " + args[i]);
                    }
                    if(f == null) {
                        Utils.log("Usage: upload [-async[:packetSize]] [-shared:usernames,...] [-zip] <file> [fileOut]");
                        return;
                    }
                    File file = new File(f);
                    if(!file.exists()) {
                        Utils.log("The file specified does not exist.");
                        return;
                    }
                    
                    if(file.isDirectory() && !zip) {
                        Utils.log("That path points to a directory. Rerun the command with -zip to upload the directory as a zip.");
                        return;
                    }else if(file.isDirectory() && zip) {
                        try {
                            Utils.log("Zipping directory...");
                            File tmp = new File(Files.createTempFile(file.getName().replaceAll("\\W", "_"), ".tmp").toUri());
                            fo = fo == null ? file.getName() + ".zip" : fo;
                            tmp.deleteOnExit();
                            CompressionHelper.zipFileOrDirectory(file, tmp, false);
                            f = tmp.getAbsolutePath();
                            file = tmp;
                            Utils.log("Finished zipping directory.");
                        }catch(IOException ex) {
                            Utils.log(ex);
                            return;
                        }
                    }
                    if(file.length() > 655200L && !async) {
                        Utils.log("This file is too large to be sent synchronously. It will be sent asynchronously instead.");
                        async = true;
                    }
                    NbtTagCompound nbt = new NbtTagCompound();
                    nbt.setBoolean("shared", shared);
                    nbt.setString("username", client.config.getString("login", "username"));
                    if(fo != null) nbt.setString("fileOut", fo);
                    if(shared) nbt.setStringArray("downloaders", downloaders);
                    if(async)
                        InstanceHandler.NETWORK_HANDLER.sendFileAsync(f, client.socket, Utils.constrain(psize, 1024, (int)NetworkHandler.MAX_FTP_PACKET_SIZE), nbt);
                    else
                        InstanceHandler.NETWORK_HANDLER.sendFile(f, client.socket, nbt);
                    if(zip) file.delete();
                    break;
                }
                case DOWNLOAD:
                {
                    if(args.length < 1) {
                        Utils.log("Usage: download <file>");
                        return;
                    }
                    InstanceHandler.NETWORK_HANDLER.sendPacket(new DownloadRequestPacket(args[0], client.dat.getFileData("cd.nbt").getInteger("accountID")), client.socket);
                    break;
                }
                case LISTFILES:
                {
                    InstanceHandler.NETWORK_HANDLER.sendPacket(new FilesListRequestPacket(), client.socket);
                    break;
                }
                case VIEW_SHARED_FILES:
                {
                    InstanceHandler.NETWORK_HANDLER.sendPacket(new VsfRequestPacket(client.dat.getFileData("cd.nbt").getInteger("accountID")), client.socket);
                    break;
                }
                default: Utils.log(cmd + " is not supported.");
            }
        }
    }
}
