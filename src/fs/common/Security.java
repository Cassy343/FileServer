package fs.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Security {
    public static final byte[] SERVER_SALT =
    {26, 43, -66, 20, -63, -80, 52, -77, 83, -108, -80, -113, -82, 84, -52, -35,
    50, 23, -67, -30, -118, 82, -8, -91, -84, 23, -92, 49, -105, -103, 122, -23};
    
    public static final byte[] CLIENT_SALT =
    {-108, -16, -23, -98, -83, -61, 112, 105, 24, 3, -114, -127, 68, -40, 118, 104,
     113, -10, 65, -92, 82, 109, -90, -48, -40, -82, -41, -79, 18, 110, -7, 69};
    
    private Security() { }
    
    public static byte[] hash(byte[] bytes, byte[] salt) {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        }catch(NoSuchAlgorithmException ex) {
            throw new InternalError(ex);
        }
        byte[] digest = sha256.digest(bytes);
        for(int i = 0;i < 32;++ i) digest[i] ^= salt[i];
        return digest;
    }
    
    public static byte[] salt(byte[] original, byte[] salt) {
        byte[] copy = new byte[32];
        for(int i = 0;i < 32;++ i) copy[i] = (byte)(original[i] ^ salt[i]);
        return copy;
    }
}
