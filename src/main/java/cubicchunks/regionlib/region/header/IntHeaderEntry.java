package cubicchunks.regionlib.region.header;

import java.nio.ByteBuffer;

public class IntHeaderEntry implements IHeaderDataEntry {

	private final int data;

	public IntHeaderEntry(int data) {
		this.data = data;
	}

	@Override public void write(ByteBuffer buffer) {
		buffer.putInt(data);
	}
}
