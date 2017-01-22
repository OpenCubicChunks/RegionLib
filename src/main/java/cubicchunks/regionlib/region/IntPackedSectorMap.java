package cubicchunks.regionlib.region;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Iterator;
import java.util.Optional;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.IRegionKey;
import cubicchunks.regionlib.region.header.EntryLocationHeaderEntryProvider;
import cubicchunks.regionlib.region.header.IntHeaderEntry;

public class IntPackedSectorMap<R extends IRegionKey<R, L>, L extends IKey<R, L>>
	implements IKeyIdToSectorMap<IntHeaderEntry, EntryLocationHeaderEntryProvider<R, L>, R, L> {

	private static final int SIZE_BITS = 8;
	private static final int OFFSET_BITS = Integer.SIZE - SIZE_BITS;
	private static final int SIZE_MASK = (1 << SIZE_BITS) - 1;
	private static final int MAX_SIZE = SIZE_MASK;
	private static final int OFFSET_MASK = (1 << OFFSET_BITS) - 1;
	private static final int MAX_OFFSET = OFFSET_MASK;

	private final int[] entrySectorOffsets;

	public IntPackedSectorMap(int[] data) {
		this.entrySectorOffsets = data;
	}

	@Override public Optional<RegionEntryLocation> getEntryLocation(L key) {
		int packed = entrySectorOffsets[key.getId()];
		if (packed == 0) {
			return Optional.empty();
		}
		return Optional.of(new RegionEntryLocation(unpackOffset(packed), unpackSize(packed)));
	}

	@Override public void setOffsetAndSize(L key, RegionEntryLocation location) throws IOException {
		entrySectorOffsets[key.getId()] = packed(location);
	}

	@Override public Iterator<RegionEntryLocation> iterator() {
		int first = 0;
		while (first < entrySectorOffsets.length && entrySectorOffsets[first] == 0) {
			first++;
		}
		int firstIdx = first;
		return new Iterator<RegionEntryLocation>() {
			int idx = firstIdx;

			@Override public boolean hasNext() {
				return idx < entrySectorOffsets.length;
			}

			@Override public RegionEntryLocation next() {
				int packed = entrySectorOffsets[idx];
				RegionEntryLocation loc = new RegionEntryLocation(unpackOffset(packed), unpackSize(packed));
				do {
					idx++;
				} while (idx < entrySectorOffsets.length && entrySectorOffsets[idx] == 0);
				return loc;
			}
		};
	}

	@Override public EntryLocationHeaderEntryProvider<R, L> headerEntryProvider() {
		return new EntryLocationHeaderEntryProvider<>(this, IntPackedSectorMap::packed);
	}

	private static int unpackOffset(int sectorLocation) {
		return sectorLocation >>> SIZE_BITS;
	}

	private static int unpackSize(int sectorLocation) {
		return sectorLocation & SIZE_MASK;
	}

	private static int packed(RegionEntryLocation location) {
		if ((location.getSize() & SIZE_MASK) != location.getSize()) {
			throw new IllegalArgumentException("Supported entry size range is 0 to " + MAX_SIZE + ", but got " + location.getSize());
		}
		if ((location.getOffset() & OFFSET_MASK) != location.getOffset()) {
			throw new IllegalArgumentException("Supported entry offset range is 0 to " + MAX_OFFSET + ", but got " + location.getOffset());
		}
		return location.getSize() | (location.getOffset() << SIZE_BITS);
	}

	public static <R extends IRegionKey<R, L>, L extends IKey<R, L>> IntPackedSectorMap<R, L> readOrCreate(
		SeekableByteChannel file, int entriesPerRegion) throws IOException {
		int entryMappingBytes = Integer.SIZE*entriesPerRegion;
		// add a new blank header if this file is new
		if (file.size() < entryMappingBytes) {
			file.write(ByteBuffer.allocate((int) (entryMappingBytes - file.size())));
		}
		file.position(0);

		int[] entrySectorOffsets = new int[entriesPerRegion];

		// read the header into entrySectorOffsets
		ByteBuffer buffer = ByteBuffer.allocate(entryMappingBytes);
		file.read(buffer);
		buffer.flip();
		buffer.asIntBuffer().get(entrySectorOffsets);

		return new IntPackedSectorMap<>(entrySectorOffsets);
	}
}
