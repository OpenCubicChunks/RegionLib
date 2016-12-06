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

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.BitSet;
import java.util.Optional;

import cubicchunks.regionlib.CurruptedDataException;
import cubicchunks.regionlib.IEntryLocation;

public class Region<R extends IRegionLocation<R, L>, L extends IEntryLocation<R, L>> implements Closeable {

	private static final int PRE_DATA_SIZE = 4;
	private static final boolean FORCE_WRITE_LOCATIONS = true;

	private final RandomAccessFile file;
	private final int sectorSize;
	private final BitSet usedSectors;

	private int[] entrySectorOffsets;

	public Region(RandomAccessFile file, int entriesPerRegion, int sectorSize) throws IOException {
		this.file = file;
		this.sectorSize = sectorSize;
		this.usedSectors = new BitSet(32);
		this.entrySectorOffsets = new int[entriesPerRegion];

		int entryMappingBytes = entriesPerRegion*Integer.BYTES;
		int entryMapSectors = ceilDiv(entryMappingBytes, sectorSize);
		for (int i = 0; i < entryMapSectors; i++) {
			this.usedSectors.set(i, true);
		}

		for (long i = file.length(); i <= entryMappingBytes; i++) {
			file.writeByte(0);
		}
		file.seek(0);

		for (int i = 0; i < entriesPerRegion; i++) {
			entrySectorOffsets[i] = file.readInt();
		}
	}

	public synchronized void writeEntry(L location, byte[] data) throws IOException {
		int oldSectorLocation = getExistingSectorLocationFor(location);
		int sectorLocation = findSectorFor(data.length, oldSectorLocation);

		int bytesOffset = unpackOffset(sectorLocation)*sectorSize;
		file.seek(bytesOffset);
		file.writeInt(data.length);
		file.write(data);
		writeSectorLocationFor(location, sectorLocation);
		updateUsedSectorsFor(oldSectorLocation, sectorLocation);
	}

	private void updateUsedSectorsFor(int oldSectorLocation, int newSectorLocation) {
		// mark old parts as unused
		int oldOffset = unpackOffset(oldSectorLocation);
		int oldSize = unpackSize(oldSectorLocation);
		for (int i = 0; i < oldSize; i++) {
			usedSectors.set(oldOffset + i, false);
		}

		// mark new parts as used, this will work even if they overlap
		int newOffset = unpackOffset(newSectorLocation);
		int newSize = unpackSize(newSectorLocation);
		for (int i = 0; i < newSize; i++) {
			usedSectors.set(newOffset + i, true);
		}
	}

	public synchronized Optional<byte[]> readEntry(L location) throws IOException, CurruptedDataException {
		int sectorLocation = getExistingSectorLocationFor(location);

		if (sectorLocation == 0) {
			return Optional.empty();
		}

		int sectorOffset = unpackOffset(sectorLocation);
		int sizeSectors = unpackSize(sectorLocation);

		file.seek(sectorOffset*sectorSize);
		int dataLength = file.readInt();
		if (dataLength > sizeSectors*sectorSize) {
			throw new CurruptedDataException("Expected data size max" + sizeSectors*sectorSize + " but found " + dataLength);
		}
		byte[] bytes = new byte[dataLength];
		file.readFully(bytes);
		return Optional.of(bytes);
	}

	private int findSectorFor(int length, int oldSectorLocation) {
		int entryBytes = length + PRE_DATA_SIZE;
		int oldSectorSize = unpackSize(oldSectorLocation);
		int newSectorSize = getSectorSize(entryBytes);

		if (newSectorSize <= oldSectorSize) {
			return unpackOffset(oldSectorLocation) << 8 | newSectorSize;
		} else {
			// first try at old sector location
			int oldSectorOffset = unpackOffset(oldSectorLocation);
			boolean isEnough = true;
			for (int i = oldSectorOffset + oldSectorSize; i < oldSectorOffset + newSectorSize; i++) {
				if (!isSectorFree(i)) {
					isEnough = false;
					break;
				}
			}
			if (isEnough) {
				return unpackOffset(oldSectorLocation) << 8 | newSectorSize;
			}
			return findNextFree(newSectorSize);
		}
	}

	private int findNextFree(int requestedSize) {
		int currentRun = 0;
		int currentSector = 0;
		do {
			// the first sector is always used no matter what
			currentSector++;
			if (isSectorFree(currentSector)) {
				currentRun++;
			} else {
				currentRun = 0;
			}
		} while (currentRun != requestedSize);

		// go back to the beginning
		currentSector -= currentRun - 1;

		return currentSector << 8 | requestedSize;
	}

	private boolean isSectorFree(int sector) {
		return !usedSectors.get(sector);
	}

	private int getExistingSectorLocationFor(L location) {
		return this.entrySectorOffsets[location.getId()];
	}

	private void writeSectorLocationFor(L location, int sectorLocation) throws IOException {
		int entryId = location.getId();
		this.entrySectorOffsets[entryId] = sectorLocation;
		if (FORCE_WRITE_LOCATIONS) {
			file.seek(entryId*Integer.BYTES);
			file.writeInt(sectorLocation);
		}
	}

	private int getSectorSize(int bytes) {
		return ceilDiv(bytes, sectorSize);
	}

	@Override public void close() throws IOException {
		this.file.close();
	}

	private static int unpackOffset(int sectorLocation) {
		return sectorLocation >>> 8;
	}

	public static int unpackSize(int sectorLocation) {
		return sectorLocation & 0xFF;
	}

	private static int ceilDiv(int x, int y) {
		return -Math.floorDiv(-x, y);
	}
}
