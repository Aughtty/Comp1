package com.bigcomp.accesscontrol.ui;

import com.bigcomp.accesscontrol.log.CSVLogger;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.time.YearMonth;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced CSVReportsPanel with advanced search, filtering, statistics, and export
 */
public class CSVReportsPanel extends JPanel {
    private CSVLogger csvLogger;
    private JTable logsTable;
    private javax.swing.table.DefaultTableModel tableModel;
    private JComboBox<String> yearBox, monthBox, dayBox;
    private JTextField badgeIdSearchField, resourceIdSearchField, userNameSearchField;
    private JComboBox<String> resultFilterBox;
    private JButton loadBtn, searchBtn, exportBtn, statsBtn;
    private List<LogRecord> allLoadedRecords;

    private static class LogRecord {
        String date, dayOfWeek, time, badgeCode, readerCode, resourceId, userId, userName, result;

        LogRecord(String[] parts) {
            if (parts.length >= 9) {
                date = parts[0];
                dayOfWeek = parts[1];
                time = parts[2];
                badgeCode = parts[3];
                readerCode = parts[4];
                resourceId = parts[5];
                userId = parts[6];
                userName = parts[7];
                result = parts[8];
            }
        }

        Object[] toRow() {
            return new Object[]{date, dayOfWeek, time, badgeCode, readerCode, resourceId, userId, userName, result};
        }
    }

    public CSVReportsPanel(CSVLogger csvLogger) {
        this.csvLogger = csvLogger;
        this.allLoadedRecords = new ArrayList<>();
        setLayout(new BorderLayout());

        // Top: Filter panel
        JPanel filterPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Date filters
        gbc.gridx = 0; gbc.gridy = 0;
        filterPanel.add(new JLabel("年份:"), gbc);
        gbc.gridx = 1;
        yearBox = new JComboBox<>();
        for (int y = 2023; y <= 2026; y++) yearBox.addItem(String.valueOf(y));
        filterPanel.add(yearBox, gbc);

        gbc.gridx = 2;
        filterPanel.add(new JLabel("月份:"), gbc);
        gbc.gridx = 3;
        monthBox = new JComboBox<>();
        monthBox.addItem("全部");
        for (int m = 1; m <= 12; m++) monthBox.addItem(String.format("%02d", m));
        filterPanel.add(monthBox, gbc);

        gbc.gridx = 4;
        filterPanel.add(new JLabel("日期:"), gbc);
        gbc.gridx = 5;
        dayBox = new JComboBox<>();
        dayBox.addItem("全部");
        for (int d = 1; d <= 31; d++) dayBox.addItem(String.format("%02d", d));
        filterPanel.add(dayBox, gbc);

        gbc.gridx = 6;
        loadBtn = new JButton("加载日志");
        loadBtn.addActionListener(e -> loadCSVLogs());
        filterPanel.add(loadBtn, gbc);

        // Search filters
        gbc.gridx = 0; gbc.gridy = 1;
        filterPanel.add(new JLabel("徽章ID:"), gbc);
        gbc.gridx = 1;
        badgeIdSearchField = new JTextField(15);
        filterPanel.add(badgeIdSearchField, gbc);

        gbc.gridx = 2;
        filterPanel.add(new JLabel("资源ID:"), gbc);
        gbc.gridx = 3;
        resourceIdSearchField = new JTextField(15);
        filterPanel.add(resourceIdSearchField, gbc);

        gbc.gridx = 4;
        filterPanel.add(new JLabel("用户名:"), gbc);
        gbc.gridx = 5;
        userNameSearchField = new JTextField(15);
        filterPanel.add(userNameSearchField, gbc);

        gbc.gridx = 6;
        filterPanel.add(new JLabel("结果:"), gbc);
        gbc.gridx = 7;
        resultFilterBox = new JComboBox<>(new String[]{"全部", "GRANTED", "DENIED"});
        filterPanel.add(resultFilterBox, gbc);

        gbc.gridx = 8;
        searchBtn = new JButton("搜索");
        searchBtn.addActionListener(e -> performSearch());
        filterPanel.add(searchBtn, gbc);

        // Action buttons
        gbc.gridx = 9;
        statsBtn = new JButton("统计");
        statsBtn.addActionListener(e -> showStatistics());
        filterPanel.add(statsBtn, gbc);

        gbc.gridx = 10;
        exportBtn = new JButton("导出");
        exportBtn.addActionListener(e -> exportToCSV());
        filterPanel.add(exportBtn, gbc);

        add(filterPanel, BorderLayout.NORTH);

        // Middle: Table
        String[] cols = {"日期", "周几", "时间", "徽章代码", "读卡器代码", "资源ID", "用户ID", "用户名", "结果"};
        tableModel = new javax.swing.table.DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Read-only
            }
        };
        logsTable = new JTable(tableModel);
        logsTable.setDefaultRenderer(Object.class, new LogTableRenderer());
        add(new JScrollPane(logsTable), BorderLayout.CENTER);
    }

    private void loadCSVLogs() {
        tableModel.setRowCount(0);
        allLoadedRecords.clear();

        String year = (String) yearBox.getSelectedItem();
        String month = (String) monthBox.getSelectedItem();
        String day = (String) dayBox.getSelectedItem();

        String baseDir = csvLogger.getBaseDir();
        try {
            File yearDir = new File(baseDir, year);
            if (!yearDir.exists()) {
                JOptionPane.showMessageDialog(this, "未找到 " + year + " 的日志");
                return;
            }

            List<File> csvFiles = new ArrayList<>();
            File[] monthDirs = yearDir.listFiles(File::isDirectory);
            if (monthDirs != null) {
                for (File monthDir : monthDirs) {
                    String monthName = monthDir.getName();
                    if (month.equals("全部") || monthName.startsWith(month + "_")) {
                        File[] csvs = monthDir.listFiles((d) -> d.getName().endsWith(".csv"));
                        if (csvs != null) {
                            for (File f : csvs) {
                                String fname = f.getName().replace(".csv", "");
                                if (day.equals("全部") || fname.equals(year + "-" + month + "-" + day)) {
                                    csvFiles.add(f);
                                }
                            }
                        }
                    }
                }
            }

            for (File f : csvFiles) {
                parseCSVFile(f);
            }

            JOptionPane.showMessageDialog(this, "加载完成: " + allLoadedRecords.size() + " 条记录");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "加载错误: " + e.getMessage());
        }
    }

    private void parseCSVFile(File f) {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) {
                    first = false;
                    continue; // skip header
                }
                String[] parts = line.split(",");
                if (parts.length >= 9) {
                    LogRecord rec = new LogRecord(parts);
                    allLoadedRecords.add(rec);
                    tableModel.addRow(rec.toRow());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void performSearch() {
        String badgeId = badgeIdSearchField.getText().trim().toLowerCase();
        String resourceId = resourceIdSearchField.getText().trim().toLowerCase();
        String userName = userNameSearchField.getText().trim().toLowerCase();
        String result = (String) resultFilterBox.getSelectedItem();

        tableModel.setRowCount(0);

        for (LogRecord rec : allLoadedRecords) {
            boolean matches = true;

            if (!badgeId.isEmpty() && !rec.badgeCode.toLowerCase().contains(badgeId)) {
                matches = false;
            }
            if (!resourceId.isEmpty() && !rec.resourceId.toLowerCase().contains(resourceId)) {
                matches = false;
            }
            if (!userName.isEmpty() && !rec.userName.toLowerCase().contains(userName)) {
                matches = false;
            }
            if (!"全部".equals(result) && !rec.result.equals(result)) {
                matches = false;
            }

            if (matches) {
                tableModel.addRow(rec.toRow());
            }
        }

        JOptionPane.showMessageDialog(this, "搜索完成: " + tableModel.getRowCount() + " 条匹配记录");
    }

    private void showStatistics() {
        if (allLoadedRecords.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先加载日志");
            return;
        }

        // Count by result
        long granted = allLoadedRecords.stream().filter(r -> "GRANTED".equals(r.result)).count();
        long denied = allLoadedRecords.stream().filter(r -> "DENIED".equals(r.result)).count();

        // Top badges
        Map<String, Long> badgeCounts = allLoadedRecords.stream()
            .collect(Collectors.groupingBy(r -> r.badgeCode, Collectors.counting()));
        String topBadges = badgeCounts.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(5)
            .map(e -> e.getKey() + ": " + e.getValue())
            .collect(Collectors.joining("\n"));

        // Top resources
        Map<String, Long> resourceCounts = allLoadedRecords.stream()
            .collect(Collectors.groupingBy(r -> r.resourceId, Collectors.counting()));
        String topResources = resourceCounts.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(5)
            .map(e -> e.getKey() + ": " + e.getValue())
            .collect(Collectors.joining("\n"));

        String stats = String.format(
            "总记录数: %d\n授予访问: %d\n拒绝访问: %d\n\n" +
            "访问最频繁的徽章:\n%s\n\n" +
            "访问最频繁的资源:\n%s",
            allLoadedRecords.size(), granted, denied, topBadges, topResources
        );

        JOptionPane.showMessageDialog(this, stats, "访问统计", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportToCSV() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "没有数据可导出");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new java.io.File("访问控制报表.csv"));
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (FileWriter fw = new FileWriter(file)) {
                // Write header
                String[] cols = {"日期", "周几", "时间", "徽章代码", "读卡器代码", "资源ID", "用户ID", "用户名", "结果"};
                fw.write(String.join(",", cols) + "\n");

                // Write data rows
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    String[] row = new String[cols.length];
                    for (int j = 0; j < cols.length; j++) {
                        Object val = tableModel.getValueAt(i, j);
                        row[j] = val != null ? val.toString() : "";
                    }
                    fw.write(String.join(",", row) + "\n");
                }

                JOptionPane.showMessageDialog(this, "导出成功: " + file.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "导出失败: " + e.getMessage());
            }
        }
    }

    /**
     * Renderer to colorize rows based on result (GRANTED=green, DENIED=red)
     */
    private static class LogTableRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if (!isSelected) {
                // Get the result column (index 8)
                Object result = table.getModel().getValueAt(row, 8);
                if ("GRANTED".equals(result)) {
                    c.setBackground(new Color(200, 255, 200)); // Light green
                } else if ("DENIED".equals(result)) {
                    c.setBackground(new Color(255, 200, 200)); // Light red
                } else {
                    c.setBackground(Color.WHITE);
                }
            }
            return c;
        }
    }
}
