package com.bigcomp.accesscontrol.ui;

import com.bigcomp.accesscontrol.db.DB;

import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class ReportsPanel extends JPanel {
    private DB db;
    private java.util.List<com.bigcomp.accesscontrol.model.AccessLog> cached = new java.util.ArrayList<>();
    private JTextField badgeField = new JTextField(8);
    private JTextField readerField = new JTextField(8);
    private JTextField resourceField = new JTextField(8);
    private JTextField startField = new JTextField(12);
    private JTextField endField = new JTextField(12);
    private JComboBox<String> resultBox = new JComboBox<>(new String[]{"ALL","GRANTED","DENIED"});

    public ReportsPanel(DB db) {
        this.db = db;
        setLayout(new BorderLayout());
        JButton load = new JButton("加载");
        JButton filter = new JButton("筛选");
        JTable table = new JTable();
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Start (yyyy-MM-dd HH:mm):"));
        top.add(startField);
        top.add(new JLabel("End:"));
        top.add(endField);
        top.add(new JLabel("Badge:"));
        top.add(badgeField);
        top.add(new JLabel("Reader:"));
        top.add(readerField);
        top.add(new JLabel("Resource:"));
        top.add(resourceField);
        top.add(new JLabel("Result:"));
        top.add(resultBox);
        top.add(load);
        top.add(filter);
        add(top, BorderLayout.NORTH);

        load.addActionListener(e -> {
            try {
                cached = db.getRecentLogs(1000);
                populateTable(table, cached);
            } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Failed to load logs: " + ex.getMessage()); }
        });

        filter.addActionListener(e -> {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            java.time.LocalDateTime start = null, end = null;
            try { if (!startField.getText().trim().isEmpty()) start = java.time.LocalDateTime.parse(startField.getText().trim(), fmt); } catch (Exception ex) {}
            try { if (!endField.getText().trim().isEmpty()) end = java.time.LocalDateTime.parse(endField.getText().trim(), fmt); } catch (Exception ex) {}

            String badge = badgeField.getText().trim().toLowerCase();
            String reader = readerField.getText().trim().toLowerCase();
            String resource = resourceField.getText().trim().toLowerCase();
            String result = (String) resultBox.getSelectedItem();

            java.util.List<com.bigcomp.accesscontrol.model.AccessLog> filtered = new java.util.ArrayList<>();
            for (var l : cached) {
                if (start != null && l.getTimestamp().isBefore(start)) continue;
                if (end != null && l.getTimestamp().isAfter(end)) continue;
                if (!badge.isEmpty() && (l.getBadgeId()==null || !l.getBadgeId().toLowerCase().contains(badge))) continue;
                if (!reader.isEmpty() && (l.getReaderId()==null || !l.getReaderId().toLowerCase().contains(reader))) continue;
                if (!resource.isEmpty() && (l.getResourceId()==null || !l.getResourceId().toLowerCase().contains(resource))) continue;
                if (!"ALL".equals(result) && !result.equalsIgnoreCase(l.getResult())) continue;
                filtered.add(l);
            }
            populateTable(table, filtered);
        });
    }

    private void populateTable(JTable table, java.util.List<com.bigcomp.accesscontrol.model.AccessLog> logs) {
        var model = new javax.swing.table.DefaultTableModel(new String[]{"Time","Badge","Reader","Resource","Result","Message"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (var l : logs) {
            model.addRow(new Object[]{l.getTimestamp(), l.getBadgeId(), l.getReaderId(), l.getResourceId(), l.getResult(), l.getMessage()});
        }
        table.setModel(model);
    }
}