package com.bigcomp.accesscontrol.ui;

import com.bigcomp.accesscontrol.db.DB;
import com.bigcomp.accesscontrol.model.AccessRight;
import com.bigcomp.accesscontrol.model.Profile;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ProfileEditorPanel provides UI for creating, editing, and managing access profiles
 */
public class ProfileEditorPanel extends JPanel {
    private DB db;
    private JTextField profileNameField, descriptionField;
    private JComboBox<String> groupCombo;
    private JTextField timeFilterField;
    private JButton addRightBtn, removeRightBtn, saveBtn, deleteBtn, newBtn;
    private JTable accessRightsTable;
    private DefaultTableModel tableModel;
    private Profile currentProfile;
    private List<AccessRight> currentRights;

    public ProfileEditorPanel(DB db) {
        this.db = db;
        this.currentRights = new ArrayList<>();
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top: Profile basics
        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        topPanel.add(new JLabel("档案名:"), gbc);
        gbc.gridx = 1;
        profileNameField = new JTextField(20);
        topPanel.add(profileNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        topPanel.add(new JLabel("描述:"), gbc);
        gbc.gridx = 1;
        descriptionField = new JTextField(20);
        topPanel.add(descriptionField, gbc);

        add(topPanel, BorderLayout.NORTH);

        // Middle: Access Rights table
        JPanel middlePanel = new JPanel(new BorderLayout());
        String[] cols = {"资源组", "时间过滤规则"};
        tableModel = new DefaultTableModel(cols, 0);
        accessRightsTable = new JTable(tableModel);
        middlePanel.add(new JScrollPane(accessRightsTable), BorderLayout.CENTER);

        // Control panel for rights
        JPanel ctrlPanel = new JPanel(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        ctrlPanel.add(new JLabel("资源组:"), gbc);
        gbc.gridx = 1;
        groupCombo = new JComboBox<>();
        for (String g : db.getAllGroupNames()) groupCombo.addItem(g);
        ctrlPanel.add(groupCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        ctrlPanel.add(new JLabel("时间规则:"), gbc);
        gbc.gridx = 1;
        timeFilterField = new JTextField(40);
        timeFilterField.setText("ALL.ALL.ALL.ALL");
        ctrlPanel.add(timeFilterField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        addRightBtn = new JButton("添加权限");
        addRightBtn.addActionListener(e -> addAccessRight());
        ctrlPanel.add(addRightBtn, gbc);

        gbc.gridx = 1;
        removeRightBtn = new JButton("删除选中权限");
        removeRightBtn.addActionListener(e -> removeAccessRight());
        ctrlPanel.add(removeRightBtn, gbc);

        middlePanel.add(ctrlPanel, BorderLayout.SOUTH);
        add(middlePanel, BorderLayout.CENTER);

        // Bottom: Action buttons
        JPanel bottomPanel = new JPanel(new FlowLayout());
        newBtn = new JButton("新建档案");
        newBtn.addActionListener(e -> newProfile());
        bottomPanel.add(newBtn);

        saveBtn = new JButton("保存档案");
        saveBtn.addActionListener(e -> saveProfile());
        bottomPanel.add(saveBtn);

        deleteBtn = new JButton("删除档案");
        deleteBtn.addActionListener(e -> deleteProfile());
        bottomPanel.add(deleteBtn);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void addAccessRight() {
        String group = (String) groupCombo.getSelectedItem();
        String timeRule = timeFilterField.getText().trim();

        if (group == null || group.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请选择资源组");
            return;
        }
        if (timeRule.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入时间规则");
            return;
        }

        // Check if already exists
        for (AccessRight r : currentRights) {
            if (r.getGroupName().equals(group)) {
                JOptionPane.showMessageDialog(this, "该资源组的权限已存在，请删除后再添加");
                return;
            }
        }

        try {
            AccessRight right = new AccessRight(group, timeRule);
            currentRights.add(right);
            tableModel.addRow(new Object[]{group, timeRule});
            timeFilterField.setText("ALL.ALL.ALL.ALL");
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "时间规则无效: " + e.getMessage());
        }
    }

    private void removeAccessRight() {
        int selectedRow = accessRightsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一行");
            return;
        }
        currentRights.remove(selectedRow);
        tableModel.removeRow(selectedRow);
    }

    private void newProfile() {
        currentProfile = null;
        currentRights.clear();
        profileNameField.setText("");
        descriptionField.setText("");
        tableModel.setRowCount(0);
        profileNameField.setEditable(true);
    }

    private void saveProfile() {
        String profileName = profileNameField.getText().trim();
        if (profileName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "档案名不能为空");
            return;
        }
        if (currentRights.isEmpty()) {
            JOptionPane.showMessageDialog(this, "至少添加一个权限");
            return;
        }

        Profile profile = new Profile(profileName);
        profile.setDescription(descriptionField.getText().trim());
        for (AccessRight right : currentRights) {
            profile.addAccessRight(right);
        }

        if (db.saveProfile(profile)) {
            JOptionPane.showMessageDialog(this, "档案保存成功");
            newProfile();
        } else {
            JOptionPane.showMessageDialog(this, "保存失败");
        }
    }

    private void deleteProfile() {
        String profileName = profileNameField.getText().trim();
        if (profileName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先创建或加载一个档案");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
            "确定要删除档案 " + profileName + " 吗?",
            "确认删除", JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            if (db.deleteProfile(profileName)) {
                JOptionPane.showMessageDialog(this, "档案已删除");
                newProfile();
            } else {
                JOptionPane.showMessageDialog(this, "删除失败");
            }
        }
    }
}
