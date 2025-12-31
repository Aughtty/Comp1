package com.bigcomp.accesscontrol.ui;

import com.bigcomp.accesscontrol.db.DB;
import com.bigcomp.accesscontrol.model.User;

import javax.swing.*;
import java.awt.*;

/**
 * UserEditorPanel provides UI for creating, editing, and deleting users
 */
public class UserEditorPanel extends JPanel {
    private DB db;
    private JTextField userIdField, firstNameField, lastNameField, genderField, idNumberField;
    private JButton saveBtn, deleteBtn, newBtn;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private User currentUser;

    public UserEditorPanel(DB db) {
        this.db = db;
        setLayout(new BorderLayout());

        // Left: User List
        JPanel leftPanel = new JPanel(new BorderLayout());
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = userList.getSelectedIndex();
                if (idx >= 0) {
                    loadUserAtIndex(idx);
                }
            }
        });
        leftPanel.add(new JScrollPane(userList), BorderLayout.CENTER);

        JButton refreshBtn = new JButton("刷新");
        refreshBtn.addActionListener(e -> refreshUserList());
        leftPanel.add(refreshBtn, BorderLayout.SOUTH);
        add(leftPanel, BorderLayout.WEST);

        // Right: Edit Form
        JPanel rightPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // User ID
        gbc.gridx = 0; gbc.gridy = 0;
        rightPanel.add(new JLabel("用户 ID:"), gbc);
        gbc.gridx = 1;
        userIdField = new JTextField(15);
        rightPanel.add(userIdField, gbc);

        // First Name
        gbc.gridx = 0; gbc.gridy = 1;
        rightPanel.add(new JLabel("名字:"), gbc);
        gbc.gridx = 1;
        firstNameField = new JTextField(15);
        rightPanel.add(firstNameField, gbc);

        // Last Name
        gbc.gridx = 0; gbc.gridy = 2;
        rightPanel.add(new JLabel("姓氏:"), gbc);
        gbc.gridx = 1;
        lastNameField = new JTextField(15);
        rightPanel.add(lastNameField, gbc);

        // Gender
        gbc.gridx = 0; gbc.gridy = 3;
        rightPanel.add(new JLabel("性别:"), gbc);
        gbc.gridx = 1;
        genderField = new JTextField(15);
        rightPanel.add(genderField, gbc);

        // ID Number
        gbc.gridx = 0; gbc.gridy = 4;
        rightPanel.add(new JLabel("身份证号:"), gbc);
        gbc.gridx = 1;
        idNumberField = new JTextField(15);
        rightPanel.add(idNumberField, gbc);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout());
        newBtn = new JButton("新建");
        newBtn.addActionListener(e -> newUser());
        btnPanel.add(newBtn);

        saveBtn = new JButton("保存");
        saveBtn.addActionListener(e -> saveUser());
        btnPanel.add(saveBtn);

        deleteBtn = new JButton("删除");
        deleteBtn.addActionListener(e -> deleteUser());
        btnPanel.add(deleteBtn);

        gbc.gridx = 0; gbc.gridy = 5;
        gbc.gridwidth = 2;
        rightPanel.add(btnPanel, gbc);

        add(rightPanel, BorderLayout.CENTER);

        refreshUserList();
    }

    private void refreshUserList() {
        userListModel.clear();
        for (User u : db.getAllUsers()) {
            userListModel.addElement(u.getUserId() + " - " + u.getFullName());
        }
    }

    private void loadUserAtIndex(int idx) {
        java.util.List<User> users = new java.util.ArrayList<>(db.getAllUsers());
        if (idx >= 0 && idx < users.size()) {
            currentUser = users.get(idx);
            userIdField.setText(currentUser.getUserId());
            firstNameField.setText(currentUser.getFirstName());
            lastNameField.setText(currentUser.getLastName());
            genderField.setText(currentUser.getGender() != null ? currentUser.getGender() : "");
            idNumberField.setText(currentUser.getIdNumber() != null ? currentUser.getIdNumber() : "");
            userIdField.setEditable(false);
        }
    }

    private void newUser() {
        currentUser = null;
        userIdField.setText("");
        firstNameField.setText("");
        lastNameField.setText("");
        genderField.setText("");
        idNumberField.setText("");
        userIdField.setEditable(true);
        userList.clearSelection();
    }

    private void saveUser() {
        String userId = userIdField.getText().trim();
        if (userId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "用户 ID 不能为空");
            return;
        }

        User user = new User();
        user.setUserId(userId);
        user.setFirstName(firstNameField.getText().trim());
        user.setLastName(lastNameField.getText().trim());
        user.setGender(genderField.getText().trim());
        user.setIdNumber(idNumberField.getText().trim());

        if (db.saveUser(user)) {
            JOptionPane.showMessageDialog(this, "用户保存成功");
            refreshUserList();
            newUser();
        } else {
            JOptionPane.showMessageDialog(this, "保存失败");
        }
    }

    private void deleteUser() {
        if (currentUser == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个用户");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this, 
            "确定要删除用户 " + currentUser.getFullName() + " 吗?", 
            "确认删除", JOptionPane.YES_NO_OPTION);
        
        if (choice == JOptionPane.YES_OPTION) {
            if (db.deleteUser(currentUser.getUserId())) {
                JOptionPane.showMessageDialog(this, "用户已删除");
                refreshUserList();
                newUser();
            } else {
                JOptionPane.showMessageDialog(this, "删除失败");
            }
        }
    }
}
