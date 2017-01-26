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
import java.util.Iterator;
import java.util.Optional;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.region.IRegion;
import cubicchunks.regionlib.region.Region;
import cubicchunks.regionlib.util.CheckedConsumer;
import cubicchunks.regionlib.util.CheckedFunction;

/**
 * A simple implementation of IRegionProvider, this is intended to be used together with CachedRegionProvider or other
 * caching implementation
 *
 * @param <K> The key type
 */
public class SimpleRegionProvider<K extends IKey<K>> implements IRegionProvider<K> {

	private Path directory;
	private RegionFactory<K> regionBuilder;

	public SimpleRegionProvider(Path directory, RegionFactory<K> regionBuilder) {
		this.directory = directory;
		this.regionBuilder = regionBuilder;
	}

	@Override
	public <R> Optional<R> fromExistingRegion(K key, CheckedFunction<? super IRegion<K>, R, IOException> func) throws IOException {
		IRegion<K> r = getExistingRegion(key).orElse(null);
		if (r != null) {
			R ret = func.apply(r);
			r.close();
			return Optional.of(ret);
		}
		return Optional.empty();
	}

	@Override
	public <R> R fromRegion(K key, CheckedFunction<? super IRegion<K>, R, IOException> func) throws IOException {
		IRegion<K> r = getRegion(key);
		R ret = func.apply(r);
		r.close();
		return ret;
	}

	@Override
	public void forRegion(K key, CheckedConsumer<? super IRegion<K>, IOException> consumer) throws IOException {
		IRegion<K> r = getRegion(key);
		consumer.accept(r);
		r.close();
	}

	@Override
	public void forExistingRegion(K key, CheckedConsumer<? super IRegion<K>, IOException> consumer) throws IOException {
		IRegion<K> r = getExistingRegion(key).orElse(null);
		if (r != null) {
			consumer.accept(r);
			r.close();
		}
	}

	@Override public IRegion<K> getRegion(K key) throws IOException {
		Path regionPath = directory.resolve(key.getRegionName());
		return regionBuilder.create(regionPath, key);
	}

	@Override public Optional<IRegion<K>> getExistingRegion(K key) throws IOException {
		Path regionPath = directory.resolve(key.getRegionName());
		if (!Files.exists(regionPath)) {
			return Optional.empty();
		}
		IRegion<K> reg = regionBuilder.create(regionPath, key);
		return Optional.of(reg);
	}

	@Override public void forAllRegions(CheckedConsumer<? super String, IOException> consumer) throws IOException {
		Iterator<String> it = allRegions();
		while (it.hasNext()) {
			consumer.accept(it.next());
		}
	}

	protected Iterator<String> allRegions() throws IOException {
		return Files.list(directory)
			.map(Path::getFileName)
			.map(Path::toString)
			.iterator();
	}

	@Override public void close() {
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
