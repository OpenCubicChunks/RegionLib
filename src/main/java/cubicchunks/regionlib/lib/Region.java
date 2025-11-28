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
package cubicchunks.regionlib.lib;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import cubicchunks.regionlib.api.region.BatchReadResult;
import cubicchunks.regionlib.api.region.IRegion;
import cubicchunks.regionlib.api.region.IRegionProvider;
import cubicchunks.regionlib.api.region.header.IHeaderDataEntryProvider;
import cubicchunks.regionlib.api.region.key.IKey;
import cubicchunks.regionlib.api.region.key.IKeyProvider;
import cubicchunks.regionlib.api.region.key.RegionKey;
import cubicchunks.regionlib.lib.header.IKeyIdToSectorMap;
import cubicchunks.regionlib.lib.header.IntPackedSectorMap;
import cubicchunks.regionlib.util.CheckedConsumer;
import cubicchunks.regionlib.util.CorruptedDataException;
import cubicchunks.regionlib.util.Utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A basic region implementation
 *
 * @param <K> The key type
 */
public class Region<K extends IKey<K>> implements IRegion<K> {

	private final IKeyIdToSectorMap<?, ?, K> sectorMap;
	private final RegionSectorTracker<K> regionSectorTracker;

	private final FileChannel file;
	private final List<IHeaderDataEntryProvider<?, K>> headerEntryProviders;
	private final int sectorSize;
	private final RegionKey regionKey;
	private final IKeyProvider<K> keyProvider;
	private final int keyCount;

	private Region(FileChannel file,
			IntPackedSectorMap<K> sectorMap,
			RegionSectorTracker<K> sectorTracker,
			List<IHeaderDataEntryProvider<?, K>> headerEntryProviders,
			RegionKey regionKey,
			IKeyProvider<K> keyProvider,
			int sectorSize) throws IOException {
		this.regionKey = regionKey;
		this.keyProvider = keyProvider;
		this.keyCount = keyProvider.getKeyCount(regionKey);
		this.file = file;
		this.headerEntryProviders = headerEntryProviders;
		this.sectorSize = sectorSize;
		this.sectorMap = sectorMap;
		this.regionSectorTracker = sectorTracker;
	}

	@Override public synchronized void writeValue(K key, ByteBuffer value) throws IOException {
		if (value == null) {
			this.regionSectorTracker.removeKey(key);
			updateHeaders(key);
			return;
		}
		int size = value.remaining();
		int sizeWithSizeInfo = size + Integer.BYTES;
		int numSectors = getSectorNumber(sizeWithSizeInfo);
		// this may throw UnsupportedDataException if data is too big
		RegionEntryLocation location = this.regionSectorTracker.reserveForKey(key, numSectors);

		int bytesOffset = location.getOffset()*sectorSize;

		Utils.writeFully(file.position(bytesOffset), ByteBuffer.allocate(Integer.BYTES).putInt(0, size));
		Utils.writeFully(file, value);
		updateHeaders(key);
	}

	@Override public void writeSpecial(K key, Object marker) throws IOException {
		this.regionSectorTracker.removeKey(key);
		this.sectorMap.setSpecial(key, marker);
		updateHeaders(key);
	}

	private void updateHeaders(K key) throws IOException {
		int id = key.getId();
		int currentHeaderBytes = 0;
		for (IHeaderDataEntryProvider<?, K> prov : headerEntryProviders) {
			ByteBuffer buf = ByteBuffer.allocate(prov.getEntryByteCount());
			prov.apply(key).write(buf);
			buf.flip();
			Utils.writeFully(file.position(currentHeaderBytes*keyCount + id*prov.getEntryByteCount()), buf);
			currentHeaderBytes += prov.getEntryByteCount();
		}
	}

	@Override
    public synchronized Optional<ByteBuffer> readValue(K key) throws IOException {
		// a hack because Optional can't throw checked exceptions
		try {
			return sectorMap.trySpecialValue(key)
					.map(reader -> Optional.of(reader.apply(key)))
					.orElseGet(() -> doReadKey(key));
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

    private Optional<ByteBuffer> doReadKey(K key) {
		return sectorMap.getEntryLocation(key).flatMap(loc -> {
			try {
				int sectorOffset = loc.getOffset();
				int sectorCount = loc.getSize();

				ByteBuffer buf = ByteBuffer.allocate(Integer.BYTES);

				Utils.readFully(file.position(sectorOffset * sectorSize), buf);

				int dataLength = buf.getInt(0);
				if (dataLength > sectorCount * sectorSize) {
					throw new CorruptedDataException(
							"Expected data size max" + sectorCount * sectorSize + " but found " + dataLength);
				}

				ByteBuffer bytes = ByteBuffer.allocate(dataLength);
				Utils.readFully(file, bytes);
				bytes.flip();
				return Optional.of(bytes);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	/**
	 * Returns true if something was stored there before within this region.
	 */
	@Override public synchronized boolean hasValue(K key) {
		return sectorMap.getEntryLocation(key).isPresent();
	}

	@Override public void forEachKey(CheckedConsumer<? super K, IOException> cons) throws IOException {
		for (int id = 0; id < this.keyCount; id++) {
			int idFinal = id; // because java is stupid
			K key = sectorMap.getEntryLocation(id).map(loc -> keyProvider.fromRegionAndId(this.regionKey, idFinal)).orElse(null);
			if (key != null) {
				cons.accept(key);
			}
		}
	}


	private int getSectorNumber(int bytes) {
		return ceilDiv(bytes, sectorSize);
	}

	@Override
	public void flush() throws IOException {
		this.ensureSectorSizeAligned();
		this.file.force(false);
	}

	@Override public void close() throws IOException {
		this.flush();
		this.file.close();
	}

	private void ensureSectorSizeAligned() throws IOException {
		if (file.size() % sectorSize != 0) {
			int extra = (int) (sectorSize - (file.size() % sectorSize));
			ByteBuffer buffer = ByteBuffer.allocateDirect(extra);
			this.file.position(this.file.size());
			Utils.writeFully(this.file, buffer);
			assert this.file.size() % sectorSize == 0;
		}
	}

	private static int ceilDiv(int x, int y) {
		return -Math.floorDiv(-x, y);
	}

	public static <L extends IKey<L>> Builder<L> builder() {
		return new Builder<>();
	}

	/**
	 * Internal Region builder. Using it is very unsafe, there are no safeguards against using it improperly. Should only be used by
	 * {@link IRegionProvider} implementations.
	 */
	// TODO: make a safer to use builder
	public static class Builder<K extends IKey<K>> {

		private Path directory;
		private int sectorSize = 512;
		private List<IHeaderDataEntryProvider<?, K>> headerEntryProviders = new ArrayList<>();
		private RegionKey regionKey;
		private IKeyProvider<K> keyProvider;
		private List<IntPackedSectorMap.SpecialSectorMapEntry<K>> specialEntries = new ArrayList<>();

		public Builder<K> setDirectory(Path path) {
			this.directory = path;
			return this;
		}

		public Builder<K> setRegionKey(RegionKey key) {
			this.regionKey = key;
			return this;
		}

		public Builder<K> setKeyProvider(IKeyProvider<K> keyProvider) {
			this.keyProvider = keyProvider;
			return this;
		}

		public Builder<K> setSectorSize(int sectorSize) {
			this.sectorSize = sectorSize;
			return this;
		}

		public Builder<K> addHeaderEntry(IHeaderDataEntryProvider<?, K> headerEntry) {
			headerEntryProviders.add(headerEntry);
			return this;
		}

		/**
		 * Creates a special {@link IntPackedSectorMap} entry type, with custom read function.
		 *
		 * The supplied conflict handler should handle conflicts in such way that the supplied reader will return the previously written value,
		 * or throw an exception is this is not possible.
		 *
		 * @param marker a marker object used to specify writing special value
		 * @param value the raw int header value
		 * @param specialReader data reader for this special value
		 * @param writeConflictHandler function for handling conflicts when writing real data with the same sectormap value.
		 *
		 */
		public Builder<K> addSpecialSectorMapEntry(Object marker, int value, Function<K, ByteBuffer> specialReader,
				BiConsumer<K, ByteBuffer> writeConflictHandler) {
			this.specialEntries.add(new IntPackedSectorMap.SpecialSectorMapEntry<>(marker, value, specialReader, writeConflictHandler));
			return this;
		}

		public Region<K> build() throws IOException {
			FileChannel file = FileChannel.open(directory.resolve(regionKey.getName()), CREATE, READ, WRITE);

			int entryMapBytes = Integer.BYTES;
			for (IHeaderDataEntryProvider<?, ?> prov : headerEntryProviders) {
				entryMapBytes += prov.getEntryByteCount();
			}
			int entryMapSectors = ceilDiv(keyProvider.getKeyCount(regionKey) * entryMapBytes, sectorSize);

			IntPackedSectorMap<K> sectorMap = IntPackedSectorMap.readOrCreate(file, keyProvider.getKeyCount(regionKey), specialEntries);
			RegionSectorTracker<K> regionSectorTracker = RegionSectorTracker.fromFile(file, sectorMap, entryMapSectors, sectorSize);
			this.headerEntryProviders.add(0, sectorMap.headerEntryProvider());
			return new Region<>(file, sectorMap, regionSectorTracker,
					this.headerEntryProviders, this.regionKey, keyProvider, this.sectorSize);
		}
	}
}
