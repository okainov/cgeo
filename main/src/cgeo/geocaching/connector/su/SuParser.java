package cgeo.geocaching.connector.su;

import cgeo.geocaching.connector.UserInfo;
import cgeo.geocaching.enumerations.CacheSize;
import cgeo.geocaching.enumerations.CacheType;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.enumerations.LoadFlags.SaveFlag;
import cgeo.geocaching.enumerations.WaypointType;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.log.LogEntry;
import cgeo.geocaching.log.LogType;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.models.Image;
import cgeo.geocaching.models.Waypoint;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.utils.SynchronizedDateFormat;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SuParser {

    private static final SynchronizedDateFormat DATE_FORMAT = new SynchronizedDateFormat("yyyy-MM-dd", Locale.US);
    private static final SynchronizedDateFormat DATE_TIME_FORMAT = new SynchronizedDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private static final String CACHE_CODE = "code";
    private static final String CACHE_ID = "id";
    private static final String CACHE_NAME = "name";
    private static final String CACHE_LAT = "latitude";
    private static final String CACHE_LON = "longitude";
    private static final String CACHE_TYPE = "type";
    private static final String CACHE_DIFFICULTY = "difficulty";
    private static final String CACHE_TERRAIN = "area";
    private static final String CACHE_SIZE = "size";
    private static final String CACHE_IS_FOUND = "isFound";
    private static final String CACHE_IS_WATCHED = "is_watched";
    private static final String CACHE_FOUND_ON = "foundOn";
    private static final String CACHE_HIDDEN = "dateHidden";
    private static final String CACHE_STATUS = "status";
    private static final String CACHE_DISABLED_STATUS = "status2";

    private static final String CACHE_AUTHOR = "author";
    private static final String USER_USERNAME = "name";
    private static final String CACHE_AUTHOR_ID = "id";

    private static final String CACHE_DESC = "description";
    private static final String CACHE_DESC_AREA = "area";
    private static final String CACHE_DESC_VIRTUAL = "virtualPart";
    private static final String CACHE_DESC_TRADITIONAL = "traditionalPart";
    private static final String CACHE_DESC_CONTAINS = "container";
    private static final String CACHE_DESC_CACHE = "cache";

    private static final String CACHE_NOTFOUNDS = "notfounds";
    private static final String CACHE_FOUNDS = "founds";

    private static final String CACHE_LATEST_LOGS = "logs";
    private static final String LOG_TYPE = "type";
    private static final String LOG_COMMENT = "text";
    private static final String LOG_DATE = "date";
    private static final String LOG_USER = "author";
    private static final String LOG_OWN = "own";

    private static final String CACHE_WPTS = "waypoints";
    private static final String WPT_LAT = "lat";
    private static final String WPT_LON = "lon";
    private static final String WPT_DESCRIPTION = "text";
    private static final String WPT_TYPE = "type";
    private static final String WPT_NAME = "name";

    private static final String CACHE_RATING = "rating";
    private static final String CACHE_VOTES = "votes";

    private static final String CACHE_RECOMMENDATIONS = "recommendations";
    private static final String CACHE_IMAGES = "images";
    private static final String CACHE_IMAGE_URL = "url";
    private static final String CACHE_IMAGE_CAPTION = "description";
    private static final String CACHE_IMAGE_TYPE = "type";

    private static final String USER_NAME = "name";
    private static final String USER_FOUNDS = "foundCaches";

    private SuParser() {
        // utility class
    }

    @NonNull
    public static UserInfo parseUser(final ObjectNode response) {
        final JsonNode data = response.get("data");

        final int finds = data.get(USER_FOUNDS).asInt();
        final String name = data.get(USER_NAME).asText();

        return new UserInfo(name, finds, UserInfo.UserInfoStatus.SUCCESSFUL);

    }

    @NonNull
    public static Geocache parseCache(final ObjectNode response) {
        final Geocache cache = new Geocache();
        cache.setReliableLatLon(true);
        final JsonNode data = response.get("data");

        parseCoreCache((ObjectNode) data, cache);

        final StringBuilder descriptionBuilder = new StringBuilder();

        parseDescription(descriptionBuilder, (ObjectNode) data.get(CACHE_DESC));

        final Map<LogType, Integer> logCounts = cache.getLogCounts();
        logCounts.put(LogType.FOUND_IT, data.get(CACHE_FOUNDS).asInt());
        logCounts.put(LogType.DIDNT_FIND_IT, data.get(CACHE_NOTFOUNDS).asInt());

        cache.setFavoritePoints(data.get(CACHE_RECOMMENDATIONS).asInt());

        cache.setRating((float) data.get(CACHE_RATING).asDouble());
        cache.setVotes(data.get(CACHE_VOTES).asInt());


        final ArrayNode images = (ArrayNode) data.get(CACHE_IMAGES);
        if (images != null) {
            for (final JsonNode imageResponse : images) {
                String title = "";
                if (imageResponse.has(CACHE_IMAGE_CAPTION)) {
                    title = imageResponse.get(CACHE_IMAGE_CAPTION).asText();
                }
                final String type = imageResponse.get(CACHE_IMAGE_TYPE).asText();
                final String url = imageResponse.get(CACHE_IMAGE_URL).asText();
                if (type.contains("area")) {
                    descriptionBuilder.append("<img src=\"" + url + "\"/><br/>");
                } else if (type.contains("cache")) {
                    title = "Spoiler";
                }

                // No idea why all images are called "spoiler" here, just need to make them
                // available at "Images" tab
                cache.addSpoiler(new Image.Builder().setUrl(url).setTitle(title).build());
            }
        }

        if (data.has(CACHE_WPTS)) {
            cache.setWaypoints(parseWaypoints((ArrayNode) data.path(CACHE_WPTS)), false);
        }

        // TODO: Maybe put smth in Hint?
        // cache.setHint(response.get(CACHE_HINT).asText());

        // TODO: Attributes?
        // cache.setAttributes(parseAttributes((ArrayNode) response.path(CACHE_ATTRNAMES), (ArrayNode) response.get(CACHE_ATTR_ACODES)));

        // TODO: Geokrety?
        // cache.mergeInventory(parseTrackables((ArrayNode) response.path(CACHE_TRACKABLES)), EnumSet.of(TrackableBrand.GEOKRETY));

        if (data.has(CACHE_IS_WATCHED)) {
            cache.setOnWatchlist(data.get(CACHE_IS_WATCHED).asBoolean());
        }

        // TODO: Uploading personal notes?
        // if (response.hasNonNull(CACHE_MY_NOTES)) {
        // cache.setPersonalNote(response.get(CACHE_MY_NOTES).asText());
        // }

        cache.setDescription(descriptionBuilder.toString());
        cache.setDetailedUpdatedNow();
        // save full detailed caches
        DataStore.saveCache(cache, EnumSet.of(LoadFlags.SaveFlag.DB));
        if (data.has(CACHE_LATEST_LOGS)) {
            DataStore.saveLogs(cache.getGeocode(), parseLogs((ArrayNode) data.path(CACHE_LATEST_LOGS)));
        }
        return cache;
    }

    @Nullable
    private static List<Waypoint> parseWaypoints(final ArrayNode wptsJson) {
        List<Waypoint> result = null;
        for (final JsonNode wptResponse : wptsJson) {
            final Waypoint wpt = new Waypoint(wptResponse.get(WPT_NAME).asText(),
                    parseWaypointType(wptResponse.get(WPT_TYPE).asText()),
                    false);
            wpt.setNote(wptResponse.get(WPT_DESCRIPTION).asText());
            final Geopoint pt = new Geopoint(wptResponse.get(WPT_LAT).asDouble(), wptResponse.get(WPT_LON).asDouble());
            wpt.setCoords(pt);
            if (result == null) {
                result = new ArrayList<>();
            }
            wpt.setPrefix(wpt.getName());
            result.add(wpt);
        }
        return result;
    }

    private static void parseDescription(final StringBuilder descriptionBuilder, final ObjectNode descriptionJson) {
        if (descriptionJson.has(CACHE_DESC_AREA)) {
            descriptionBuilder.append(descriptionJson.get(CACHE_DESC_AREA).asText());
        }
        if (descriptionJson.has(CACHE_DESC_CACHE)) {
            descriptionBuilder.append(descriptionJson.get(CACHE_DESC_CACHE).asText());
        }
        if (descriptionJson.has(CACHE_DESC_CONTAINS)) {
            descriptionBuilder.append(descriptionJson.get(CACHE_DESC_CONTAINS).asText());
        }
        if (descriptionJson.has(CACHE_DESC_VIRTUAL)) {
            descriptionBuilder.append(descriptionJson.get(CACHE_DESC_VIRTUAL).asText());
        }
        if (descriptionJson.has(CACHE_DESC_TRADITIONAL)) {
            descriptionBuilder.append(descriptionJson.get(CACHE_DESC_TRADITIONAL).asText());
        }
    }

    @NonNull
    private static List<LogEntry> parseLogs(final ArrayNode logsJSON) {
        final List<LogEntry> result = new LinkedList<>();
        for (final JsonNode logResponse : logsJSON) {
            final Date date = parseDateTime(logResponse.get(LOG_DATE).asText());
            if (date == null) {
                continue;
            }

            final boolean isOwnLog = logResponse.has(LOG_OWN) && logResponse.get(LOG_OWN).asInt() == 1;

            final LogEntry log = new LogEntry.Builder()
                    .setAuthor(parseUser(logResponse.get(LOG_USER)))
                    .setDate(date.getTime())
                    .setLogType(parseLogType(logResponse.get(LOG_TYPE).asText()))
                    .setLog(logResponse.get(LOG_COMMENT).asText().trim())
                    .setFriend(isOwnLog)
                    .build();
            result.add(log);
        }
        return result;
    }

    private static String parseUser(final JsonNode user) {
        return user.get(USER_USERNAME).asText();
    }

    /**
     * Parses cache received by map query request. So the response contains only "core" data
     * (i.e. without logs, waypoints etc.)
     *
     * @param response JSON response from API
     * @return parsed {@link Geocache}
     */
    @NonNull
    private static Geocache parseSmallCache(final ObjectNode response) {
        final Geocache cache = new Geocache();
        cache.setReliableLatLon(true);
        parseCoreCache(response, cache);
        DataStore.saveCache(cache, EnumSet.of(SaveFlag.CACHE));
        return cache;
    }

    private static void parseCoreCache(final ObjectNode data, @NonNull final Geocache cache) {
        cache.setCacheId(data.get(CACHE_ID).asText());
        cache.setName(data.get(CACHE_NAME).asText());

        cache.setType(parseType(data.get(CACHE_TYPE).asInt()));
        cache.setGeocode(data.get(CACHE_CODE).asText());
        cache.setHidden(parseDate(data.get(CACHE_HIDDEN).asText()));

        final double latitude = data.get(CACHE_LAT).asDouble();
        final double longitude = data.get(CACHE_LON).asDouble();
        cache.setCoords(new Geopoint(latitude, longitude));

        cache.setDisabled(isDisabledStatus(data.get(CACHE_DISABLED_STATUS).asText()));
        cache.setArchived(isArchivedStatus(data.get(CACHE_STATUS).asText()));

        final JsonNode author = data.get(CACHE_AUTHOR);
        cache.setOwnerDisplayName(parseUser(author));
        cache.setOwnerUserId(author.get(CACHE_AUTHOR_ID).asText());

        cache.setSize(parseSize(data.get(CACHE_SIZE).asText()));

        cache.setDifficulty((float) data.get(CACHE_DIFFICULTY).asDouble());
        cache.setTerrain((float) data.get(CACHE_TERRAIN).asDouble());

        if (data.has(CACHE_IS_FOUND)) {
            cache.setFound(data.get(CACHE_IS_FOUND).asBoolean());

            if (cache.isFound()) {
                cache.setVisitedDate(parseDate(data.get(CACHE_FOUND_ON).asText()).getTime());
            }
        }
    }

    @NonNull
    public static List<Geocache> parseCaches(final ObjectNode response) {
        // Check for empty result
        final JsonNode results = response.path("data");
        if (results.size() == 0) {
            return Collections.emptyList();
        }

        // Get and iterate result list
        final List<Geocache> caches = new ArrayList<>(results.size());
        for (final JsonNode cache : results) {
            caches.add(parseSmallCache((ObjectNode) cache));
        }
        return caches;
    }

    private static LogType parseLogType(final String status) {
        switch (status) {
            case "1":
                return LogType.FOUND_IT;
            case "2":
                return LogType.DIDNT_FIND_IT;
            case "3":
                return LogType.NOTE;
            case "4":
                return LogType.DIDNT_FIND_IT;
            case "5":
                return LogType.OWNER_MAINTENANCE;
            case "6":
                return LogType.OWNER_MAINTENANCE;
            default:
                return LogType.UNKNOWN;
        }
    }

    private static CacheSize parseSize(final String size) {
        switch (size) {
            case "1":
                return CacheSize.UNKNOWN;
            case "2":
                return CacheSize.MICRO;
            case "3":
                return CacheSize.SMALL;
            case "4":
                return CacheSize.REGULAR;
            case "5":
                return CacheSize.OTHER;
            default:
                return CacheSize.UNKNOWN;
        }
    }

    private static WaypointType parseWaypointType(final String wpType) {
        switch (wpType) {
            case "1":
                return WaypointType.PARKING;
            case "2":
                return WaypointType.STAGE;
            case "3":
                return WaypointType.PUZZLE;
            case "4":
                return WaypointType.TRAILHEAD;
            case "5":
                return WaypointType.FINAL;
            case "6":
                return WaypointType.WAYPOINT;
            default:
                return WaypointType.WAYPOINT;
        }
    }

    private static boolean isDisabledStatus(final String status2) {
        // Possible values:
        // 1 - normal active
        // 2 - "doubtful"
        // 3 - "inactive", i.e. missing container
        // TODO: Maybe it makes some sense to distinguish between real "inactive" and "doubtful"?
        return !("1".equals(status2));
    }

    private static boolean isArchivedStatus(final String status) {
        // Possible values:
        // 1 - normal active
        // 2 to 7 - different options of "Archived". API should not send such caches in response,
        // so all caches here should be "normal"
        return !("1".equals(status));
    }


    @NonNull
    private static CacheType parseType(final int type) {
        switch (type) {
            case 1:
                return CacheType.TRADITIONAL;
            case 2:
                return CacheType.MULTI;
            case 3:
                return CacheType.VIRTUAL;
            case 4:
                return CacheType.EVENT;
            case 5:
                // Not used
                return CacheType.WEBCAM;
            case 7:
                // Virtual Multi-step
                return CacheType.VIRTUAL;
            case 8:
                // Contest
                return CacheType.EVENT;
            case 9:
                return CacheType.MYSTERY;
            case 10:
                // Mystery Virtual
                return CacheType.MYSTERY;
            case 6:
                // "Extreme cache", not used.
            default:
                return CacheType.UNKNOWN;
        }
    }

    private static Date parseDate(final String text) {
        try {
            return DATE_FORMAT.parse(text);
        } catch (final ParseException e) {
            return new Date("");
        }
    }

    private static Date parseDateTime(final String text) {
        try {
            return DATE_TIME_FORMAT.parse(text);
        } catch (final ParseException e) {
            return new Date("");
        }
    }
}
