package com.bigcomp.accesscontrol.ui;

import com.bigcomp.accesscontrol.db.DB;
import com.bigcomp.accesscontrol.model.Reader;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class MapPanel extends JPanel {
    private DB db;
    private BufferedImage bg;
    private Map<String, Flash> flashes = new ConcurrentHashMap<>();

    private String currentImageName = null;

    public MapPanel(DB db) {
        this.db = db;
        // load default image (prefer 1.png)
        java.util.List<String> imgs = listAvailableImages();
        if (imgs.contains("1.png")) setBackgroundImage("1.png");
        else if (!imgs.isEmpty()) setBackgroundImage(imgs.get(0));
        else bg = new BufferedImage(800,600,BufferedImage.TYPE_INT_RGB);
        setPreferredSize(new Dimension(800,600));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.WHITE);
        g2.fillRect(0,0,getWidth(),getHeight());
        // draw background scaled with aspect ratio and centered
        int drawX = 0, drawY = 0, drawW = getWidth(), drawH = getHeight();
        if (bg != null) {
            int imgW = bg.getWidth();
            int imgH = bg.getHeight();
            double scale = Math.min((double)getWidth()/imgW, (double)getHeight()/imgH);
            drawW = (int)(imgW * scale);
            drawH = (int)(imgH * scale);
            drawX = (getWidth() - drawW)/2;
            drawY = (getHeight() - drawH)/2;
            g2.drawImage(bg, drawX, drawY, drawW, drawH, null);
        }

        // draw readers (coords are in a source coordinate space; map them to the drawn image)
        Collection<Reader> readers = db.getAllReaders();
        for (Reader r : readers) {
            // assume coordinates are relative to an 800x600 base; if bg present, scale accordingly
            double sx = drawW / (double) (bg != null ? bg.getWidth() : 800);
            double sy = drawH / (double) (bg != null ? bg.getHeight() : 600);
            int x = drawX + (int) (r.getUiX() * sx);
            int y = drawY + (int) (r.getUiY() * sy);
            Color c = Color.GRAY;
            Flash f = flashes.get(r.getReaderId());
            if (f != null) c = f.granted ? Color.GREEN : Color.RED;
            g2.setColor(c);
            g2.fillOval(x-8, y-8, 16, 16);
            g2.setColor(Color.BLACK);
            g2.drawOval(x-8, y-8, 16, 16);
        }
    }

    public void flashReader(String readerId, boolean granted) {
        flashes.put(readerId, new Flash(granted));
        repaint();
        // schedule removal
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
        ex.schedule(() -> { flashes.remove(readerId); repaint(); ex.shutdown(); }, 2, TimeUnit.SECONDS);
    }

    public java.util.List<String> listAvailableImages() {
        File dir = new File("resources/img");
        if (!dir.exists() || !dir.isDirectory()) return java.util.Collections.emptyList();
        String[] names = dir.list((d,n)->{ String l = n.toLowerCase(); return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg"); });
        if (names == null) return java.util.Collections.emptyList();
        java.util.List<String> out = java.util.Arrays.asList(names);
        java.util.Collections.sort(out);
        return out;
    }

    public void setBackgroundImage(String name) {
        if (name == null) return;
        File f = new File("resources/img", name);
        if (!f.exists()) return;
        try {
            BufferedImage im = ImageIO.read(f);
            if (im != null) {
                this.bg = im;
                this.currentImageName = name;
                repaint();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public String getCurrentImageName() { return currentImageName; }

    private static class Flash { boolean granted; public Flash(boolean g){this.granted=g;} }
}
