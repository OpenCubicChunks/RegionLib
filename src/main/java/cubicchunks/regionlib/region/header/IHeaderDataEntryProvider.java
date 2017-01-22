package cubicchunks.regionlib.region.header;

import java.util.function.Function;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.IRegionKey;

public interface IHeaderDataEntryProvider<H extends IHeaderDataEntry, R extends IRegionKey<R, L>, L extends IKey<R, L>> extends Function<L, H> {

	int getEntryByteCount();
}
