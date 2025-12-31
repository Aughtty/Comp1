package com.bigcomp.accesscontrol.ui;

import com.bigcomp.accesscontrol.arp.AccessProcessor;
import com.bigcomp.accesscontrol.db.DB;
import com.bigcomp.accesscontrol.log.CSVLogger;
import com.bigcomp.accesscontrol.model.AccessLog;
import com.bigcomp.accesscontrol.sim.Simulator;

import javax.swing.*;
import java.awt.*;

public class MainWindow extends JFrame implements AccessProcessor.AccessEventListener {
    private DB db;
    private AccessProcessor arp;
    private Simulator sim;
    private CSVLogger csvLogger;

    private MapPanel mapPanel;
    private ControlPanel controlPanel;

    public MainWindow(DB db, AccessProcessor arp, Simulator sim, CSVLogger csvLogger) {
        super("BigComp 访问控制系统 - 原型演示");
        this.db = db; this.arp = arp; this.sim = sim; this.csvLogger = csvLogger;
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initUI();

        arp.addListener(this);
    }

    private void initUI() {
        JTabbedPane tabs = new JTabbedPane();
        // Monitor tab
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mapPanel = new MapPanel(db);
        controlPanel = new ControlPanel(db, sim, mapPanel);
        split.setLeftComponent(controlPanel);
        split.setRightComponent(mapPanel);
        split.setDividerLocation(320);
        tabs.addTab("实时监控", split);

        // Admin and Reports
        tabs.addTab("徽章管理", new AdminPanel(db));
        tabs.addTab("用户管理", new UserManagementPanel(db));
        tabs.addTab("编辑用户", new UserEditorPanel(db));
        tabs.addTab("档案编辑", new ProfileEditorPanel(db));
        tabs.addTab("访问报表", new CSVReportsPanel(csvLogger));

        this.getContentPane().add(tabs, BorderLayout.CENTER);
        this.setSize(1400, 900);
        this.setLocationRelativeTo(null);
    }

    @Override
    public void onAccessEvent(AccessLog log) {
        // update UI: append to control's log, trigger map flash
        SwingUtilities.invokeLater(() -> {
            controlPanel.appendLog(format(log));
            mapPanel.flashReader(log.getReaderId(), "GRANTED".equals(log.getResult()));
        });
    }

    private String format(AccessLog l) {
        return String.format("[%s] %s -> %s (%s) %s",
            l.getTimestamp(), l.getBadgeId(), l.getResourceId(), l.getResult(), 
            l.getMessage() != null ? l.getMessage() : "");
    }
}
