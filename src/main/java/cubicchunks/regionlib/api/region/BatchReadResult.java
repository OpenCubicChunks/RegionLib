package cubicchunks.regionlib.api.region;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public class BatchReadResult<K> {

    public final Map<K, ByteBuffer> read;
    public final Map<K, IOException> errored;

    public BatchReadResult(Map<K, ByteBuffer> read, Map<K, IOException> errored) {
        this.read = read;
        this.errored = errored;
    }
}
