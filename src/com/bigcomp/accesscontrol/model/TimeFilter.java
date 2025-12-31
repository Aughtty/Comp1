package com.bigcomp.accesscontrol.model;

import java.time.*;
import java.util.*;

/**
 * TimeFilter parses and evaluates time rules like:
 * 2025.July,August.Monday-Friday.8:00-12:00,14:00-17:00
 * 2026.EXCEPT June,July,August.EXCEPT Sunday.ALL
 * ALL.ALL.Monday-Friday.EXCEPT 12:00-14:00
 */
public class TimeFilter {
    private final ValueRule<Integer> years;
    private final ValueRule<Integer> months;
    private final ValueRule<Integer> daysOfMonth;
    private final ValueRule<DayOfWeek> daysOfWeek;
    private final HourRule hours;

    public TimeFilter(ValueRule<Integer> years,
                      ValueRule<Integer> months,
                      ValueRule<Integer> daysOfMonth,
                      ValueRule<DayOfWeek> daysOfWeek,
                      HourRule hours) {
        this.years = years;
        this.months = months;
        this.daysOfMonth = daysOfMonth;
        this.daysOfWeek = daysOfWeek;
        this.hours = hours;
    }

    public boolean matches(LocalDateTime dt) {
        return years.test(dt.getYear()) &&
               months.test(dt.getMonthValue()) &&
               daysOfMonth.test(dt.getDayOfMonth()) &&
               daysOfWeek.test(dt.getDayOfWeek()) &&
               hours.test(dt.toLocalTime());
    }

    public static TimeFilter parse(String expr) {
        if (expr == null || expr.isEmpty()) expr = "ALL.ALL.ALL.ALL";
        String[] parts = expr.split("\\.");
        String yearPart = parts.length > 0 ? parts[0] : "ALL";
        String monthPart = parts.length > 1 ? parts[1] : "ALL";
        String dayPart = parts.length > 2 ? parts[2] : "ALL";
        String hourPart = parts.length > 3 ? parts[3] : "ALL";

        ValueRule<Integer> years = ValueRule.parseIntRule(yearPart);
        ValueRule<Integer> months = ValueRule.parseMonthRule(monthPart);
        ValueRule<Integer> daysOfMonth = ValueRule.parseDayOfMonthRule(dayPart);
        ValueRule<DayOfWeek> daysOfWeek = ValueRule.parseDayOfWeekRule(dayPart);
        HourRule hours = HourRule.parse(hourPart);
        return new TimeFilter(years, months, daysOfMonth, daysOfWeek, hours);
    }

    /** Generic include/exclude rule */
    public static class ValueRule<T> {
        private final boolean allowAll;
        private final Set<T> include;
        private final Set<T> exclude;

        public ValueRule(boolean allowAll, Set<T> include, Set<T> exclude) {
            this.allowAll = allowAll;
            this.include = include;
            this.exclude = exclude;
        }

        public boolean test(T value) {
            if (allowAll) {
                return !exclude.contains(value);
            }
            boolean in = include.isEmpty() || include.contains(value);
            if (!in) return false;
            return !exclude.contains(value);
        }

        // Year: numbers or ranges
        public static ValueRule<Integer> parseIntRule(String part) {
            return parseIntRuleInternal(part, 1900, 3000);
        }

        // Months: names or numbers
        public static ValueRule<Integer> parseMonthRule(String part) {
            Map<String,Integer> map = monthMap();
            return parseNumericWithNames(part, map, 1, 12);
        }

        // Days of month: 1-31
        public static ValueRule<Integer> parseDayOfMonthRule(String part) {
            if (part.matches(".*[A-Za-z].*")) {
                // expression is probably day-of-week; accept all days-of-month
                return new ValueRule<>(true, new HashSet<>(), new HashSet<>());
            }
            return parseNumericWithNames(part, Collections.emptyMap(), 1, 31);
        }

        // Days of week: Monday, Tue, etc.
        public static ValueRule<DayOfWeek> parseDayOfWeekRule(String part) {
            String p = part.trim();
            if (p.equalsIgnoreCase("ALL")) return new ValueRule<>(true, new HashSet<>(), new HashSet<>());
            Set<DayOfWeek> include = new HashSet<>();
            Set<DayOfWeek> exclude = new HashSet<>();
            for (String token : p.split(",")) {
                String t = token.trim();
                if (t.isEmpty()) continue;
                boolean exc = t.toUpperCase().startsWith("EXCEPT ");
                if (exc) t = t.substring(7).trim();
                if (t.contains("-")) {
                    String[] arr = t.split("-");
                    DayOfWeek start = parseDayOfWeek(arr[0]);
                    DayOfWeek end = parseDayOfWeek(arr[1]);
                    for (DayOfWeek d = start; ; d = d.plus(1)) {
                        (exc ? exclude : include).add(d);
                        if (d == end) break;
                    }
                } else {
                    DayOfWeek d = parseDayOfWeek(t);
                    (exc ? exclude : include).add(d);
                }
            }
            return new ValueRule<>(false, include, exclude);
        }

        private static ValueRule<Integer> parseNumericWithNames(String part, Map<String,Integer> nameMap, int min, int max) {
            String p = part.trim();
            if (p.equalsIgnoreCase("ALL")) return new ValueRule<>(true, new HashSet<>(), new HashSet<>());
            Set<Integer> include = new HashSet<>();
            Set<Integer> exclude = new HashSet<>();
            for (String token : p.split(",")) {
                String t = token.trim();
                if (t.isEmpty()) continue;
                boolean exc = t.toUpperCase().startsWith("EXCEPT ");
                if (exc) t = t.substring(7).trim();
                if (t.contains("-")) {
                    String[] arr = t.split("-");
                    int start = toNumber(arr[0], nameMap, min, max);
                    int end = toNumber(arr[1], nameMap, min, max);
                    for (int i = start; ; i++) {
                        (exc ? exclude : include).add(i);
                        if (i == end) break;
                        if (i > max) break;
                    }
                } else {
                    int v = toNumber(t, nameMap, min, max);
                    (exc ? exclude : include).add(v);
                }
            }
            return new ValueRule<>(false, include, exclude);
        }

        private static ValueRule<Integer> parseIntRuleInternal(String part, int min, int max) {
            return parseNumericWithNames(part, Collections.emptyMap(), min, max);
        }

        private static int toNumber(String token, Map<String,Integer> map, int min, int max) {
            String t = token.trim();
            if (map.containsKey(t.toUpperCase())) return map.get(t.toUpperCase());
            try {
                int v = Integer.parseInt(t.replace(" ",""));
                if (v < min || v > max) throw new IllegalArgumentException("Value out of range: " + v);
                return v;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid number token: " + token);
            }
        }

        private static DayOfWeek parseDayOfWeek(String token) {
            String t = token.trim().toUpperCase();
            if (t.startsWith("MON")) return DayOfWeek.MONDAY;
            if (t.startsWith("TUE")) return DayOfWeek.TUESDAY;
            if (t.startsWith("WED")) return DayOfWeek.WEDNESDAY;
            if (t.startsWith("THU")) return DayOfWeek.THURSDAY;
            if (t.startsWith("FRI")) return DayOfWeek.FRIDAY;
            if (t.startsWith("SAT")) return DayOfWeek.SATURDAY;
            if (t.startsWith("SUN")) return DayOfWeek.SUNDAY;
            throw new IllegalArgumentException("Invalid day of week: " + token);
        }

        private static Map<String,Integer> monthMap() {
            Map<String,Integer> m = new HashMap<>();
            String[] names = {"JANUARY","FEBRUARY","MARCH","APRIL","MAY","JUNE","JULY","AUGUST","SEPTEMBER","OCTOBER","NOVEMBER","DECEMBER"};
            for (int i=0;i<names.length;i++) {
                m.put(names[i], i+1);
                m.put(names[i].substring(0,3), i+1);
            }
            return m;
        }
    }

    /** Hour ranges with allow/exclude semantics */
    public static class HourRule {
        private final boolean allowAll;
        private final List<Range> include;
        private final List<Range> exclude;

        public HourRule(boolean allowAll, List<Range> include, List<Range> exclude) {
            this.allowAll = allowAll;
            this.include = include;
            this.exclude = exclude;
        }

        public boolean test(LocalTime time) {
            if (allowAll) {
                return exclude.stream().noneMatch(r -> r.contains(time));
            }
            boolean inInclude = include.isEmpty() || include.stream().anyMatch(r -> r.contains(time));
            if (!inInclude) return false;
            return exclude.stream().noneMatch(r -> r.contains(time));
        }

        public static HourRule parse(String part) {
            String p = part.trim();
            if (p.equalsIgnoreCase("ALL")) return new HourRule(true, new ArrayList<>(), new ArrayList<>());
            List<Range> inc = new ArrayList<>();
            List<Range> exc = new ArrayList<>();
            for (String token : p.split(",")) {
                String t = token.trim();
                if (t.isEmpty()) continue;
                boolean isExc = t.toUpperCase().startsWith("EXCEPT ");
                if (isExc) t = t.substring(7).trim();
                Range r = parseRange(t);
                (isExc ? exc : inc).add(r);
            }
            return new HourRule(false, inc, exc);
        }

        private static Range parseRange(String token) {
            if (!token.contains("-")) {
                LocalTime t = parseTime(token);
                return new Range(t, t);
            }
            String[] arr = token.split("-");
            LocalTime start = parseTime(arr[0]);
            LocalTime end = parseTime(arr[1]);
            return new Range(start, end);
        }

        private static LocalTime parseTime(String t) {
            String s = t.trim();
            if (s.contains(":")) return LocalTime.parse(s.length() == 4 ? "0" + s : s);
            int h = Integer.parseInt(s);
            return LocalTime.of(h, 0);
        }

        public static class Range {
            private final LocalTime start;
            private final LocalTime end;
            public Range(LocalTime start, LocalTime end) {
                this.start = start; this.end = end;
            }
            public boolean contains(LocalTime t) {
                return !t.isBefore(start) && !t.isAfter(end);
            }
        }
    }
}
