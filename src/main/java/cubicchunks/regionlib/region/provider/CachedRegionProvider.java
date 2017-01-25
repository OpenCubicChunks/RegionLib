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
import java.util.function.Function;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.IRegionKey;
import cubicchunks.regionlib.region.IRegion;

/**
 * An implementation of IRegionProvider that caches opened Regions
 *
 * @param <R> The region key type
 * @param <L> The key type
 */
public class CachedRegionProvider<R extends IRegionKey<R, L>, L extends IKey<R, L>> implements IRegionProvider<R, L> {

	private IRegionProvider<R, L> sourceProvider;
	private final Map<R, IRegion<R, L>> regionLocationToRegion;
	private int maxCacheSize;

	private boolean closed;

	/**
	 * Creates a RegionProvider using the given {@code regionFactory} and {@code maxCacheSize}
	 *
	 * @param sourceProvider provider used as source of regions
	 * @param maxCacheSize The maximum number of cached region files
	 */
	public CachedRegionProvider(IRegionProvider<R, L> sourceProvider, int maxCacheSize) {
		this.sourceProvider = sourceProvider;
		this.regionLocationToRegion = new HashMap<>(maxCacheSize*2); // methods below are synchronized anyway, no need for concurrent map
		this.maxCacheSize = maxCacheSize;
	}

	@Override
	public synchronized IRegion<R, L> getRegion(R regionKey) throws IOException {
		if (closed) {
			throw new IllegalStateException("Already closed");
		}
		return getRegion(regionKey, true); // it can't be null here
	}

	@Override
	public synchronized Optional<IRegion<R, L>> getRegionIfExists(R regionKey) throws IOException {
		if (closed) {
			throw new IllegalStateException("Already closed");
		}
		return Optional.ofNullable(getRegion(regionKey, false));
	}

	@Override public void returnRegion(R key) {
		// no-op
	}

	@Override public Iterator<R> allRegions() throws IOException {
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

	private synchronized IRegion<R, L> getRegion(R location, boolean canCreate) throws IOException {
		if (regionLocationToRegion.size() > maxCacheSize) {
			this.clearRegions();
		}

		IRegion<R, L> region = regionLocationToRegion.get(location);
		if (region == null) {
			region = canCreate ? sourceProvider.getRegion(location) : sourceProvider.getRegionIfExists(location).orElse(null);
			if (region != null) {
				regionLocationToRegion.put(location, region);
			}
		}
		return region;
	}

	private void clearRegions() throws IOException {
		Iterator<R> it = regionLocationToRegion.keySet().iterator();
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
	public static <R extends IRegionKey<R, L>, L extends IKey<R, L>> CachedRegionProvider<R, L> makeProvider(
		Path directory, Function<String, R> nameToRegionKey) {
		return makeProvider(directory, nameToRegionKey, 512, 128);
	}

	/**
	 * Creates a RegionProvider with custom sector size and custom max cache size
	 * using the given {@code directory}
	 *
	 * @param directory The directory that region files are stored in
	 * @param sectorSize The sector size used in the region files
	 * @param maxSize The maximum number of cached region files
	 */
	public static <R extends IRegionKey<R, L>, L extends IKey<R, L>> CachedRegionProvider<R, L> makeProvider(
		Path directory, Function<String, R> nameToRegionKey, int sectorSize, int maxSize) {
		return new CachedRegionProvider<R, L>(SimpleRegionProvider.createDefault(directory, nameToRegionKey, sectorSize), maxSize);
	}
}
