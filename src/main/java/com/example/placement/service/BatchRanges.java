package com.example.placement.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses eligible-batch specifications such as "2022-2026" or "2022-2024, 2026-2028".
 *
 * <p>A specification is a comma-separated list of year ranges. Taking the minimum and maximum
 * year of the whole string would silently widen a multi-range placement (for example
 * "2022-2024, 2026-2028" would wrongly admit the 2025 batch), so ranges are always evaluated
 * individually.</p>
 */
public final class BatchRanges {

    private static final Pattern RANGE = Pattern.compile("(\\d{4})\\s*-\\s*(\\d{4})");
    private static final Pattern YEAR = Pattern.compile("\\d{4}");

    private BatchRanges() {
    }

    public record Range(int start, int end) {

        public boolean overlaps(Range other) {
            return other != null && start <= other.end && other.start <= end;
        }

        @Override
        public String toString() {
            return start + "-" + end;
        }
    }

    /**
     * Parses every range in the specification. Loose values such as "2022" become a single-year
     * range. Unparseable tokens are skipped, so an empty result means "no usable range".
     */
    public static List<Range> parse(String value) {
        List<Range> ranges = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return ranges;
        }
        for (String token : value.split(",")) {
            String part = token.trim();
            if (part.isEmpty()) {
                continue;
            }
            Matcher rangeMatcher = RANGE.matcher(part);
            if (rangeMatcher.find()) {
                ranges.add(toRange(rangeMatcher.group(1), rangeMatcher.group(2)));
                continue;
            }
            Matcher yearMatcher = YEAR.matcher(part);
            if (yearMatcher.find()) {
                int year = Integer.parseInt(yearMatcher.group());
                ranges.add(new Range(year, year));
            }
        }
        return ranges;
    }

    private static Range toRange(String startText, String endText) {
        int start = Integer.parseInt(startText);
        int end = Integer.parseInt(endText);
        return start <= end ? new Range(start, end) : new Range(end, start);
    }

    /**
     * Validates that every comma-separated token is an explicit "From Year-To Year" pair with
     * From Year not greater than To Year.
     */
    public static boolean isValidSpec(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        for (String token : value.split(",", -1)) {
            String part = token.trim();
            Matcher matcher = RANGE.matcher(part);
            if (part.isEmpty() || !matcher.matches()) {
                return false;
            }
            if (Integer.parseInt(matcher.group(1)) > Integer.parseInt(matcher.group(2))) {
                return false;
            }
        }
        return true;
    }

    /** Canonical comma-separated form, e.g. "2022-2024, 2026-2028". */
    public static String normalize(String value) {
        List<Range> ranges = parse(value);
        if (ranges.isEmpty()) {
            return "";
        }
        StringBuilder normalized = new StringBuilder();
        for (Range range : ranges) {
            if (normalized.length() > 0) {
                normalized.append(", ");
            }
            normalized.append(range);
        }
        return normalized.toString();
    }

    /** True when any range in {@code spec} overlaps any range in {@code other}. */
    public static boolean overlapsAny(String spec, String other) {
        List<Range> left = parse(spec);
        List<Range> right = parse(other);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        for (Range a : left) {
            for (Range b : right) {
                if (a.overlaps(b)) {
                    return true;
                }
            }
        }
        return false;
    }
}
