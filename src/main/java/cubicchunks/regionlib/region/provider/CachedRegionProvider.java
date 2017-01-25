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
package cubicchunks.regionlib.region.provider;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.region.IRegion;

/**
 * An implementation of IRegionProvider that caches opened Regions
 *
 * @param <K> The key type
 */
public class CachedRegionProvider<K extends IKey<K>> implements IRegionProvider<K> {

	private IRegionProvider<K> sourceProvider;
	private final Map<String, IRegion<K>> regionLocationToRegion;
	private int maxCacheSize;

	private boolean closed;

	/**
	 * Creates a RegionProvider using the given {@code regionFactory} and {@code maxCacheSize}
	 *
	 * @param sourceProvider provider used as source of regions
	 * @param maxCacheSize The maximum number of cached region files
	 */
	public CachedRegionProvider(IRegionProvider<K> sourceProvider, int maxCacheSize) {
		this.sourceProvider = sourceProvider;
		this.regionLocationToRegion = new HashMap<>(maxCacheSize*2); // methods below are synchronized anyway, no need for concurrent map
		this.maxCacheSize = maxCacheSize;
	}

	@Override
	public synchronized IRegion<K> getRegion(K key) throws IOException {
		if (closed) {
			throw new IllegalStateException("Already closed");
		}
		return getRegion(key, true); // it can't be null here
	}

	@Override
	public synchronized Optional<IRegion<K>> getRegionIfExists(K key) throws IOException {
		if (closed) {
			throw new IllegalStateException("Already closed");
		}
		return Optional.ofNullable(getRegion(key, false));
	}

	@Override public void returnRegion(String name) {
		// no-op
	}

	@Override public Iterator<String> allRegions() throws IOException {
		return sourceProvider.allRegions();
	}

	@Override public void close() throws IOException {
		if (closed) {
			throw new IllegalStateException("Already closed");
		}
		this.clearRegions();
		this.sourceProvider.close();
		this.closed = true;
	}

	private synchronized IRegion<K> getRegion(K location, boolean canCreate) throws IOException {
		if (regionLocationToRegion.size() > maxCacheSize) {
			this.clearRegions();
		}

		IRegion<K> region = regionLocationToRegion.get(location.getRegionName());
		if (region == null) {
			region = canCreate ? sourceProvider.getRegion(location) : sourceProvider.getRegionIfExists(location).orElse(null);
			if (region != null) {
				regionLocationToRegion.put(location.getRegionName(), region);
			}
		}
		return region;
	}

	private void clearRegions() throws IOException {
		Iterator<String> it = regionLocationToRegion.keySet().iterator();
		while (it.hasNext()) {
			this.sourceProvider.returnRegion(it.next());
			it.remove();
		}
	}

	/**
	 * Creates a RegionProvider with default settings using the given {@code directory}
	 *
	 * @param directory The directory that region files are stored in
	 */
	public static <L extends IKey<L>> CachedRegionProvider<L> makeProvider(
		Path directory) {
		return makeProvider(directory, 512, 128);
	}

	/**
	 * Creates a RegionProvider with custom sector size and custom max cache size
	 * using the given {@code directory}
	 *
	 * @param directory The directory that region files are stored in
	 * @param sectorSize The sector size used in the region files
	 * @param maxSize The maximum number of cached region files
	 */
	public static <K extends IKey<K>> CachedRegionProvider<K> makeProvider(Path directory, int sectorSize, int maxSize) {
		return new CachedRegionProvider<K>(SimpleRegionProvider.createDefault(directory, sectorSize), maxSize);
	}
}
