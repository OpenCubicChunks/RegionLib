package cubicchunks.regionlib.region.header;

import java.util.function.ToIntFunction;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.IRegionKey;
import cubicchunks.regionlib.region.IKeyIdToSectorMap;
import cubicchunks.regionlib.region.RegionEntryLocation;

public class EntryLocationHeaderEntryProvider<R extends IRegionKey<R, L>, L extends IKey<R, L>>
	implements IHeaderDataEntryProvider<IntHeaderEntry, R, L> {

	private IKeyIdToSectorMap<IntHeaderEntry, EntryLocationHeaderEntryProvider<R, L>, R, L> sectorMap;
	private ToIntFunction<RegionEntryLocation> pack;

	public EntryLocationHeaderEntryProvider(
		IKeyIdToSectorMap<IntHeaderEntry, EntryLocationHeaderEntryProvider<R, L>, R, L> sectorMap, ToIntFunction<RegionEntryLocation> pack) {
		this.sectorMap = sectorMap;

		this.pack = pack;
	}

	@Override public int getEntryByteCount() {
		return Integer.BYTES;
	}

	@Override public IntHeaderEntry apply(L key) {
		return new IntHeaderEntry(
			sectorMap.getEntryLocation(key)
				.map(l -> pack.applyAsInt(l))
				.orElse(0)
		);
	}
}
