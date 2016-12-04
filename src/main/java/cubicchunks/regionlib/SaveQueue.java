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

import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import cubicchunks.regionlib.region.IRegionLocation;

public class SaveQueue<R extends IRegionLocation<R, L>, L extends IEntryLocation<R, L>> {
	private final Deque<Entry<R, L>> queuedToWrite;
	private final Map<L, Entry<R, L>> queuedEntryMap;

	public SaveQueue() {
		this.queuedToWrite = new ConcurrentLinkedDeque<>();
		this.queuedEntryMap = new ConcurrentHashMap<>();
	}

	public void add(Entry<R, L> entry) {
		this.queuedToWrite.add(entry);
		this.queuedEntryMap.put(entry.getLocation(), entry);
	}

	public Optional<Entry<R, L>> getQueuedFor(L location) {
		return Optional.ofNullable(queuedEntryMap.get(location));
	}

	public Optional<Entry<R, L>> nextToSave() {
		return Optional.ofNullable(queuedToWrite.poll());
	}

	public void onIOSaved(Entry<R, L> entry) {
		this.queuedEntryMap.remove(entry.getLocation());
	}

	public boolean hasNextToSave() {
		return !this.queuedToWrite.isEmpty();
	}
}
