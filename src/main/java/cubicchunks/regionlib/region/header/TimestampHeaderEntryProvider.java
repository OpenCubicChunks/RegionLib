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
package cubicchunks.regionlib.region.header;

import java.util.concurrent.TimeUnit;

import cubicchunks.regionlib.IKey;
import cubicchunks.regionlib.IRegionKey;

public class TimestampHeaderEntryProvider<R extends IRegionKey<R, L>, L extends IKey<R, L>>
	implements IHeaderDataEntryProvider<IntHeaderEntry, R, L> {

	private final TimeUnit timeUnit;

	public TimestampHeaderEntryProvider(TimeUnit timeUnit) {

		this.timeUnit = timeUnit;
	}

	@Override public int getEntryByteCount() {
		return Integer.BYTES;
	}

	@Override public IntHeaderEntry apply(L o) {
		return new IntHeaderEntry((int) TimeUnit.MILLISECONDS.convert(System.currentTimeMillis(), timeUnit));
	}
}
