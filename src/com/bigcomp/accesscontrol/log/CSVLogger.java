package com.bigcomp.accesscontrol.log;

import com.bigcomp.accesscontrol.model.AccessLog;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class CSVLogger {
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("EEEE");
    private String baseDir = "logs";

    public CSVLogger() {
        new File(baseDir).mkdirs();
    }

    public void logAccess(AccessLog log, String userName) {
        try {
            LocalDateTime ts = log.getTimestamp();
            LocalDate date = ts.toLocalDate();
            int year = date.getYear();
            int month = date.getMonthValue();
            String monthName = date.getMonth().toString();
            int day = date.getDayOfMonth();
            String dayOfWeek = ts.format(dayFormatter);

            // Directory: logs/YYYY/MM_MonthName/
            String dirPath = String.format("%s/%d/%02d_%s", baseDir, year, month, monthName);
            new File(dirPath).mkdirs();

            // File: YYYY-MM-DD.csv
            String fileName = String.format("%s/%s.csv", dirPath, dateFormatter.format(date));
            File file = new File(fileName);
            boolean isNewFile = !file.exists();

            // CSV record format
            String dateStr = dateFormatter.format(date);
            String timeStr = timeFormatter.format(ts);
            String record = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    dateStr,           // 2025-12-24
                    dayOfWeek,         // Wednesday
                    timeStr,           // 14:36:49
                    log.getBadgeId(),  // BX76Z541
                    log.getReaderId(), // BR59KA87
                    log.getResourceId(), // R7U39PL2
                    log.getUserId(),   // 83746028 (badge owner ID)
                    userName,          // John:Doe
                    log.getResult()    // GRANTED / DENIED
            );

            // Append to file
            try (FileWriter fw = new FileWriter(file, true)) {
                if (isNewFile) {
                    fw.write("Date,DayOfWeek,Time,BadgeCode,ReaderCode,ResourceId,UserId,UserName,Result\n");
                }
                fw.write(record + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getBaseDir() { return baseDir; }
}
