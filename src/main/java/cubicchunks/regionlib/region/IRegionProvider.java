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
package cubicchunks.regionlib.region;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.IRegionKey;

/**
 * Acts as a source of regions (creation/loading/caching)
 *
 * @param <R> The region key type
 * @param <L> The key type
 */
public interface IRegionProvider<R extends IRegionKey<R, L>, L extends IKey<R, L>> extends Closeable {

	/**
	 * Gets an IRegion at a given region key, or create one if it does not exist
	 *
	 * @param regionKey The key for the IRegion
	 * @return The IRegion at {@code regionKey}
	 * @throws IOException
	 */
	IRegion<R, L> getRegion(R regionKey) throws IOException;

	/**
	 * Gets an IRegion at a given region key
	 *
	 * @param regionKey The key for the IRegion
	 * @return An Optional containing the IRegion at {@code regionKey} if it exists
	 * @throws IOException
	 */
	Optional<IRegion<R, L>> getRegionIfExists(R regionKey) throws IOException;
}
