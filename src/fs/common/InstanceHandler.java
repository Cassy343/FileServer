package fs.common;

import fs.client.Client;
import fs.network.NetworkHandler;
import fs.network.Side;
import fs.server.Server;
import java.io.IOException;
import stg.config.ini.IniData;
import stg.nbt.NbtException;

public final class InstanceHandler {
    public static final NetworkHandler NETWORK_HANDLER = new NetworkHandler();
    public static Server server;
    public static Client client;
    public static CommandHandler commandHandler;
    public static DataHandler dataHandler;
    public static IniData config;
    
    public static final short VERSION = 8;
    
    public static Side side() {
        return server == null ? Side.CLIENT : Side.SERVER;
    }
    
    public static void saveData() {
        if(dataHandler == null) {
            Utils.log("A data handler has not been registered.");
            return;
        }
        try {
            dataHandler.saveData();
        }catch(IOException | NbtException ex) {
            Utils.err("Failed to save data.");
            Utils.logError(ex);
        }
    }
    
    public static void stopThreads() {
        NETWORK_HANDLER.destructor();
        if(server != null)
            server.interrupt();
        if(client != null)
            client.interrupt();
        if(commandHandler != null)
            commandHandler.interrupt();
        if(dataHandler != null) {
            try {
                dataHandler.saveData();
            }catch(IOException | NbtException ex) { }
        }
    }
}
