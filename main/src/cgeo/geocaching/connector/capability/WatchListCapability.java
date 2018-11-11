package cgeo.geocaching.connector.capability;

import cgeo.geocaching.connector.IConnector;
import cgeo.geocaching.models.Geocache;

import android.support.annotation.NonNull;

/**
 * Connector interface to implement for adding/removing caches to/from a watch list (which is hosted at the connectors
 * site).
 */
public interface WatchListCapability extends IConnector {

    /**
     * Restrict the caches or circumstances when to add a cache to the watchlist.
     */
    boolean canAddToWatchList(@NonNull final Geocache cache);

    /**
     * Add the cache to the watchlist
     *
     * @return True - success/False - failure
     */
    boolean addToWatchlist(@NonNull final Geocache cache);

    /**
     * Remove the cache from the watchlist
     *
     * @return True - success/False - failure
     */
    boolean removeFromWatchlist(@NonNull final Geocache cache);

}
