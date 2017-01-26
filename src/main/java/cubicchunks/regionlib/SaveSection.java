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
package cubicchunks.regionlib;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

import cubicchunks.regionlib.region.provider.IRegionProvider;
import cubicchunks.regionlib.util.CheckedConsumer;

/**
 * A low level key/value database optimized for storing
 * keys that are close together/clumped<br/>
 * Example: Key could be an integer location in n-dimensional space, like Minecraft chunk locations
 *
 * @param <S> This type
 * @param <K> The location key type
 */
public abstract class SaveSection<S extends SaveSection<S, K>, K extends IKey<K>> implements Closeable {

	private final IRegionProvider<K> regionProvider;

	/**
	 * Creates a SaveSection with a customized IRegionProvider
	 *
	 * @param regionProvider The region provider
	 */
	public SaveSection(IRegionProvider<K> regionProvider) {
		this.regionProvider = regionProvider;
	}

	/**
	 * Saves/puts a value at a key<br/>
	 * This method is thread safe.
	 *
	 * @param key The key
	 * @param value The value to save
	 */
	public void save(K key, ByteBuffer value) throws IOException {
		this.regionProvider.forRegion(key, r -> r.writeValue(key, value));
	}

	/**
	 * Loads/gets a value at a key<br/>
	 * This Method is thread safe.
	 *
	 * @param key The key
	 *
	 * @return An Optional containing the value if it exists
	 */
	public Optional<ByteBuffer> load(K key) throws IOException {
		return this.regionProvider.fromExistingRegion(key,
			r -> r.readValue(key)
		).flatMap(x -> x);
	}

	/**
	 * Returns iterator with all currently existing regions. Regions created after this method is called are not
	 * guaranteed to be listed.
	 */
	public void forAllRegions(CheckedConsumer<? super String, IOException> cons) throws IOException {
		this.regionProvider.forAllRegions(cons);
	}

	public boolean hasEntry(K key) throws IOException {
		return this.regionProvider.fromExistingRegion(key, r -> r.hasValue(key)).orElse(false);
	}

	@Override
	public void close() throws IOException {
		this.regionProvider.close();
	}
}
