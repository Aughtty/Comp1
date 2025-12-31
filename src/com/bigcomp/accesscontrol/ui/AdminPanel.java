package com.bigcomp.accesscontrol.ui;

import com.bigcomp.accesscontrol.db.DB;
import com.bigcomp.accesscontrol.model.Badge;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

public class AdminPanel extends JPanel {
    private DB db;
    private JTable table;
    private javax.swing.table.DefaultTableModel tableModel;

    public AdminPanel(DB db) {
        this.db = db;
        setLayout(new BorderLayout());
        
        // Badge management table
        String[] cols = {"Badge ID", "User", "Active", "Expires", "Requires Update", "Grace Period End"};
        tableModel = new javax.swing.table.DefaultTableModel(cols, 0);
        loadBadges();
        
        table = new JTable(tableModel);
        table.setPreferredScrollableViewportSize(new Dimension(800, 300));
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Control buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton revokeBtn = new JButton("Revoke Selected");
        JButton markUpdateBtn = new JButton("Mark For Update");
        JButton processUpdateBtn = new JButton("Process Update");
        JButton refreshBtn = new JButton("Refresh");
        
        revokeBtn.addActionListener(e -> onRevokeBadge());
        markUpdateBtn.addActionListener(e -> onMarkForUpdate());
        processUpdateBtn.addActionListener(e -> onProcessUpdate());
        refreshBtn.addActionListener(e -> loadBadges());
        
        buttonPanel.add(revokeBtn);
        buttonPanel.add(markUpdateBtn);
        buttonPanel.add(processUpdateBtn);
        buttonPanel.add(refreshBtn);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void loadBadges() {
        tableModel.setRowCount(0);
        for (Badge b : db.getAllBadges()) {
            Object[] row = {
                b.getBadgeId(),
                b.getUserId(),
                b.isActive(),
                b.getExpirationDate(),
                b.isRequiresUpdate(),
                b.getUpdateGracePeriodEnd()
            };
            tableModel.addRow(row);
        }
    }

    private void onRevokeBadge() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a badge");
            return;
        }

        String badgeId = (String) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Revoke badge " + badgeId + "?");
        if (confirm == JOptionPane.YES_OPTION) {
            if (db.revokeBadge(badgeId)) {
                JOptionPane.showMessageDialog(this, "Badge revoked");
                loadBadges();
            }
        }
    }

    private void onMarkForUpdate() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a badge");
            return;
        }

        String badgeId = (String) tableModel.getValueAt(row, 0);
        Optional<Badge> obadge = db.findBadge(badgeId);
        if (obadge.isPresent()) {
            Badge badge = obadge.get();
            badge.setRequiresUpdate(true);
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            badge.setUpdateDueDate(now.plusDays(1));
            badge.setUpdateGracePeriodEnd(now.plusDays(7));
            if (db.updateBadgeUpdateStatus(badge)) {
                JOptionPane.showMessageDialog(this, "Badge marked for update (7-day grace period)");
                loadBadges();
            }
        }
    }

    private void onProcessUpdate() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a badge");
            return;
        }

        String badgeId = (String) tableModel.getValueAt(row, 0);
        Optional<Badge> obadge = db.findBadge(badgeId);
        if (obadge.isPresent()) {
            Badge badge = obadge.get();
            if (!badge.isRequiresUpdate()) {
                JOptionPane.showMessageDialog(this, "Badge does not require update");
                return;
            }
            
            badge.setRequiresUpdate(false);
            badge.setLastUpdateTime(java.time.LocalDateTime.now());
            if (db.updateBadgeUpdateStatus(badge)) {
                JOptionPane.showMessageDialog(this, "Badge updated successfully");
                loadBadges();
            }
        }
    }
}
