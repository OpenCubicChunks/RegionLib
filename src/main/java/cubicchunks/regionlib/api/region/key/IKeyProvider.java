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
    IKey<K> fromRegionAndId(RegionKey regionKey, int id) throws IllegalArgumentException;

    /**
     * Gets the maximum number of keys within the region.
     * The number must be constant within one region (where the same value for getRegionKey is returned)
     *
     * @param key the region key
     * @return The number of keys in this region
     */
    int getKeyCount(RegionKey key);
}
