package com.bigcomp.accesscontrol.arp;

import java.io.*;
import java.time.*;
import java.time.temporal.WeekFields;
import java.util.*;

/** Tracks per-badge per-group usage for day/week/month limits with simple file persistence. */
public class UsageTracker {
    private final Map<String, UsageWindow> counters = new HashMap<>();
    private final File file = new File("data/usage_tracker.csv");
    private final WeekFields weekFields = WeekFields.ISO;

    public UsageTracker() {
        load();
    }

    public Optional<String> checkAndIncrement(String badgeId, String group, Limits limits, LocalDateTime now) {
        if (limits == null) return Optional.empty();
        if (limits.isUnlimited()) return Optional.empty();
        String key = badgeId + "|" + group;
        UsageWindow w = counters.computeIfAbsent(key, k -> new UsageWindow(now));
        w.rollIfNeeded(now, weekFields);

        if (limits.perDay > 0 && w.dayCount >= limits.perDay) {
            return Optional.of("Daily limit reached " + w.dayCount + "/" + limits.perDay);
        }
        if (limits.perWeek > 0 && w.weekCount >= limits.perWeek) {
            return Optional.of("Weekly limit reached " + w.weekCount + "/" + limits.perWeek);
        }
        if (limits.perMonth > 0 && w.monthCount >= limits.perMonth) {
            return Optional.of("Monthly limit reached " + w.monthCount + "/" + limits.perMonth);
        }

        w.dayCount++;
        w.weekCount++;
        w.monthCount++;
        save();
        return Optional.empty();
    }

    private void load() {
        counters.clear();
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] arr = line.split("\\|");
                if (arr.length != 8) continue;
                String key = arr[0];
                LocalDate day = LocalDate.parse(arr[1]);
                int dayCount = Integer.parseInt(arr[2]);
                int week = Integer.parseInt(arr[3]);
                int weekYear = Integer.parseInt(arr[4]);
                int weekCount = Integer.parseInt(arr[5]);
                String monthKey = arr[6];
                int monthCount = Integer.parseInt(arr[7]);
                counters.put(key, new UsageWindow(day, dayCount, week, weekYear, weekCount, monthKey, monthCount));
            }
        } catch (Exception ignored) { }
    }

    private void save() {
        file.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            for (Map.Entry<String, UsageWindow> e : counters.entrySet()) {
                UsageWindow w = e.getValue();
                pw.printf("%s|%s|%d|%d|%d|%d|%s|%d%n",
                        e.getKey(),
                        w.day.toString(), w.dayCount,
                        w.week, w.weekYear, w.weekCount,
                        w.monthKey, w.monthCount);
            }
        } catch (Exception ignored) { }
    }

    public static class Limits {
        public final int perDay;
        public final int perWeek;
        public final int perMonth;
        public Limits(int perDay, int perWeek, int perMonth) {
            this.perDay = perDay; this.perWeek = perWeek; this.perMonth = perMonth;
        }
        public boolean isUnlimited() { return perDay <=0 && perWeek<=0 && perMonth<=0; }
    }

    private static class UsageWindow {
        LocalDate day;
        int dayCount;
        int week;
        int weekYear;
        int weekCount;
        String monthKey;
        int monthCount;

        UsageWindow(LocalDateTime now) {
            this(now.toLocalDate(), 0,
                    (int) WeekFields.ISO.weekOfWeekBasedYear().getFrom(now),
                    now.get(WeekFields.ISO.weekBasedYear()),
                    0,
                    now.getYear() + "-" + String.format("%02d", now.getMonthValue()),
                    0);
        }

        UsageWindow(LocalDate day, int dayCount, int week, int weekYear, int weekCount, String monthKey, int monthCount) {
            this.day = day; this.dayCount = dayCount; this.week = week; this.weekYear = weekYear; this.weekCount = weekCount; this.monthKey = monthKey; this.monthCount = monthCount;
        }

        void rollIfNeeded(LocalDateTime now, WeekFields wf) {
            LocalDate today = now.toLocalDate();
            String currentMonthKey = now.getYear() + "-" + String.format("%02d", now.getMonthValue());
            int currentWeek = (int) wf.weekOfWeekBasedYear().getFrom(now);
            int currentWeekYear = now.get(wf.weekBasedYear());

            if (!today.equals(day)) {
                day = today;
                dayCount = 0;
            }
            if (currentWeek != week || currentWeekYear != weekYear) {
                week = currentWeek;
                weekYear = currentWeekYear;
                weekCount = 0;
            }
            if (!currentMonthKey.equals(monthKey)) {
                monthKey = currentMonthKey;
                monthCount = 0;
            }
        }
    }
}
