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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import cubicchunks.regionlib.util.SaveSectionException;
import cubicchunks.regionlib.api.region.key.IKey;
import cubicchunks.regionlib.api.region.IRegionProvider;
import cubicchunks.regionlib.util.CheckedConsumer;

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
public abstract class SaveSection<S extends SaveSection<S, K>, K extends IKey<K>> implements Closeable {

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
		List<IOException> exceptions = null;
		for (IRegionProvider<K> prov : regionProviders) {
			try {
				prov.forRegion(key, r -> r.writeValue(key, value));
				return;
			} catch (IOException ex) {
				if (exceptions == null) {
					exceptions = new ArrayList<>();
				}
				exceptions.add(ex);
			}
		}
		throw new SaveSectionException("No region provider supporting key " + key + " with data size " + value.capacity(), exceptions);
	}

	/**
	 * Loads/gets a value at a key
	 * This Method is thread safe.
	 *
	 * @param key The key
	 * @throws IOException when an unexpected IO error occurs
	 *
	 * @return An Optional containing the value if it exists
	 */
	public Optional<ByteBuffer> load(K key) throws IOException {
		for (IRegionProvider<K> prov : regionProviders) {
			Optional<ByteBuffer> opt = prov.fromExistingRegion(key, r -> r.readValue(key)).flatMap(x -> x);
			if (opt.isPresent()) {
				return opt;
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
				p.forAllRegions(reg -> {
					reg.forEachKey(cons);
				});
			} else {
				int max = i;
				p.forAllRegions(reg -> {
					reg.forEachKey(key -> {
						boolean prevContains = false;

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
				});
			}
		}
	}

	public boolean hasEntry(K key) throws IOException {
		boolean found = false;
		for (IRegionProvider<K> prov : this.regionProviders) {
			if (prov.fromExistingRegion(key, r -> r.hasValue(key)).orElse(false)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void close() throws IOException {
		for (IRegionProvider<K> prov : this.regionProviders) {
			prov.close();
		}
	}
}
