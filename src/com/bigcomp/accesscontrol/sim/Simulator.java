package com.bigcomp.accesscontrol.sim;

import com.bigcomp.accesscontrol.arp.AccessProcessor;
import com.bigcomp.accesscontrol.db.DB;
import com.bigcomp.accesscontrol.model.AccessLog;

import java.util.*;
import java.util.concurrent.*;

public class Simulator {
    private final DB db;
    private final AccessProcessor arp;
    private ScheduledExecutorService exec;
    private int periodMs = 1000; // default
    private boolean running = false;

    public Simulator(DB db, AccessProcessor arp) {
        this.db = db; this.arp = arp;
    }

    public void startAuto() {
        if (running) return;
        exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(this::generateAndSend, 0, periodMs, TimeUnit.MILLISECONDS);
        running = true;
    }

    public void stopAuto() {
        if (exec != null) exec.shutdownNow();
        running = false;
    }

    public void setSpeed(int ms) { this.periodMs = Math.max(200, ms); if (running) { stopAuto(); startAuto(); } }

    private void generateAndSend() {
        try {
            // pick random badge and reader
            List<String> badgeIds = new ArrayList<>();
            db.getAllBadges().forEach(b->badgeIds.add(b.getBadgeId()));
            List<String> readerIds = new ArrayList<>();
            db.getAllReaders().forEach(r->readerIds.add(r.getReaderId()));
            if (badgeIds.isEmpty() || readerIds.isEmpty()) return;
            Random rnd = new Random();
            String b = badgeIds.get(rnd.nextInt(badgeIds.size()));
            String r = readerIds.get(rnd.nextInt(readerIds.size()));
            AccessLog log = arp.processSwipe(b, r);
            // processed via listeners
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // manual swipe
    public AccessLog manualSwipe(String badgeId, String readerId) {
        return arp.processSwipe(badgeId, readerId);
    }

    public boolean isRunning() { return running; }
}
