package com.bigcomp.accesscontrol.util;

import java.time.*;
import java.util.*;
import java.util.regex.*;

/**
 * TimeFilter: Parses and evaluates complex time-based access rules.
 * Supports formats like:
 * - 2025.July,August.Monday-Friday.8:00-12:00,14:00-17:00
 * - 2026.EXCEPT June,July,August.EXCEPT Sunday.ALL
 * - ALL.ALL.Monday-Friday.EXCEPT 12:00-14:00
 */
public class TimeFilter {
    private YearFilter yearFilter;
    private MonthFilter monthFilter;
    private DayOfWeekFilter dayOfWeekFilter;
    private TimeRangeFilter timeRangeFilter;

    public TimeFilter(String rule) throws IllegalArgumentException {
        if (rule == null || rule.trim().isEmpty()) {
            throw new IllegalArgumentException("TimeFilter rule cannot be empty");
        }
        parse(rule.trim());
    }

    private void parse(String rule) throws IllegalArgumentException {
        String[] parts = rule.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("TimeFilter must have 4 parts separated by dots: YEAR.MONTH.DAY_OF_WEEK.HOUR");
        }

        yearFilter = new YearFilter(parts[0].trim());
        monthFilter = new MonthFilter(parts[1].trim());
        dayOfWeekFilter = new DayOfWeekFilter(parts[2].trim());
        timeRangeFilter = new TimeRangeFilter(parts[3].trim());
    }

    /**
     * Check if the given LocalDateTime matches this time filter
     */
    public boolean matches(LocalDateTime dateTime) {
        if (dateTime == null) return false;
        return yearFilter.matches(dateTime.getYear())
                && monthFilter.matches(dateTime.getMonth())
                && dayOfWeekFilter.matches(dateTime.getDayOfWeek())
                && timeRangeFilter.matches(dateTime.toLocalTime());
    }

    // ============ Year Filter ============
    private static class YearFilter {
        private boolean isAll;
        private boolean isExcept;
        private Set<Integer> years;

        YearFilter(String rule) throws IllegalArgumentException {
            years = new HashSet<>();
            if ("ALL".equalsIgnoreCase(rule)) {
                isAll = true;
                isExcept = false;
            } else if (rule.toUpperCase().startsWith("EXCEPT ")) {
                isAll = false;
                isExcept = true;
                String[] parts = rule.substring(7).split(",");
                for (String part : parts) {
                    try {
                        years.add(Integer.parseInt(part.trim()));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid year in TimeFilter: " + part);
                    }
                }
            } else {
                isAll = false;
                isExcept = false;
                String[] parts = rule.split(",");
                for (String part : parts) {
                    try {
                        years.add(Integer.parseInt(part.trim()));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid year in TimeFilter: " + part);
                    }
                }
            }
        }

        boolean matches(int year) {
            if (isAll) return true;
            if (isExcept) return !years.contains(year);
            return years.contains(year);
        }
    }

    // ============ Month Filter ============
    private static class MonthFilter {
        private boolean isAll;
        private boolean isExcept;
        private Set<Month> months;

        MonthFilter(String rule) throws IllegalArgumentException {
            months = new HashSet<>();
            if ("ALL".equalsIgnoreCase(rule)) {
                isAll = true;
                isExcept = false;
            } else if (rule.toUpperCase().startsWith("EXCEPT ")) {
                isAll = false;
                isExcept = true;
                String[] parts = rule.substring(7).split(",");
                for (String part : parts) {
                    months.add(parseMonth(part.trim()));
                }
            } else {
                isAll = false;
                isExcept = false;
                String[] parts = rule.split(",");
                for (String part : parts) {
                    months.add(parseMonth(part.trim()));
                }
            }
        }

        private Month parseMonth(String monthStr) throws IllegalArgumentException {
            try {
                return Month.valueOf(monthStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid month in TimeFilter: " + monthStr);
            }
        }

        boolean matches(Month month) {
            if (isAll) return true;
            if (isExcept) return !months.contains(month);
            return months.contains(month);
        }
    }

    // ============ Day of Week Filter ============
    private static class DayOfWeekFilter {
        private boolean isAll;
        private boolean isExcept;
        private Set<DayOfWeek> daysOfWeek;

        DayOfWeekFilter(String rule) throws IllegalArgumentException {
            daysOfWeek = new HashSet<>();
            if ("ALL".equalsIgnoreCase(rule)) {
                isAll = true;
                isExcept = false;
            } else if (rule.toUpperCase().startsWith("EXCEPT ")) {
                isAll = false;
                isExcept = true;
                parseDaysOfWeek(rule.substring(7));
            } else {
                isAll = false;
                isExcept = false;
                parseDaysOfWeek(rule);
            }
        }

        private void parseDaysOfWeek(String rule) throws IllegalArgumentException {
            String[] parts = rule.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.contains("-")) {
                    // Range: Monday-Friday
                    String[] range = part.split("-");
                    if (range.length != 2) {
                        throw new IllegalArgumentException("Invalid day range in TimeFilter: " + part);
                    }
                    DayOfWeek start = parseDayOfWeek(range[0].trim());
                    DayOfWeek end = parseDayOfWeek(range[1].trim());
                    addDayRange(start, end);
                } else {
                    daysOfWeek.add(parseDayOfWeek(part));
                }
            }
        }

        private DayOfWeek parseDayOfWeek(String dayStr) throws IllegalArgumentException {
            try {
                return DayOfWeek.valueOf(dayStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid day of week in TimeFilter: " + dayStr);
            }
        }

        private void addDayRange(DayOfWeek start, DayOfWeek end) {
            int startVal = start.getValue();
            int endVal = end.getValue();
            if (startVal <= endVal) {
                for (int i = startVal; i <= endVal; i++) {
                    daysOfWeek.add(DayOfWeek.of(i));
                }
            } else {
                // Wrap around (e.g., Friday-Monday)
                for (int i = startVal; i <= 7; i++) {
                    daysOfWeek.add(DayOfWeek.of(i));
                }
                for (int i = 1; i <= endVal; i++) {
                    daysOfWeek.add(DayOfWeek.of(i));
                }
            }
        }

        boolean matches(DayOfWeek dayOfWeek) {
            if (isAll) return true;
            if (isExcept) return !daysOfWeek.contains(dayOfWeek);
            return daysOfWeek.contains(dayOfWeek);
        }
    }

    // ============ Time Range Filter ============
    private static class TimeRangeFilter {
        private boolean isAll;
        private boolean isExcept;
        private List<TimeRange> ranges;

        TimeRangeFilter(String rule) throws IllegalArgumentException {
            ranges = new ArrayList<>();
            if ("ALL".equalsIgnoreCase(rule)) {
                isAll = true;
                isExcept = false;
            } else if (rule.toUpperCase().startsWith("EXCEPT ")) {
                isAll = false;
                isExcept = true;
                parseTimeRanges(rule.substring(7));
            } else {
                isAll = false;
                isExcept = false;
                parseTimeRanges(rule);
            }
        }

        private void parseTimeRanges(String rule) throws IllegalArgumentException {
            String[] parts = rule.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.contains("-")) {
                    String[] times = part.split("-");
                    if (times.length != 2) {
                        throw new IllegalArgumentException("Invalid time range in TimeFilter: " + part);
                    }
                    LocalTime start = parseTime(times[0].trim());
                    LocalTime end = parseTime(times[1].trim());
                    ranges.add(new TimeRange(start, end));
                } else {
                    throw new IllegalArgumentException("Time must be in range format HH:MM-HH:MM: " + part);
                }
            }
        }

        private LocalTime parseTime(String timeStr) throws IllegalArgumentException {
            try {
                return LocalTime.parse(timeStr);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid time format in TimeFilter (expected HH:MM): " + timeStr);
            }
        }

        boolean matches(LocalTime time) {
            if (isAll) return true;
            boolean inRange = false;
            for (TimeRange r : ranges) {
                if (r.contains(time)) {
                    inRange = true;
                    break;
                }
            }
            if (isExcept) return !inRange;
            return inRange;
        }

        private static class TimeRange {
            LocalTime start;
            LocalTime end;

            TimeRange(LocalTime start, LocalTime end) {
                this.start = start;
                this.end = end;
            }

            boolean contains(LocalTime time) {
                return !time.isBefore(start) && !time.isAfter(end);
            }
        }
    }

    // Test method
    public static void main(String[] args) {
        try {
            TimeFilter tf1 = new TimeFilter("2025.July,August.Monday-Friday.8:00-12:00,14:00-17:00");
            LocalDateTime dt1 = LocalDateTime.of(2025, 7, 21, 10, 30); // Mon, Jul 21, 2025 10:30
            System.out.println("Test 1: " + tf1.matches(dt1)); // should be true

            TimeFilter tf2 = new TimeFilter("2026.EXCEPT June,July,August.EXCEPT Sunday.ALL");
            LocalDateTime dt2 = LocalDateTime.of(2026, 5, 25, 14, 0); // Mon, May 25, 2026
            System.out.println("Test 2: " + tf2.matches(dt2)); // should be true

            TimeFilter tf3 = new TimeFilter("ALL.ALL.Monday-Friday.EXCEPT 12:00-14:00");
            LocalDateTime dt3 = LocalDateTime.of(2025, 12, 22, 15, 0); // Mon, Dec 22, 2025 15:00
            System.out.println("Test 3: " + tf3.matches(dt3)); // should be true

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
