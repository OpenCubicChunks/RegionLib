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

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.BitSet;
import java.util.Optional;

import cubicchunks.regionlib.IKey;

public class RegionSectorTracker<K extends IKey<K>> {

	private final BitSet usedSectors;
	private IKeyIdToSectorMap<?, ?, K> sectorMap;

	public RegionSectorTracker(BitSet usedSectors, IKeyIdToSectorMap<?, ?, K> sectorMap) {
		this.usedSectors = usedSectors;
		this.sectorMap = sectorMap;
	}

	/**
	 * Returns offset for the given key and requestedSize, and reserves these sectors
	 */
	public RegionEntryLocation reserveForKey(K key, int requestedSize) throws IOException {
		if (requestedSize > IntPackedSectorMap.MAX_SIZE)	{
			return new RegionEntryLocation(-1, 0);
		}

		Optional<RegionEntryLocation> existing = sectorMap.getEntryLocation(key);
		RegionEntryLocation found = findSectorFor(existing.orElse(null), requestedSize);
		this.sectorMap.setOffsetAndSize(key, found);
		this.updateUsedSectorsFor(existing.orElse(null), found);
		return found;
	}

	private RegionEntryLocation findSectorFor(RegionEntryLocation oldSector, int requestedSize) {
		int oldSectorSize = oldSector == null ? 0 : oldSector.getSize();
		int newSectorSize = requestedSize;

		if (newSectorSize <= oldSectorSize) {
			return oldSector.withSize(requestedSize);
		}
		// first try at old sector location, or at 0 if it's not there
		int oldSectorOffset = oldSector == null ? 0 : oldSector.getOffset();
		boolean isEnough = true;
		for (int i = oldSectorOffset + oldSectorSize; i < oldSectorOffset + newSectorSize; i++) {
			if (!isSectorFree(i)) {
				isEnough = false;
				break;
			}
		}
		if (isEnough) {
			return oldSector.withSize(requestedSize);
		}
		return findNextFree(newSectorSize);
	}

	private RegionEntryLocation findNextFree(int requestedSize) {
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

		return new RegionEntryLocation(currentSector, requestedSize);
	}


	private void updateUsedSectorsFor(RegionEntryLocation oldSectorLocation, RegionEntryLocation newSectorLocation) {
		// mark old parts as unused
		if (oldSectorLocation != null) {
			int oldOffset = oldSectorLocation.getOffset();
			int oldSize = oldSectorLocation.getSize();
			for (int i = 0; i < oldSize; i++) {
				usedSectors.set(oldOffset + i, false);
			}
		}

		// mark new parts as used, this will work even if they overlap
		if (newSectorLocation != null) {
			int newOffset = newSectorLocation.getOffset();
			int newSize = newSectorLocation.getSize();
			for (int i = 0; i < newSize; i++) {
				usedSectors.set(newOffset + i, true);
			}
		}
	}

	private boolean isSectorFree(int sector) {
		return !usedSectors.get(sector);
	}

	public static <L extends IKey<L>> RegionSectorTracker<L> fromFile(
		SeekableByteChannel file, IKeyIdToSectorMap<?, ?, L> sectorMap, int reservedSectors, int sectorSize) throws IOException {
		// initialize usedSectors and make the header sectors as used
		BitSet usedSectors = new BitSet(Math.max((int) (file.size()/sectorSize), reservedSectors));
		for (int i = 0; i < reservedSectors; i++) {
			usedSectors.set(i, true);
		}
		// mark used sectors
		for (RegionEntryLocation loc : sectorMap) {
			int offset = loc.getOffset();
			int size = loc.getSize();
			for (int i = 0; i < size; i++) {
				usedSectors.set(offset + i);
			}
		}
		return new RegionSectorTracker<>(usedSectors, sectorMap);
	}
}
