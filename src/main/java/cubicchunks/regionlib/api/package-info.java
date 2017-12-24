/**
 * The standard public API. Aside of instantiating the required objects, this is what should be used.
 * Reference implementations together with some extra utilities can be found in impl package.
 *
 * A summary of the API (all of that can also be found in documentation of these classes, this place summarizes the information in one place):
 * <ul>
 *     <li>{@link cubicchunks.regionlib.api.region.key.IKey}: Specifies a logical location of a chunk of data (ByteBuffer), and it's implementation
 *     maps the logical location to the region it's stored in (region name), and logical location inside the region (ID)</li>
 *     <li>{@link cubicchunks.regionlib.api.region.key.IKeyProvider}: Provides metadata about keys and allows creating keys by region name.</li>
 *     <li>{@link cubicchunks.regionlib.api.region.IRegion}: The low level storage for data, represents it's logical organization. Different
 *     regions may not necessarily correspong to different files, and a region may be backed by more than one file. A region can store a constant,
 *     known amount of entries, specified by {@link cubicchunks.regionlib.api.region.key.IKeyProvider}</li>
 *     <li>{@link cubicchunks.regionlib.api.region.IRegionProvider}: Provides a by-key access to Regions, also allows to get access to all existing
 *     regions.</li>
 *     <li>{@link cubicchunks.regionlib.api.storage.SaveSection}: A high level abstraction over the low level storage API.
 *     Allows to use fallback region providers for reading and writing, allowing for backwards compatibility when data organization gets changed,
 *     or allows to use different storage method for entries not supported by the default IRegion implementation (standard Region implementetion
 *     has an upper limit for size)</li>
 * </ul>
 *
 * The API also allows to store additional, small, constant-size per-entry metadata inside region header. Region implementations may provide their
 * own required header data, and must accept and store any user-defined data:
 * <ul>
 *     <li>{@link cubicchunks.regionlib.api.region.header.IHeaderDataEntry} wrtites the data to a buffer</li>
 *     <li>{@link cubicchunks.regionlib.api.region.header.IHeaderDataEntryProvider} creates new HeaderDataEntries by
 *     {@link cubicchunks.regionlib.api.region.key.IKey}</li>
 * </ul>
 * The header entry data is intended to be small as implementations may reserve space for it before it's written.
 */
package cubicchunks.regionlib.api;