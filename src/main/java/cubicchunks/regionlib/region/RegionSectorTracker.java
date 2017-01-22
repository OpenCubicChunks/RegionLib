package cubicchunks.regionlib.region;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.BitSet;
import java.util.Optional;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.IRegionKey;

public class RegionSectorTracker<R extends IRegionKey<R, L>, L extends IKey<R, L>> {

	private final BitSet usedSectors;
	private IKeyIdToSectorMap<?, ?, R, L> sectorMap;

	public RegionSectorTracker(BitSet usedSectors, IKeyIdToSectorMap<?, ?, R, L> sectorMap) {
		this.usedSectors = usedSectors;
		this.sectorMap = sectorMap;
	}

	/**
	 * Returns offset for the given key and requestedSize, and reserves these sectors
	 */
	public RegionEntryLocation reserveForKey(L key, int requestedSize) throws IOException {
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

	public static <R extends IRegionKey<R, L>, L extends IKey<R, L>> RegionSectorTracker<R, L> fromFile(
		SeekableByteChannel file, IKeyIdToSectorMap<?, ?, R, L> sectorMap, int reservedSectors, int sectorSize) throws IOException {
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
