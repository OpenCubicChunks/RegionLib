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
package cubicchunks.regionlib.lib.provider;

import cubicchunks.regionlib.api.region.IRegion;
import cubicchunks.regionlib.api.region.IRegionProvider;
import cubicchunks.regionlib.api.region.key.IKey;
import cubicchunks.regionlib.api.region.key.IKeyProvider;
import cubicchunks.regionlib.api.region.key.RegionKey;
import cubicchunks.regionlib.lib.Region;
import cubicchunks.regionlib.util.CheckedConsumer;
import cubicchunks.regionlib.util.CheckedFunction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A simple implementation of IRegionProvider, this is intended to be used together with CachedRegionProvider or other
 * caching implementation
 *
 * @param <K> The key type
 */
public class SimpleRegionProvider<K extends IKey<K>> implements IRegionProvider<K> {

	private final IKeyProvider<K> keyProvider;
	private final Path directory;
	private final RegionFactory<K> regionBuilder;

	public SimpleRegionProvider(IKeyProvider<K> keyProvider, Path directory, RegionFactory<K> regionBuilder) {
		this.keyProvider = keyProvider;
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
		return regionBuilder.create(keyProvider, key.getRegionKey());
	}

	@Override public Optional<IRegion<K>> getExistingRegion(K key) throws IOException {
		Path regionPath = directory.resolve(key.getRegionKey().getName());
		if (!Files.exists(regionPath)) {
			return Optional.empty();
		}
		IRegion<K> reg = regionBuilder.create(keyProvider, key.getRegionKey());
		return Optional.of(reg);
	}

	@Override public void forAllRegions(CheckedConsumer<? super IRegion<K>, IOException> consumer) throws IOException {
		try (Stream<Path> stream = Files.list(directory)) {
			Iterator<RegionKey> it = stream.map(Path::getFileName)
				.map(Path::toString)
				.map(RegionKey::new)
				.filter(keyProvider::isValid)
				.iterator();
			while (it.hasNext()) {
				RegionKey key = it.next();
				if (!keyProvider.isValid(key)) {
					continue;
				}
				consumer.accept(regionBuilder.create(keyProvider, key));
			}
		}
	}

	@Override public void close() {
	}

	public static <K extends IKey<K>> SimpleRegionProvider<K> createDefault(IKeyProvider<K> keyProvider, Path directory, int sectorSize) {
		return new SimpleRegionProvider<>(keyProvider, directory, (keyProv, r) ->
			new Region.Builder<K>()
					.setDirectory(directory)
					.setRegionKey(r)
					.setKeyProvider(keyProv)
					.setSectorSize(sectorSize)
					.build()
		);
	}

	@FunctionalInterface
	public interface RegionFactory<K extends IKey<K>> {
		IRegion<K> create(IKeyProvider<K> keyProvider, RegionKey key) throws IOException;
	}
}
