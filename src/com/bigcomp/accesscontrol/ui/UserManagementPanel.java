package com.bigcomp.accesscontrol.ui;

import com.bigcomp.accesscontrol.db.DB;
import com.bigcomp.accesscontrol.model.User;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class UserManagementPanel extends JPanel {
    private DB db;
    private JTable userTable;
    private javax.swing.table.DefaultTableModel tableModel;

    public UserManagementPanel(DB db) {
        this.db = db;
        setLayout(new BorderLayout());

        // User list
        String[] cols = {"User ID", "ID Number", "First Name", "Last Name", "Gender"};
        tableModel = new javax.swing.table.DefaultTableModel(cols, 0);
        userTable = new JTable(tableModel);
        add(new JScrollPane(userTable), BorderLayout.CENTER);

        // Refresh button
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadUsers());
        add(refresh, BorderLayout.SOUTH);

        loadUsers();
    }

    private void loadUsers() {
        tableModel.setRowCount(0);
        java.util.Collection<User> users = db.getAllUsers();
        for (User u : users) {
            tableModel.addRow(new Object[]{u.getUserId(), u.getIdNumber(), u.getFirstName(), u.getLastName(), u.getGender()});
        }
    }
}
