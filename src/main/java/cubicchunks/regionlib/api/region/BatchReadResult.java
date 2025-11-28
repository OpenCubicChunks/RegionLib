package cubicchunks.regionlib.api.region;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

public class BatchReadResult<K> {

    public final ImmutableMap<K, ByteBuffer> read;
    public final ImmutableMap<K, IOException> errored;

    public BatchReadResult(Map<K, ByteBuffer> read, Map<K, IOException> errored) {
        this.read = ImmutableMap.copyOf(read);
        this.errored = ImmutableMap.copyOf(errored);
    }
}
