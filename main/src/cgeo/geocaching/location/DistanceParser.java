package cgeo.geocaching.location;

import cgeo.geocaching.utils.MatcherWrapper;

import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

public final class DistanceParser {

    private static final Pattern pattern = Pattern.compile("^([0-9.,]+)[ ]*(m|km|ft|yd|mi|)?$", Pattern.CASE_INSENSITIVE);

    public enum UNIT {
        M(0), KM(1), FT(2), YD(3), MI(4);
        private int value;

        UNIT(int value) {
            this.value = value;
        }

        public static UNIT getById(int id) {
            for (final UNIT e : values()) {
                if (e.value == id)
                    return e;
            }
            return MI;
        }
    }

    private DistanceParser() {
        // utility class
    }

    /**
     * Parse a distance string composed by a number and an optional suffix
     * (such as "1.2km").
     *
     * @param distanceText the string to analyze
     * @param metricUnit   if false AND no unit is present in the string, the units will be considered
     *                     to be FT, meters otherwise
     * @return the distance in kilometers
     * @throws NumberFormatException if the given number is invalid
     */
    public static float parseDistance(final String distanceText, final boolean metricUnit)
            throws NumberFormatException {
        final MatcherWrapper matcher = new MatcherWrapper(pattern, distanceText);

        if (!matcher.find()) {
            throw new NumberFormatException(distanceText);
        }

        final float value = Float.parseFloat(matcher.group(1).replace(',', '.'));
        final String unitStr = StringUtils.lowerCase(matcher.group(2), Locale.US);

        UNIT unit = metricUnit ? UNIT.M : UNIT.FT;

        switch (unitStr) {
            case "m":
                unit = UNIT.M;
                break;
            case "km":
                unit = UNIT.KM;
                break;
            case "yd":
                unit = UNIT.YD;
                break;
            case "mi":
                unit = UNIT.MI;
                break;
            case "ft":
                unit = UNIT.FT;
                break;
        }
        return convertDistance(value, unit);
    }

    /**
     * Converts distance from different units to kilometers
     *
     * @param distance source distance to convert
     * @param unit   unit to convert from
     * @return the distance in kilometers
     */
    public static float convertDistance(final float distance, final UNIT unit)
            throws NumberFormatException {
        switch (unit) {
            case M:
                return distance / 1000;
            case FT:
                return distance * IConversion.FEET_TO_KILOMETER;
            case MI:
                return distance * IConversion.MILES_TO_KILOMETER;
            case YD:
                return distance * IConversion.YARDS_TO_KILOMETER;
            case KM:
            default:
                return distance;
        }
    }

}
