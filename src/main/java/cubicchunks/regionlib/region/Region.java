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
package cubicchunks.regionlib.region;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import cubicchunks.regionlib.CorruptedDataException;
import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.region.header.IHeaderDataEntryProvider;
import cubicchunks.regionlib.util.WrappedException;
import sun.misc.IOUtils;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * A basic region implementation
 *
 * @param <K> The key type
 */
public class Region<K extends IKey<K>> implements IRegion<K> {

	private final IKeyIdToSectorMap<?, ?, K> sectorMap;
	private final RegionSectorTracker<K> regionSectorTracker;

	private final SeekableByteChannel file;
	private List<IHeaderDataEntryProvider<?, K>> headerEntryProviders;
	private final int sectorSize;
	private Path path;

	private Region(SeekableByteChannel file, IntPackedSectorMap<K> sectorMap, RegionSectorTracker<K> sectorTracker,
	               List<IHeaderDataEntryProvider<?, K>> headerEntryProviders, int sectorSize) throws IOException {
		this.file = file;
		this.headerEntryProviders = headerEntryProviders;
		this.sectorSize = sectorSize;
		this.sectorMap = sectorMap;
		this.regionSectorTracker = sectorTracker;
	}

	@Override public synchronized void writeValue(K key, ByteBuffer value) throws IOException {
		int size = value.remaining();
		int sizeWithSizeInfo = size + Integer.BYTES;
		int numSectors = getSectorNumber(sizeWithSizeInfo);

		//overflow is thrown by this method
		RegionEntryLocation location = this.regionSectorTracker.reserveForKey(key, numSectors);

		if (location.isExternal())	{
			System.out.println(path.toAbsolutePath().toFile().getName());
			File f = new File(path.toFile().getParentFile(), "ext" + File.separator + key.getRegionName() + ".E" + key.getId() + ".bin");
			System.out.println("Saving data to external file: " + f.getAbsoluteFile().toString());
			if (f.exists()) {
				//clear the contents of the file before writing
				PrintWriter writer = new PrintWriter(f);
				writer.close();
			}
			Files.createDirectories(f.toPath().getParent());
			Files.write(f.toPath(), value.array());
		} else {

			int bytesOffset = location.getOffset() * sectorSize;

			file.position(bytesOffset).write(ByteBuffer.allocate(Integer.BYTES).putInt(0, size));
			file.write(value);
		}
		final int id = key.getId();
		final int sectorMapEntries = key.getKeyCount();
		int currentHeaderBytes = 0;
		for (IHeaderDataEntryProvider<?, K> prov : headerEntryProviders) {
			ByteBuffer buf = ByteBuffer.allocate(prov.getEntryByteCount());
			prov.apply(key).write(buf);
			buf.flip();
			file.position(currentHeaderBytes*sectorMapEntries + id*prov.getEntryByteCount()).write(buf);
			currentHeaderBytes += prov.getEntryByteCount();
		}
	}

	@Override public synchronized Optional<ByteBuffer> readValue(K key) throws IOException {
		// a hack because Optional can't throw checked exceptions
		try {
			return sectorMap.getEntryLocation(key).flatMap(loc -> {
				try {
					int sectorOffset = loc.getOffset();
					int sectorCount = loc.getSize();

					ByteBuffer buf = ByteBuffer.allocate(Integer.BYTES);

					if (loc.isExternal())	{
						System.out.println(path.toAbsolutePath().toFile().getName());
						File f = new File(path.toFile().getParentFile(), "ext" + File.separator + key.getRegionName() + ".E" + key.getId() + ".bin");
						System.out.println("Reading data from external file: " + f.getAbsoluteFile().toString());
						if (f.exists())	{
							byte[] data = IOUtils.readFully(new FileInputStream(f), -1, false);
							return Optional.of(ByteBuffer.wrap(data));
						} else {
							throw new IllegalStateException("External data doesn't exist! Path: " + f.getAbsoluteFile().toString());
						}
					}

					file.position(sectorOffset*sectorSize).read(buf);

					int dataLength = buf.getInt(0);
					if (dataLength > sectorCount*sectorSize) {
						throw new CorruptedDataException("Expected data size max" + sectorCount*sectorSize + " but found " + dataLength);
					}

					ByteBuffer bytes = ByteBuffer.allocate(dataLength);
					file.read(bytes);
					bytes.flip();
					return Optional.of(bytes);
				} catch (IOException e) {
					throw new WrappedException(e);
				}
			});
		} catch (WrappedException e) {
			throw (IOException) e.get();
		}
	}

	public Region setPath(Path path)	{
		this.path = path;
		return this;
	}

	/**
	 * Returns true if something was stored there before within this region.
	 */
	@Override public synchronized boolean hasValue(K key) {
		return sectorMap.getEntryLocation(key).isPresent();
	}


	private int getSectorNumber(int bytes) {
		return ceilDiv(bytes, sectorSize);
	}

	@Override public void close() throws IOException {
		this.file.close();
	}

	private static int ceilDiv(int x, int y) {
		return -Math.floorDiv(-x, y);
	}

	public static <L extends IKey<L>> Builder<L> builder() {
		return new Builder<>();
	}

	public static class Builder<K extends IKey<K>> {

		private Path path;
		private int entriesPerRegion;
		private int sectorSize = 512;
		private List<IHeaderDataEntryProvider> headerEntryProviders = new ArrayList<>();

		public Builder<K> setPath(Path path) {
			this.path = path;
			return this;
		}

		public Builder<K> setEntriesPerRegion(int entriesPerRegion) {
			this.entriesPerRegion = entriesPerRegion;
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

		public Region<K> build() throws IOException {
			SeekableByteChannel file = Files.newByteChannel(path, CREATE, READ, WRITE);

			int entryMappingBytes = entriesPerRegion*Integer.BYTES;
			int entryMapSectors = ceilDiv(entryMappingBytes, sectorSize);

			IntPackedSectorMap<K> sectorMap = IntPackedSectorMap.readOrCreate(file, entriesPerRegion);
			RegionSectorTracker<K> regionSectorTracker = RegionSectorTracker.fromFile(file, sectorMap, entryMapSectors, sectorSize);
			this.headerEntryProviders.add(0, sectorMap.headerEntryProvider());
			return new Region(file, sectorMap, regionSectorTracker, this.headerEntryProviders, this.sectorSize).setPath(path);
		}
	}
}
