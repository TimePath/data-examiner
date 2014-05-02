package com.timepath.curses;

import static com.timepath.curses.Terminal.FONT_SIZE;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.util.Arrays;
import java.util.logging.Logger;
import javax.swing.JComponent;

@SuppressWarnings("serial")
public class Terminal extends JComponent {

    private static final Logger LOG = Logger.getLogger(Terminal.class.getName());

    public int xPos, yPos;
    public int w, h;
    public Color[] bgBuf, fgBuf;
    public char[] charBuf;
    public Dimension m;

    public Point caret = new Point(0, 0);

    public static final int FONT_SIZE = 12;

    /**
     * Java2D assumes 72 DPI.
     */
    public Font f = new Font(Font.MONOSPACED, Font.PLAIN, (int) Math.round(FONT_SIZE * Toolkit.getDefaultToolkit().getScreenResolution() / 72.0));
    public FontMetrics fm = this.getFontMetrics(f);

    protected Terminal() {
        super();
        m = new Dimension(fm.stringWidth(" "), fm.getHeight() - fm.getLeading());
    }

    public Terminal(int w, int h) {
        this();
        this.w = w;
        this.h = h;
        charBuf = new char[w * h];
        bgBuf = new Color[w * h];
        fgBuf = new Color[w * h];
        clear();
    }

    public void clear() {
        Arrays.fill(charBuf, (char) 0);
        Arrays.fill(bgBuf, Color.BLACK);
        Arrays.fill(fgBuf, Color.WHITE);
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
        newAt.translate(xPos * m.width, yPos * m.height);
        g.transform(newAt);

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
        g.setFont(f);

        char character;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Rectangle r = new Rectangle(x * m.width, y * m.height, m.width, m.height);
                g.setColor(bgBuf[x + y * w]);
                g.fillRect(r.x, r.y, r.width, r.height);
                g.setColor(fgBuf[x + y * w]);
                if ((character = charBuf[x + y * w]) == 0) {
                    continue;
                }
                g.drawString(String.valueOf(character), r.x, r.y + fm.getAscent());
            }
        }

        g.setTransform(oldAt);
        g.setColor(oldColor);
    }

    public void position(int x, int y) {
        caret.setLocation(x, y);
    }
    
    public void write(Object o) {
        write(String.valueOf(o));
    }

    public void write(String text) {
        char[] chars = text.toCharArray();
        int idx;
        for (int i = 0; i < chars.length; i++) {
            idx = (caret.x + i) + (caret.y * w);
            if (idx >= 0 && idx < charBuf.length) {
                charBuf[idx] = chars[i];
            }
        }
    }

    public Point cellToView(long ptr) {
        long x = ptr % w;
        long y = ptr / w;
        Point p = new Point((int) (x * m.width), (int) (y * m.height));
        p.translate(xPos * m.width, yPos * m.height);
        return p;
    }

    public int viewToCell(Point p) {
        p.translate(-xPos * m.width, -yPos * m.height);
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
