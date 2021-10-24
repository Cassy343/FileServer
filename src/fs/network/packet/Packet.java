package fs.network.packet;

import stg.generic.ByteBuffer;

public interface Packet {
    void serialize(ByteBuffer buffer);
    
    void deserialize(ByteBuffer buffer);
}
