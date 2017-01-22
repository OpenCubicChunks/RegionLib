package cubicchunks.regionlib.region.header;

import java.util.concurrent.TimeUnit;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.IRegionKey;

public class TimestampHeaderEntryProvider<R extends IRegionKey<R, L>, L extends IKey<R, L>>
	implements IHeaderDataEntryProvider<IntHeaderEntry, R, L> {

	private final TimeUnit timeUnit;

	public TimestampHeaderEntryProvider(TimeUnit timeUnit) {

		this.timeUnit = timeUnit;
	}

	@Override public int getEntryByteCount() {
		return Integer.BYTES;
	}

	@Override public IntHeaderEntry apply(L o) {
		return new IntHeaderEntry((int) TimeUnit.MILLISECONDS.convert(System.currentTimeMillis(), timeUnit));
	}
}
