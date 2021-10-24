package fs.main;

import fs.client.Client;
import fs.common.DataHandler;
import fs.server.Server;
import fs.common.InstanceHandler;
import fs.common.Utils;

public final class Main {
    private Main() { }
    
    public static void main(String[] args) {
        if(args.length < 2)
            throw new IllegalArgumentException("Usage: java -jar file-server.jar <client|server> <configFile>");
        DataHandler.enableCompression();
        if("client".equalsIgnoreCase(args[0])) {
            Utils.log("Starting module client (v" + InstanceHandler.VERSION + ").");
            Client client = new Client(args[1]);
            InstanceHandler.client = client;
            client.start();
        }else if("server".equalsIgnoreCase(args[0])) {
//            Utils.log("Server module not supported.");
//            System.exit(0);
            Utils.log("Starting module server (v" + InstanceHandler.VERSION + ").");
            Server server = new Server(args[1]);
            InstanceHandler.server = server;
            server.start();
        }else
            throw new IllegalArgumentException(args[1] + " is not a valid module.");
    }
}
