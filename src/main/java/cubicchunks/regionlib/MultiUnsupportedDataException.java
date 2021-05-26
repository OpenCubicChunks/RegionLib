package cubicchunks.regionlib;

import java.io.IOException;
import java.util.Map;

public class MultiUnsupportedDataException extends IOException {
    private final Map<?, UnsupportedDataException> children;

    public MultiUnsupportedDataException(Map<?, UnsupportedDataException> children) {
        this.children = children;
    }

    @SuppressWarnings("unchecked")
    public <K> Map<K, UnsupportedDataException> getChildren() {
        return (Map<K, UnsupportedDataException>) children;
    }
}
