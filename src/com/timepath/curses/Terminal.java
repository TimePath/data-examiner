package com.timepath.curses;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.Arrays;
import java.util.logging.Logger;

@SuppressWarnings("serial")
public class Terminal extends JComponent {

    private static final Logger      LOG         = Logger.getLogger(Terminal.class.getName());
    private static final int         FONT_SIZE   = 12;
    /**
     * Java2D assumes 72 DPI.
     */
    private              Font        termFont    = new Font(Font.MONOSPACED,
                                                            Font.PLAIN,
                                                            (int) Math.round(( FONT_SIZE * Toolkit.getDefaultToolkit()
                                                                                                  .getScreenResolution() ) / 72.0)
    );
    private              FontMetrics fontMetrics = getFontMetrics(termFont);
    public int xPos, yPos;
    public Color[] bgBuf, fgBuf;
    protected Dimension metrics;
    int termWidth, termHeight;
    char[] charBuf;
    private Point caret = new Point(0, 0);

    public Terminal(int w, int h) {
        this();
        termWidth = w;
        termHeight = h;
        charBuf = new char[w * h];
        bgBuf = new Color[w * h];
        fgBuf = new Color[w * h];
        clear();
    }

    Terminal() {
        metrics = new Dimension(fontMetrics.stringWidth(" "), fontMetrics.getHeight() - fontMetrics.getLeading());
    }

    public void clear() {
        Arrays.fill(charBuf, (char) 0);
        Arrays.fill(bgBuf, Color.BLACK);
        Arrays.fill(fgBuf, Color.WHITE);
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        Color oldColor = g2.getColor();
        AffineTransform oldAt = g2.getTransform();
        AffineTransform newAt = new AffineTransform();
        newAt.translate(xPos * metrics.width, yPos * metrics.height);
        g2.transform(newAt);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
        g2.setFont(termFont);
        for(int y = 0; y < termHeight; y++) {
            for(int x = 0; x < termWidth; x++) {
                Rectangle r = new Rectangle(x * metrics.width, y * metrics.height, metrics.width, metrics.height);
                g2.setColor(bgBuf[x + ( y * termWidth )]);
                g2.fillRect(r.x, r.y, r.width, r.height);
                g2.setColor(fgBuf[x + ( y * termWidth )]);
                char character;
                if(( character = charBuf[x + ( y * termWidth )] ) == 0) {
                    continue;
                }
                g2.drawString(String.valueOf(character), r.x, r.y + fontMetrics.getAscent());
            }
        }
        g2.setTransform(oldAt);
        g2.setColor(oldColor);
    }

    @Override
    public Dimension getPreferredSize() {
        if(metrics == null) {
            return super.getPreferredSize();
        }
        return new Dimension(termWidth * metrics.width, termHeight * metrics.height);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    public void position(int x, int y) {
        caret.setLocation(x, y);
    }

    public void write(Object o) {
        String text = String.valueOf(o);
        char[] chars = text.toCharArray();
        for(int i = 0; i < chars.length; i++) {
            int idx = caret.x + i + ( caret.y * termWidth );
            if(( idx >= 0 ) && ( idx < charBuf.length )) {
                charBuf[idx] = chars[i];
            }
        }
    }

    public Point cellToView(long ptr) {
        long x = ptr % termWidth;
        long y = ptr / termWidth;
        Point p = new Point((int) ( x * metrics.width ), (int) ( y * metrics.height ));
        p.translate(xPos * metrics.width, yPos * metrics.height);
        return p;
    }

    public int viewToCell(Point p) {
        p.translate(-xPos * metrics.width, -yPos * metrics.height);
        if(( p.x < 0 ) || ( p.x >= ( termWidth * metrics.width ) )) {
            return -1;
        }
        if(( p.y < 0 ) || ( p.y >= ( termHeight * metrics.height ) )) {
            return -1;
        }
        int x = p.x / metrics.width;
        int y = p.y / metrics.height;
        return ( termWidth * y ) + x;
    }
}
