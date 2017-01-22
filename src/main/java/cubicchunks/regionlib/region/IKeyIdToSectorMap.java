package cubicchunks.regionlib.region;

import java.io.IOException;
import java.util.Optional;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.IRegionKey;
import cubicchunks.regionlib.region.header.IHeaderDataEntry;
import cubicchunks.regionlib.region.header.IHeaderDataEntryProvider;

public interface IKeyIdToSectorMap<
	H extends IHeaderDataEntry,
	P extends IHeaderDataEntryProvider<H, R, L>,
	R extends IRegionKey<R, L>,
	L extends IKey<R, L>> extends Iterable<RegionEntryLocation> {

	Optional<RegionEntryLocation> getEntryLocation(L key);

	void setOffsetAndSize(L key, RegionEntryLocation location) throws IOException;

	P headerEntryProvider();
}
