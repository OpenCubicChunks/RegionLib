/*
 *  This file is part of RegionLib, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2016 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.regionlib.lib.header;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import cubicchunks.regionlib.UnsupportedDataException;
import cubicchunks.regionlib.api.region.key.IKey;
import cubicchunks.regionlib.lib.RegionEntryLocation;

public class IntPackedSectorMap<K extends IKey<K>>
	implements IKeyIdToSectorMap<IntHeaderEntry, EntryLocationHeaderEntryProvider<K>, K> {

	private static final int SIZE_BITS = 8;
	private static final int OFFSET_BITS = Integer.SIZE - SIZE_BITS;
	private static final int SIZE_MASK = (1 << SIZE_BITS) - 1;
	private static final int MAX_SIZE = SIZE_MASK;
	private static final int OFFSET_MASK = (1 << OFFSET_BITS) - 1;
	private static final int MAX_OFFSET = OFFSET_MASK;

	private final int[] entrySectorOffsets;
	private final List<SpecialSectorMapEntry<K>> specialEntries;

	public IntPackedSectorMap(int[] data) {
		this(data, Collections.emptyList());
	}

	public IntPackedSectorMap(int[] data, List<SpecialSectorMapEntry<K>> specialEntries) {
		this.entrySectorOffsets = data;
		this.specialEntries = specialEntries;
	}

	@Override public Optional<Function<K, ByteBuffer>> trySpecialValue(K key) {
		if (specialEntries.isEmpty()) {
			return Optional.empty();
		}
		int packed = entrySectorOffsets[key.getId()];
		for (SpecialSectorMapEntry<K> entry : specialEntries) {
			if (entry.rawValue == packed) {
				return Optional.of(entry.reader);
			}
		}
		return Optional.empty();
	}

	@Override public Optional<RegionEntryLocation> getEntryLocation(int id) {
		int packed = entrySectorOffsets[id];
		if (packed == 0) {
			return Optional.empty();
		}
		return Optional.of(new RegionEntryLocation(unpackOffset(packed), unpackSize(packed)));
	}

	@Override public Optional<BiConsumer<K, ByteBuffer>> setOffsetAndSize(K key, RegionEntryLocation location) throws IOException {
		if (location.getSize() > MAX_SIZE) {
			throw new UnsupportedDataException("Max supported size " + MAX_SIZE + " but requested " + location.getSize());
		}
		if (location.getOffset() > MAX_OFFSET) {
			throw new UnsupportedDataException("Max supported offset " + MAX_OFFSET + " but requested " + location.getOffset());
		}
		BiConsumer<K, ByteBuffer> colflictHandler = null;
		int packed = packed(location);
		if (!specialEntries.isEmpty()) {
			for (SpecialSectorMapEntry<K> specialEntry : specialEntries) {
				if (specialEntry.rawValue == packed) {
					colflictHandler = specialEntry.conflictHandler;
				}
			}
		}
		entrySectorOffsets[key.getId()] = packed;
		return Optional.ofNullable(colflictHandler);
	}

	@Override public boolean isSpecial(RegionEntryLocation loc) {
		if (this.specialEntries.isEmpty()) {
			return false;
		}
		int packed = packed(loc);
		for (SpecialSectorMapEntry<K> specialEntry : specialEntries) {
			if (specialEntry.rawValue == packed) {
				return true;
			}
		}
		return false;
	}

	@Override public void setSpecial(K key, Object marker) {
		if (!specialEntries.isEmpty()) {
			for (SpecialSectorMapEntry<K> specialEntry : specialEntries) {
				if (specialEntry.marker == marker) {
					entrySectorOffsets[key.getId()] = specialEntry.rawValue;
					return;
				}
			}
		}
		throw new IllegalArgumentException("Unknown marker object" + marker);
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

	@Override public EntryLocationHeaderEntryProvider<K> headerEntryProvider() {
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


	public static <K extends IKey<K>> IntPackedSectorMap<K> readOrCreate(
			SeekableByteChannel file, int entriesPerRegion, List<SpecialSectorMapEntry<K>> specialEntries) throws IOException {
		int entryMappingBytes = Integer.BYTES*entriesPerRegion;
		// add a new blank header if this file is new. If the file is smaller than the header, there can't be any entries in it anyway
		// so can be safely filled with zeros
		if (file.size() < entryMappingBytes) {
			file.position(0);
			file.write(ByteBuffer.allocate(entryMappingBytes));
		}
		file.position(0);

		int[] entrySectorOffsets = new int[entriesPerRegion];

		// read the header into entrySectorOffsets
		ByteBuffer buffer = ByteBuffer.allocate(entryMappingBytes);
		file.read(buffer);
		buffer.flip();
		buffer.asIntBuffer().get(entrySectorOffsets);

		return new IntPackedSectorMap<>(entrySectorOffsets, specialEntries);
	}

	public static <L extends IKey<L>> IntPackedSectorMap<L> readOrCreate(SeekableByteChannel file, int entriesPerRegion) throws IOException {
		return readOrCreate(file, entriesPerRegion, Collections.emptyList());
	}

	public static class SpecialSectorMapEntry<K extends IKey<K>> {
		private final Object marker;
		private final int rawValue;
		private final Function<K, ByteBuffer> reader;
		private final BiConsumer<K, ByteBuffer> conflictHandler;

		public SpecialSectorMapEntry(Object marker, int rawValue, Function<K, ByteBuffer> reader,
				BiConsumer<K, ByteBuffer> conflictHandler) {
			this.marker = marker;
			this.rawValue = rawValue;
			this.reader = reader;
			this.conflictHandler = conflictHandler;
		}
	}
}
