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

import java.util.function.BiFunction;

/**
 * Provides metadata about keys and allows creating keys by region name.
 */
public interface IKeyProvider<K extends IKey<K>> {

    /**
     * Creates instance of {@code K} such that {@code key.getRegionKey().equals(regionKey) && key.getId() == id}, or throws
     * {@link IllegalArgumentException} when the region key and id pair doesn't represent any key of this type.
     *
     * @param regionKey the {@link RegionKey} for which an {@link IKey} should be created
     * @param id the ID for which an {@link IKey} should be created
     * @return the newly created key
     *
     * @throws IllegalArgumentException when the supplied regionKey and id pair doesn't correspond to a valid key of type {@code K}.
     */
    K fromRegionAndId(RegionKey regionKey, int id) throws IllegalArgumentException;

    /**
     * Gets the maximum number of keys within the region.
     * The number must be constant within one region (where the same value for getRegionKey is returned)
     *
     * @param key the region key
     * @return The number of keys in this region
     */
    int getKeyCount(RegionKey key);
}
