package com.bigcomp.accesscontrol.ui;

import com.bigcomp.accesscontrol.db.DB;

import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class ReportsPanel extends JPanel {
    private DB db;

    public ReportsPanel(DB db) {
        this.db = db;
        setLayout(new BorderLayout());
        JButton load = new JButton("Load Logs");
        JTable table = new JTable();
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(load, BorderLayout.NORTH);
        load.addActionListener(e -> {
            try {
                var logs = db.getRecentLogs(500);
                var model = new javax.swing.table.DefaultTableModel(new String[]{"Time","Badge","Reader","Resource","Result","Message"}, 0);
                for (var l : logs) {
                    model.addRow(new Object[]{l.getTimestamp(), l.getBadgeId(), l.getReaderId(), l.getResourceId(), l.getResult(), l.getMessage()});
                }
                table.setModel(model);
            } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Failed to load logs: " + ex.getMessage()); }
        });
    }
}