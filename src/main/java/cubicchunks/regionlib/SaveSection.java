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
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Function;

import cubicchunks.regionlib.region.IRegion;
import cubicchunks.regionlib.region.provider.CachedRegionProvider;
import cubicchunks.regionlib.region.provider.IRegionProvider;

/**
 * A low level key/value database optimized for storing
 * keys that are close together/clumped<br/>
 * Example: Key could be an integer location in n-dimensional space, like Minecraft chunk locations
 *
 * @param <R> The region key type
 * @param <L> The location key type
 */
public class SaveSection<R extends IRegionKey<R, L>, L extends IKey<R, L>> implements Closeable {

	private final IRegionProvider<R, L> regionProvider;

	/**
	 * Creates a SaveSection with a customized IRegionProvider
	 *
	 * @param regionProvider The region provider
	 */
	public SaveSection(IRegionProvider<R, L> regionProvider) {
		this.regionProvider = regionProvider;
	}

	/**
	 * Saves/puts a value at a key<br/>
	 * This method is thread safe.
	 *
	 * @param key The key
	 * @param value The value to save
	 */
	public void save(L key, ByteBuffer value) throws IOException {
		this.regionProvider.getRegion(key.getRegionKey()).writeValue(key, value);
		this.regionProvider.returnRegion(key.getRegionKey());
	}

	/**
	 * Loads/gets a value at a key<br/>
	 * This Method is thread safe.
	 *
	 * @param key The key
	 *
	 * @return An Optional containing the value if it exists
	 */
	public Optional<ByteBuffer> load(L key) throws IOException {
		Optional<IRegion<R, L>> region = this.regionProvider.getRegionIfExists(key.getRegionKey());
		if (region.isPresent()) {
			Optional<ByteBuffer> ret = region.get().readValue(key);
			this.regionProvider.returnRegion(key.getRegionKey());
			return ret;
		}
		return Optional.empty();
	}

	/**
	 * Returns iterator with all currently existing regions. Regions created after this method is called are not
	 * guaranteed to be listed.
	 */
	public Iterator<R> allRegions() throws IOException {
		return this.regionProvider.allRegions();
	}

	@Override
	public void close() throws IOException {
		this.regionProvider.close();
	}

	/**
	 * Creates a SaveSection that stores its data in {@code directory}
	 *
	 * @param directory The place to store the region files
	 */
	public static <R extends IRegionKey<R, L>, L extends IKey<R, L>> SaveSection<R, L> createDefaultAt(Path directory, Function<String, R> nameToRegionKey) {
		return new SaveSection(CachedRegionProvider.makeProvider(directory, nameToRegionKey));
	}
}
