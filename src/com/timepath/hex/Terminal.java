package com.timepath.hex;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 *
 * @author TimePath
 */
@SuppressWarnings("serial")
public class Terminal extends Component {

    private static final Logger LOG = Logger.getLogger(Terminal.class.getName());

    int xPos, yPos;
    int w, h;
    Color bg[], fg[];
    char c[];
    Dimension m;

    Point caret = new Point(0, 0);

    protected Terminal() {

    }

    public Terminal(int w, int h) {
        this.w = w;
        this.h = h;
        c = new char[w * h];
        bg = new Color[w * h];
        fg = new Color[w * h];
        clear();
    }

    public void clear() {
        Arrays.fill(c, (char) 0);
        Arrays.fill(bg, Color.BLACK);
        Arrays.fill(fg, Color.WHITE);
    }

    @Override
    public Dimension getPreferredSize() {
        if (m == null) {
            return super.getPreferredSize();
        }
        return new Dimension(w * m.width, h * m.height);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public void paint(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        Color oldColor = g.getColor();
        AffineTransform oldAt = g.getTransform();
        AffineTransform newAt = new AffineTransform();
        newAt.translate(xPos, yPos);
        g.transform(newAt);

        FontMetrics fm = g.getFontMetrics();
        int ascent = fm.getMaxAscent();
        int descent = fm.getMaxDescent();
        int leading = 2;
        m = new Dimension(fm.getMaxAdvance(), ascent + descent + leading);

        char character;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Rectangle r = new Rectangle(x * m.width, y * m.height, m.width, m.height);
                g.setColor(bg[x + y * w]);
                g.fillRect(r.x, r.y, r.width, r.height);
                g.setColor(fg[x + y * w]);
                if ((character = c[x + y * w]) == 0) {
                    continue;
                }
                g.drawString(String.valueOf(character), r.x, r.y + ascent);
            }
        }
        g.setTransform(oldAt);
        g.setColor(oldColor);
    }

    public void position(int x, int y) {
        caret.setLocation(x, y);
    }

    public void write(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            c[(caret.x + i) + (caret.y * w)] = chars[i];
        }
    }

    Point cellToView(long ptr) {
        long x = ptr % w;
        long y = ptr / w;
        Point p = new Point((int) (x * m.width), (int) (y * m.height));
        p.translate(xPos, yPos);
        return p;
    }

    int viewToCell(Point p) {
        p.translate(-xPos, -yPos);
        if (p.x < 0 || p.x >= w * m.width) {
            return -1;
        }
        if (p.y < 0 || p.y >= h * m.height) {
            return -1;
        }
        int x = p.x / m.width;
        int y = p.y / m.height;
        return (w * y) + x;
    }
}
