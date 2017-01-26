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
import cubicchunks.regionlib.util.CheckedConsumer;
import cubicchunks.regionlib.util.CheckedFunction;

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
	public <R> Optional<R> fromExistingRegion(K key, CheckedFunction<? super IRegion<K>, R, IOException> func) throws IOException {
		if (closed) {
			throw new IllegalStateException("Already closed");
		}
		return fromRegion(key, func, false);
	}

	@Override
	public <R> R fromRegion(K key, CheckedFunction<? super IRegion<K>, R, IOException> func) throws IOException {
		if (closed) {
			throw new IllegalStateException("Already closed");
		}
		return fromRegion(key, func, true).get();
	}

	@Override
	public synchronized void forRegion(K key, CheckedConsumer<? super IRegion<K>, IOException> cons) throws IOException {
		if (closed) {
			throw new IllegalStateException("Already closed");
		}
		forRegion(key, cons, true);
	}

	@Override
	public synchronized void forExistingRegion(K key, CheckedConsumer<? super IRegion<K>, IOException> cons) throws IOException {
		if (closed) {
			throw new IllegalStateException("Already closed");
		}
		forRegion(key, cons, false);
	}

	@Override public IRegion<K> getRegion(K key) throws IOException {
		IRegion<K> r = regionLocationToRegion.get(key.getRegionName());
		if (r != null) {
			regionLocationToRegion.remove(key.getRegionName());
			return r;
		} else {
			return sourceProvider.getRegion(key);
		}
	}

	@Override public Optional<IRegion<K>> getExistingRegion(K key) throws IOException {
		IRegion<K> r = regionLocationToRegion.get(key.getRegionName());
		if (r != null) {
			regionLocationToRegion.remove(key.getRegionName());
			return Optional.of(r);
		} else {
			return sourceProvider.getExistingRegion(key);
		}
	}

	@Override public void forAllRegions(CheckedConsumer<? super String, IOException> cons) throws IOException {
		if (closed) {
			throw new IllegalStateException("Already closed");
		}
		sourceProvider.forAllRegions(cons);
	}

	@Override public void close() throws IOException {
		if (closed) {
			throw new IllegalStateException("Already closed");
		}
		this.clearRegions();
		this.sourceProvider.close();
		this.closed = true;
	}

	private synchronized void forRegion(K location, CheckedConsumer<? super IRegion<K>, IOException> cons, boolean canCreate) throws IOException {
		if (regionLocationToRegion.size() > maxCacheSize) {
			this.clearRegions();
		}

		IRegion<K> region = regionLocationToRegion.get(location.getRegionName());
		if (region == null) {
			if (canCreate) {
				region = sourceProvider.getRegion(location);
			} else {
				region = sourceProvider.getExistingRegion(location).orElse(null);
			}
			if (region != null) {
				regionLocationToRegion.put(location.getRegionName(), region);
				cons.accept(region);
			}
		} else {
			cons.accept(region);
		}
	}

	private synchronized <R> Optional<R> fromRegion(K location, CheckedFunction<? super IRegion<K>, R, IOException> func, boolean canCreate) throws IOException {
		if (regionLocationToRegion.size() > maxCacheSize) {
			this.clearRegions();
		}

		IRegion<K> region = regionLocationToRegion.get(location.getRegionName());
		if (region == null) {
			if (canCreate) {
				region = sourceProvider.getRegion(location);
			} else {
				region = sourceProvider.getExistingRegion(location).orElse(null);
			}
			if (region != null) {
				regionLocationToRegion.put(location.getRegionName(), region);
				return Optional.of(func.apply(region));
			}
		}
		if (region == null) {
			return Optional.empty();
		}
		return Optional.of(func.apply(region));
	}

	private void clearRegions() throws IOException {
		Iterator<IRegion<K>> it = regionLocationToRegion.values().iterator();
		while (it.hasNext()) {
			it.next().close();
		}
		regionLocationToRegion.clear();
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
