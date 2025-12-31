package com.bigcomp.accesscontrol.ui;

import com.bigcomp.accesscontrol.sim.Simulator;
import com.bigcomp.accesscontrol.db.DB;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ControlPanel extends JPanel {
    private JTextArea logArea = new JTextArea();
    private Simulator sim;
    private DB db;

    private JComboBox<String> badgeBox = new JComboBox<>();
    private JComboBox<String> readerBox = new JComboBox<>();
    private JComboBox<String> imageBox = new JComboBox<>();
    private JToggleButton autoBtn = new JToggleButton("Start Auto");
    private JSlider speedSlider = new JSlider(200,2000,1000);
    private JComboBox<String> groupBox = new JComboBox<>();

    public ControlPanel(DB db, Simulator sim, MapPanel mapPanel) {
        this.db = db; this.sim = sim;
        setLayout(new BorderLayout());
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        // Top: image selector
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Map:"));
        java.util.List<String> imgs = mapPanel.listAvailableImages();
        imageBox.addItem("(none)");
        for (String s : imgs) imageBox.addItem(s);
        if (mapPanel.getCurrentImageName() != null) imageBox.setSelectedItem(mapPanel.getCurrentImageName());
        imageBox.addActionListener(e -> {
            String sel = (String) imageBox.getSelectedItem();
            if (sel == null || "(none)".equals(sel)) return;
            mapPanel.setBackgroundImage(sel);
        });
        top.add(imageBox);
        add(top, BorderLayout.NORTH);

        JPanel bottom = new JPanel();
        bottom.setLayout(new GridLayout(0,1));

        JPanel manual = new JPanel();
        manual.add(new JLabel("Badge:"));
        for (var b : db.getAllBadges()) badgeBox.addItem(b.getBadgeId());
        manual.add(badgeBox);
        manual.add(new JLabel("Reader:"));
        for (var r : db.getAllReaders()) readerBox.addItem(r.getReaderId());
        manual.add(readerBox);
        JButton swipe = new JButton("Swipe");
        swipe.addActionListener(e -> {
            String b = (String) badgeBox.getSelectedItem();
            String r = (String) readerBox.getSelectedItem();
            if (b!=null && r!=null) sim.manualSwipe(b,r);
        });
        manual.add(swipe);
        JButton update = new JButton("Hold to Update");
        update.addActionListener(e -> {
            String b = (String) badgeBox.getSelectedItem();
            String r = (String) readerBox.getSelectedItem();
            if (b!=null && r!=null) sim.manualUpdate(b,r);
        });
        manual.add(update);
        bottom.add(manual);

        JPanel auto = new JPanel();
        auto.add(autoBtn);
        autoBtn.addActionListener(e -> {
            if (autoBtn.isSelected()) { sim.startAuto(); autoBtn.setText("Stop Auto"); }
            else { sim.stopAuto(); autoBtn.setText("Start Auto"); }
        });
        auto.add(new JLabel("Speed (ms):"));
        speedSlider.addChangeListener(e -> sim.setSpeed(speedSlider.getValue()));
        auto.add(speedSlider);
        bottom.add(auto);

        JPanel evacuation = new JPanel();
        evacuation.add(new JLabel("Resource Group:"));
        for (String g : db.getAllGroupNames()) groupBox.addItem(g);
        evacuation.add(groupBox);
        JButton setUncontrolled = new JButton("Set Uncontrolled (Evac)");
        setUncontrolled.addActionListener(e -> toggleGroup(false));
        JButton setControlled = new JButton("Restore Controlled");
        setControlled.addActionListener(e -> toggleGroup(true));
        evacuation.add(setUncontrolled);
        evacuation.add(setControlled);
        bottom.add(evacuation);

        add(bottom, BorderLayout.SOUTH);
    }

    private void toggleGroup(boolean controlled) {
        String g = (String) groupBox.getSelectedItem();
        if (g == null) return;
        boolean ok = db.setGroupControlled(g, controlled);
        JOptionPane.showMessageDialog(this, ok ? "Updated group " + g : "Failed to update group state");
    }

    public void appendLog(String s) { logArea.append(s + "\n"); }
}
