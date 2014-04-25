package com.timepath.hex;

import java.awt.Color;
import java.awt.Graphics;
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
        add(args);

        c = new char[w * h];

        bg = new Color[w * h];
        Arrays.fill(bg, Color.BLACK);

        fg = new Color[w * h];
        Arrays.fill(fg, Color.WHITE);
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
        graphics.setColor(this.getBackground());
        graphics.fillRect(0, 0, w, h);
        for (Terminal t : terms) {
            t.paint(graphics);
        }
    }

}
