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

/**
 * A key for locating a region.<br/>
 * This class should be immutable.
 *
 * @param <R> This type
 * @param <L> The key type used to locate values within this region
 */
public interface IRegionKey<R extends IRegionKey<R, L>, L extends IKey<R, L>> {
	/**
	 * Gets the region's name.<br/>
	 * The name must be unique per region key.<br/>
	 * The name will usually be used as a file name so don't use any special characters.
	 *
	 * @return This region's name
	 */
	String getRegionName();

	/**
	 * Gets the maximum number of keys within this region.<br/>
	 * (yes, different regions *can* have different a different number of keys,
	 * but don't do this unless you KNOW what you are doing)
	 *
	 * @return The number of keys in this region
	 */
	int getKeyCount();
}
