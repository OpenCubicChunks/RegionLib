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
package cubicchunks.regionlib.api.region.key;

/**
 * Specifies a logical location of a chunk of data (ByteBuffer), and maps the logical location to the region it's stored in ({@link RegionKey}),
 * and logical location inside the region (ID).
 * This class should be immutable.
 *
 * @param <K> This type
 */
public interface IKey<K extends IKey<K>> {
	/**
	 * Gets the index of this key in the region associated with
	 * this region location
	 *
	 * The index must be grater than or equal to 0 AND less than
	 * {@link IKeyProvider#getKeyCount(RegionKey)}.
	 *
	 * The index must be unique within one region. Specifically, for any 2 IKey objects of the same type,
	 * {@code k1.getRegionKey().equals(k2.getRegionKey()) && k1.getId() != k2.getId() } must always be true.
	 *
	 * @return The index
	 */
	int getId();

	/**
	 * Gets the region's key.
	 * The name may be used as a file name, so names not matching {@code ^[a-z0-9\._\-]+$} are not supported. Uppercase characters are not allowed
	 * to avoid case-sensitivity issues across different operating systems.
	 *
	 * @return This region's key
	 */
	RegionKey getRegionKey();
}
