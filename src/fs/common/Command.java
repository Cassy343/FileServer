package fs.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum Command {
    STOP("shutdown"),
    DOWNLOAD("dl"),
    UPLOAD("ul"),
    LISTFILES("list-files", "lf", "ls"),
    CACHE_ACCOUNT(false, "cache-account", "ca"),
    UNCACHE_ACCOUNT(false, "uncache-account", "uca"),
    DEL_ACCOUNT(false, "delete-account", "da"),
    ACCOUNTS,
    DELETE_FILE(false, "delete", "dlt"),
    VIEW_SHARED_FILES(false, "view-shared-files", "vsf");
    
    public static final Command[] VALUES = values();
    
    private final List<String> aliases;
    
    private Command(boolean keepDefault, String... aliases) {
        this.aliases = new ArrayList<>();
        this.aliases.addAll(Arrays.asList(aliases));
        if(keepDefault)
            this.aliases.add(toString().toLowerCase());
    }
    
    private Command(String... aliases) {
        this(true, aliases);
    }
    
    public static Command forString(String cmd) {
        for(Command c : VALUES) {
            if(c.aliases.contains(cmd.toLowerCase()))
                return c;
        }
        return null;
    }
}
