package com.timepath.curses;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 *
 * @author TimePath
 */
@SuppressWarnings("serial")
public class Multiplexer extends Terminal {

    private static final Logger LOG = Logger.getLogger(Multiplexer.class.getName());

    private final List<Terminal> terms = new LinkedList<Terminal>();

    public Multiplexer(Terminal... args) {
        super();
        add(args);

        charBuf = new char[w * h];

        bgBuf = new Color[w * h];
        Arrays.fill(bgBuf, Color.BLACK);

        fgBuf = new Color[w * h];
        Arrays.fill(fgBuf, Color.WHITE);
    }

    public void add(Terminal... args) {
        for (Terminal t : args) {
            terms.add(t);
            w = Math.max(t.xPos + t.w, w);
            h = Math.max(t.yPos + t.h, h);
        }
    }

    @Override
    public void paint(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        g.setColor(this.getBackground());
        g.fillRect(0, 0, w * m.width, h * m.height);
        for (Terminal t : terms) {
            t.paint(g);
        }
    }

}
