package com.timepath.curses;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
@SuppressWarnings("serial")
public class Multiplexer extends Terminal {

    private static final Logger LOG = Logger.getLogger(Multiplexer.class.getName());
    private final Collection<Terminal> terms = new LinkedList<>();

    public Multiplexer() {
    }

    @SuppressWarnings("OverloadedVarargsMethod")
    public Multiplexer(Terminal... args) {
        add(args);
        charBuf = new char[termWidth * termHeight];
        bgBuf = new Color[termWidth * termHeight];
        Arrays.fill(bgBuf, Color.BLACK);
        fgBuf = new Color[termWidth * termHeight];
        Arrays.fill(fgBuf, Color.WHITE);
    }

    protected void add(@NotNull Terminal... args) {
        for (@NotNull Terminal t : args) {
            terms.add(t);
            termWidth = Math.max(t.xPos + t.termWidth, termWidth);
            termHeight = Math.max(t.yPos + t.termHeight, termHeight);
        }
    }

    @Override
    public void paint(Graphics g) {
        @NotNull Graphics2D g2 = (Graphics2D) g;
        g2.setColor(getBackground());
        g2.fillRect(0, 0, termWidth * metrics.width, termHeight * metrics.height);
        for (@NotNull Terminal t : terms) {
            t.paint(g2);
        }
    }
}
