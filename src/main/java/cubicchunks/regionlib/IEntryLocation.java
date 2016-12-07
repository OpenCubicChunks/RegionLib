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
 * A key for locating a value.<br/>
 * This class should be immutable.
 *
 * @param <R> The IRegionLocation type associated this IEntryLocation
 * @param <L> This type
 */
public interface IEntryLocation<R extends IRegionLocation<R, L>, L extends IEntryLocation<R, L>> {
	/**
	 * Gets a region key associated with the region that contains this key's value
	 *
	 * @return A IRegionLocation of a region that contains this key's value
	 */
	R getRegionLocation();

	/**
	 * Gets the index of this key in the region associated with
	 * {@link IEntryLocation#getRegionLocation()}'s region.<br/>
	 *
	 * The index must be grater than or equal to 0 AND less than
	 * {@link IEntryLocation#getRegionLocation()}'s {@link IRegionLocation#getEntryCount()}.<br/>
	 *
	 * The index must not overlap with another key's index within the region<br/>
	 *
	 * @return The index
	 */
	int getId();
}
