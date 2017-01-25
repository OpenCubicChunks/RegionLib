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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.region.IRegion;
import cubicchunks.regionlib.region.Region;

/**
 * A simple implementation of IRegionProvider, this is intended to be used together with CachedRegionProvider or other
 * caching implementation
 *
 * @param <K> The key type
 */
public class SimpleRegionProvider<K extends IKey<K>> implements IRegionProvider<K> {

	private Path directory;
	private RegionFactory<K> regionBuilder;
	private Map<String, IRegion<K>> toReturn;

	public SimpleRegionProvider(Path directory, RegionFactory<K> regionBuilder) {
		this.directory = directory;
		this.regionBuilder = regionBuilder;
		this.toReturn = new HashMap<>();
	}

	@Override public IRegion<K> getRegion(K key) throws IOException {
		Path regionPath = directory.resolve(key.getRegionName());

		IRegion<K> reg = regionBuilder.create(regionPath, key);

		this.toReturn.put(key.getRegionName(), reg);
		return reg;
	}

	@Override public Optional<IRegion<K>> getRegionIfExists(K key) throws IOException {
		Path regionPath = directory.resolve(key.getRegionName());
		if (!Files.exists(regionPath)) {
			return Optional.empty();
		}
		IRegion<K> reg = regionBuilder.create(regionPath, key);
		this.toReturn.put(key.getRegionName(), reg);
		return Optional.of(reg);
	}

	@Override public void returnRegion(String name) throws IOException {
		IRegion<?> reg = toReturn.remove(name);
		if (reg == null) {
			throw new IllegalArgumentException("No region found");
		}
		reg.close();
	}

	@Override public Iterator<String> allRegions() throws IOException {
		return Files.list(directory)
			.map(Path::getFileName)
			.map(Path::toString)
			.iterator();
	}

	@Override public void close() throws IOException {
		if (!toReturn.isEmpty()) {
			System.err.println("Warning: leaked " + toReturn.size() + " regions! Closing them now");
			for (IRegion<?> r : toReturn.values()) {
				r.close();
			}
			toReturn.clear();
			toReturn = null;
		}
	}

	public static <K extends IKey<K>> SimpleRegionProvider<K> createDefault(Path directory, int sectorSize) {
		return new SimpleRegionProvider<>(directory, (p, r) ->
			new Region.Builder<K>()
				.setPath(p)
				.setEntriesPerRegion(r.getKeyCount())
				.setSectorSize(sectorSize)
				.build()
		);
	}

	@FunctionalInterface
	public interface RegionFactory<K extends IKey<K>> {
		IRegion<K> create(Path path, K key) throws IOException;
	}
}
