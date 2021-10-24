package fs.common;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.function.BiConsumer;

public class CommandHandler extends Thread {
    private final List<Command> disabledCommands;
    private Scanner scanner;
    private boolean questioning;
    private BiConsumer<Command, String[]> processingCallback;
    
    public CommandHandler(InputStream in) {
        setName("CommandHandler");
        this.disabledCommands = new ArrayList<>();
        this.scanner = new Scanner(in);
        this.questioning = false;
        this.processingCallback = null;
    }
    
    @Override
    public void run() {
        questionCommand();
    }
    
    public final void questionCommand() {
        System.out.print("> ");
        questioning = true;
        String nextCmd = scanner.nextLine();
        questioning = false;
        if(nextCmd.trim().isEmpty()) {
            questionCommand();
            return;
        }
        String[] cmdData = Utils.safeArgSplit(nextCmd), args = new String[Math.max(cmdData.length - 1, 0)];
        if(cmdData.length == 0) {
            questionCommand();
            return;
        }
        Command cmd = Command.forString(cmdData[0]);
        if(cmd == null) {
            Utils.log(cmdData[0] + " is an invalid command.");
            questionCommand();
            return;
        }
        if(disabledCommands.contains(cmd)) {
            Utils.log(cmdData[0] + " is a disabled command.");
            questionCommand();
            return;
        }
        if(args.length != 0)
            System.arraycopy(cmdData, 1, args, 0, args.length);
        processCommand(cmd, args);
    }
    
    public final synchronized void disableCommands(Command... commands) {
        disabledCommands.addAll(Arrays.asList(commands));
    }
    
    public final synchronized void setProcessingCallback(BiConsumer<Command, String[]> callback) {
        processingCallback = callback;
    }
    
    public final synchronized boolean questioning() {
        return questioning;
    }
    
    public void processCommand(Command command, String[] args) {
        switch(command) {
            case STOP:
                Utils.log("Stopping command manager...");
                Utils.log("Stopping internal server...");
                InstanceHandler.stopThreads();
                System.runFinalization();
                System.exit(0);
                break;
            default:
                if(processingCallback != null)
                    processingCallback.accept(command, args);
                else
                    Utils.log("The command \"" + command.toString().toLowerCase() + "\" currently has no implementation.");
                break;
        }
        questionCommand();
    }
}
