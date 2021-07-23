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

import cubicchunks.regionlib.MultiUnsupportedDataException;
import cubicchunks.regionlib.UnsupportedDataException;
import cubicchunks.regionlib.api.region.IRegionProvider;
import cubicchunks.regionlib.api.region.key.IKey;
import cubicchunks.regionlib.api.region.key.RegionKey;
import cubicchunks.regionlib.util.CheckedConsumer;
import cubicchunks.regionlib.util.SaveSectionException;
import cubicchunks.regionlib.util.Utils;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
	 * Saves/puts multiple values at multiple keys.
	 * This method is thread safe.
	 *
	 * @param entries a mutable map containing the keys and values to save
	 * @throws IOException when an unexpected IO error occurs. If thrown, all mappings that remain in {@code entries}
	 */
	public void save(Map<K, ByteBuffer> entries) throws IOException {
		Map<K, ByteBuffer> pendingEntries = new HashMap<>(entries); //duplicate to avoid mutating input entry map
		Map<K, List<UnsupportedDataException>> exceptions = new HashMap<>(); //lists of pending exceptions that occurred on each entry

		//group positions into batches based on their containing region
		Map<RegionKey, List<K>> positionsByRegion = pendingEntries.keySet().stream().collect(Collectors.groupingBy(IKey::getRegionKey, Collectors.toList()));

		for (List<K> positionsIn : positionsByRegion.values()) {
			//for each position group (corresponding to a single region), emulate behavior of save(key, value)
			for (IRegionProvider<K> prov : this.regionProviders) {
				//we can safely retrieve element 0 as an arbitrary member of the positions in this region, as the list is guaranteed to be non-empty
				prov.forRegion(positionsIn.get(0), r -> {
					List<K> positions = positionsIn; //copy reference to allow modification inside lambda

					try {
						//write all entries to the region
						Map<K, ByteBuffer> regionEntries = new HashMap<>(positions.size());
						positions.forEach(k -> regionEntries.put(k, pendingEntries.get(k)));
						r.writeValues(regionEntries);
					} catch (MultiUnsupportedDataException ex) {
						Map<K, UnsupportedDataException> children = ex.getChildren();

						//remove errored entries from the local positions set to prevent them from being cleaned up afterwards
						//we use a stream to avoid mutating the original positionsIn list
						positions = positions.stream().filter(((Predicate<K>) children::containsKey).negate()).collect(Collectors.toList());

						//save the exception that occurred at each position for later
						children.forEach((k, e) -> exceptions.computeIfAbsent(k, unused -> new ArrayList<>()).add(e));

						//remove if write not successful
						Map<K, ByteBuffer> toNulls = new HashMap<>(positions.size());
						children.forEach((k, v) -> toNulls.put(k, null));
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

		if (exceptions.isEmpty()) {
			//all entries were written successfully, so nothing should be allowed to remain in the map
			entries.clear();
		} else {
			entries.keySet().removeIf(((Predicate<K>) exceptions::containsKey).negate());
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
	 * Gets a {@link Stream} over all the already saved keys. Keys saved while the {@link Stream} is being evaluated are not guaranteed to be listed.
	 * <p>
	 * Note that the keys are not necessarily guaranteed to be distinct, although the chances of encountering a duplicate key are slim to none.
	 * <p>
	 * Note that the returned {@link Stream} must be closed (using {@link Stream#close()}) once no longer needed.
	 *
	 * @return a {@link Stream} over all already saved keys
	 * @throws IOException when an unexpected IO error occurs
	 */
	public Stream<K> allKeys() throws IOException {
		List<Stream<K>> streams = new ArrayList<>(this.regionProviders.size());
		try {
			//the original code here did some fancy stuff with checking previous region providers to ensure there were never any duplicate key returned.
			//  however, this isn't actually needed - when writing, we already make sure to remove keys from other region providers, which tells us
			//  two things:
			//  - there will never be any duplicate keys returned unless a write issued during iteration causes a key to be moved from one region provider
			//    to another, in which case it may be returned either twice or never
			//  - the original code actually had the exact same weakness, with the additional caveat that it's significantly slower
			//
			//  unfortunately, this cannot be resolved at all without one of the following:
			//  - causing all writes to block during iteration (which would still be problematic if writes are issued *during* iteration)
			//  - requiring every region provider to be implemented using a snapshot-capable database or CoW filesystem such as BTRFS, in order to
			//    allow us to take a snapshot and using that as our iteration source while completely ignoring any writes issued during iteration
			//  - reverting the unfortunate design choice of allowing multiple region providers to exist in the first place, and implementing external
			//    regions as part of standard ones

			for (IRegionProvider<K> regionProvider : this.regionProviders) {
				streams.add(regionProvider.allKeys());
			}
		} catch (IOException e) {
			//close any streams that may already have been created
			streams.forEach(Stream::close);
			throw e;
		}

		return Utils.concat(streams);
	}

	/**
	 * Applies the given consumer to all already saved keys. Keys saved while this method is running are not guaranteed to be listed.
	 * <p>
	 * Implementation note: this implementation will sacrifice overall performance for O(1) memory usage in case of many fallback region provider
	 *
	 * @param cons Consumer that will accept all the existing regions
	 * @throws IOException when an unexpected IO error occurs
	 * @deprecated see {@link #allKeys()}
	 */
	@Deprecated
	public void forAllKeys(CheckedConsumer<? super K, IOException> cons) throws IOException {
		try (Stream<K> stream = this.allKeys()) {
			stream.forEach(key -> {
				try {
					cons.accept(key);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (UncheckedIOException e) {
			throw e.getCause();
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
