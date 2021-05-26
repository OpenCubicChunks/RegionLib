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
package cubicchunks.regionlib.api.storage;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import cubicchunks.regionlib.MultiUnsupportedDataException;
import cubicchunks.regionlib.UnsupportedDataException;
import cubicchunks.regionlib.api.region.IRegionProvider;
import cubicchunks.regionlib.api.region.key.IKey;
import cubicchunks.regionlib.api.region.key.RegionKey;
import cubicchunks.regionlib.util.CheckedConsumer;
import cubicchunks.regionlib.util.SaveSectionException;

/**
 * A high level abstraction over the low level region-based storage API. Acts as a simple database for storing data for keys that are close
 * together/clumped. Example: Key could be an integer location in n-dimensional space, like Minecraft chunk locations.
 *
 * Allows to use fallback region providers for reading and writing, allowing for backwards compatibility when data organization gets changed,
 * or allows to use different storage method for entries not supported by the default IRegion implementation (standard Region implementetion
 * has an upper limit for size)
 *
 * @param <S> This type
 * @param <K> The location key type
 */
public abstract class SaveSection<S extends SaveSection<S, K>, K extends IKey<K>> implements Flushable, Closeable {
	private static final ByteBuffer DUMMY_EMPTY = ByteBuffer.allocate(0);
	private final List<IRegionProvider<K>> regionProviders;

	/**
	 * Creates a SaveSection with a customized IRegionProvider
	 *
	 * @param regionProvider The region provider
	 */
	public SaveSection(IRegionProvider<K> regionProvider) {
		this(Arrays.asList(regionProvider));
	}

	/**
	 * Creates a SaveSection with a customized IRegionProviders, with each next RegionProvider used as a fallback
	 * in case reading/writing is unsuccessful
	 *
	 * @param regionProviders The region providers
	 */
	public SaveSection(List<IRegionProvider<K>> regionProviders) {
		this.regionProviders = regionProviders;
	}

	/**
	 * Saves/puts a value at a key
	 * This method is thread safe.
	 *
	 * @param key The key
	 * @param value The value to save
	 * @throws IOException when an unexpected IO error occurs
	 */
	public void save(K key, ByteBuffer value) throws IOException {
		ByteBuffer toWrite = value;
		List<UnsupportedDataException> exceptions = new ArrayList<>();
		for (IRegionProvider<K> prov : regionProviders) {
			// variables used in lambdas must to be "effectively final"
			ByteBuffer toWriteFinal = toWrite;
			prov.forRegion(key, r -> {
				try {
					r.writeValue(key, toWriteFinal);
					exceptions.clear(); // clear exceptions on success
				} catch (UnsupportedDataException ex) {
					exceptions.add(ex);
					r.writeValue(key, null); // remove if write not successful
				}
			});
			// if successfully written, write null to all other region types
			if (exceptions.isEmpty()) {
				toWrite = null;
			}
		}
		if (!exceptions.isEmpty())
			throw new SaveSectionException("No region provider supporting key " + key + " with data size " + value.capacity(), exceptions);
	}

	/**
	 * Saves/puts multiple values at multiple keys
	 * This method is thread safe.
	 *
	 * @param entries the keys and values to save
	 * @throws IOException when an unexpected IO error occurs
	 */
	public void save(Map<K, ByteBuffer> entries) throws IOException {
		Map<K, ByteBuffer> pendingEntries = new HashMap<>(entries); //duplicate to avoid mutating input entry map
		Map<K, List<UnsupportedDataException>> exceptions = new HashMap<>(); //lists of pending exceptions that occurred on each entry

		//group positions into batches based on their containing region
		Map<RegionKey, List<K>> positionsByRegion = pendingEntries.keySet().stream().collect(Collectors.groupingBy(IKey::getRegionKey, Collectors.toList()));

		for (List<K> positionsIn : positionsByRegion.values()) {
			//for each position group (corresponding to a single region), emulate behavior of save(key, value)
			for (IRegionProvider<K> prov : regionProviders) {
				//we can safely retrieve element 0 as an arbitrary member of the positions in this region, as the list is guaranteed to be non-empty
				prov.forRegion(positionsIn.get(0), r -> {
					List<K> positions = positionsIn; //copy reference to allow modification inside lambda

					try {
						//write all entries to the region
						Map<K, ByteBuffer> regionEntries = new HashMap<>(positions.size());
						positions.forEach(k -> regionEntries.put(k, pendingEntries.get(k)));
						r.writeValues(regionEntries);
					} catch (MultiUnsupportedDataException ex) {
						//remove errored entries from the local positions set to prevent them from being cleaned up afterwards
						//we use a stream to avoid mutating the original positionsIn list
						positions = positions.stream().filter(((Predicate<K>) ex.<K>getChildren()::containsKey).negate()).collect(Collectors.toList());

						//save the exception that occurred at each position for later
						ex.<K>getChildren().forEach((k, e) -> exceptions.computeIfAbsent(k, unused -> new ArrayList<>()).add(e));

						//remove if write not successful
						Map<K, ByteBuffer> toNulls = new HashMap<>(positions.size());
						positions.forEach(k -> toNulls.put(k, null));
						r.writeValues(toNulls);
					}

					//positions now only contains entry keys that were able to be successfully written
					positions.forEach(k -> {
						//clear exceptions on success
						exceptions.remove(k);

						//if successfully written, write null to all other region types
						pendingEntries.put(k, null);
					});
				});
			}
		}

		if (!exceptions.isEmpty()) {
			throw new SaveSectionException("multiple write errors", exceptions.entrySet().stream()
					.map(e -> new SaveSectionException("No region provider supporting key " + e.getKey() + " with data size " + entries.get(e.getKey()), e.getValue()))
					.collect(Collectors.toList()));
		}
	}

	/**
	 * Loads/gets a value at a key
	 * This Method is thread safe.
	 *
	 * @param key The key
	 * @param createRegion if true, a new region file will be created and cached. This is the preferred option.
	 * @throws IOException when an unexpected IO error occurs
	 *
	 * @return An Optional containing the value if it exists
	 */
	public Optional<ByteBuffer> load(K key, boolean createRegion) throws IOException {
		for (IRegionProvider<K> prov : regionProviders) {
			ByteBuffer buf =
					createRegion ? prov.fromRegion(key, r -> r.readValue(key)).orElse(null)
							: prov.fromExistingRegion(key, r -> r.readValue(key)).orElse(Optional.of(DUMMY_EMPTY)).orElse(null);
			if (buf != null) {
				return buf == DUMMY_EMPTY ? Optional.empty() : Optional.of(buf);
			}
		}
		return Optional.empty();
	}

	/**
	 * Applies the given consumer to all already saved keys. Keys saved while this method is running are not guaranteed to be listed.
	 *
	 * Implementation note: this implementation will sacrifice overall performance for O(1) memory usage in case of many fallback region provider
	 *
	 * @param cons Consumer that will accept all the existing regions
	 *
	 * @throws IOException when an unexpected IO error occurs
	 */
	public void forAllKeys(CheckedConsumer<? super K, IOException> cons) throws IOException {
		for (int i = 0; i < regionProviders.size(); i++) {
			IRegionProvider<K> p = regionProviders.get(i);
			if (i == 0) {
				p.forAllRegions((key, reg) -> {
					reg.forEachKey(cons);
					reg.close();
				});
			} else {
				int max = i;
				p.forAllRegions((regionKey, reg) -> {
					reg.forEachKey(key -> {
						// cancel if any of the providers before contain this key
						for (int j = 0; j < max; j++) {
							K superKey = regionProviders.get(j)
									.getExistingRegion(key)
									.flatMap(r -> r.hasValue(key) ? Optional.of(key) : Optional.empty())
									.orElse(null);
							if (superKey != null) {
								return;
							}
						}
						cons.accept(key);
					});
					reg.close();
				});
			}
		}
	}

	public boolean hasEntry(K key) throws IOException {
		for (IRegionProvider<K> prov : this.regionProviders) {
			if (prov.fromExistingRegion(key, r -> r.hasValue(key)).orElse(false)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void flush() throws IOException {
		for (IRegionProvider<K> prov : this.regionProviders) {
			prov.flush();
		}
	}

	@Override
	public void close() throws IOException {
		for (IRegionProvider<K> prov : this.regionProviders) {
			prov.close();
		}
	}
}
