package com.bigcomp.accesscontrol;

import com.bigcomp.accesscontrol.db.DB;
import com.bigcomp.accesscontrol.arp.AccessProcessor;
import com.bigcomp.accesscontrol.log.CSVLogger;
import com.bigcomp.accesscontrol.sim.Simulator;
import com.bigcomp.accesscontrol.ui.MainWindow;

import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        // Start in EDT
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            // Initialize DB
            DB db = new DB();
            db.initialize(); // creates logs table if needed and loads initial data

            // CSV Logger
            CSVLogger csvLogger = new CSVLogger();

            // ARP
            AccessProcessor arp = new AccessProcessor(db, csvLogger);

            // Simulator
            Simulator sim = new Simulator(db, arp);

            // Main Window
            MainWindow window = new MainWindow(db, arp, sim, csvLogger);
            window.setPreferredSize(new Dimension(1100, 700));
            window.pack();
            window.setLocationRelativeTo(null);
            window.setVisible(true);

            // Start simulator in stopped state; user can start it from UI
        });
    }
}