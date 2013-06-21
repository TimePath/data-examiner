package com.timepath.hex;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 *
 * @author timepath
 */
public class Editor extends JPanel {

    public static final String PROP_CARETLOCATION = "PROP_CARETLOCATION";
    public static final String PROP_MARKLOCATION = "PROP_MARKLOCATION";
    private final transient PropertyChangeSupport propertyChangeSupport = new java.beans.PropertyChangeSupport(this);
    private final transient VetoableChangeSupport vetoableChangeSupport = new java.beans.VetoableChangeSupport(this);
    Terminal calc;

    public Editor() {
        this.setBackground(Color.BLACK);
        this.setForeground(Color.WHITE);
        this.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                try {
                    int c = e.getKeyCode();
                    switch (c) {
                        case KeyEvent.VK_UP:
                            setCaretLocation(getCaretLocation() - cols);
                            if (!selecting) {
                                setMarkLocation(-1);
                            }
                            break;
                        case KeyEvent.VK_DOWN:
                            setCaretLocation(Math.min(getCaretLocation() + cols, eof));
                            if (!selecting) {
                                setMarkLocation(-1);
                            }
                            break;
                        case KeyEvent.VK_LEFT:
                            setCaretLocation(getCaretLocation() - 1);
                            if (!selecting) {
                                setMarkLocation(-1);
                            }
                            break;
                        case KeyEvent.VK_RIGHT:
                            setCaretLocation(getCaretLocation() + 1);
                            if (!selecting) {
                                setMarkLocation(-1);
                            }
                            break;
                        case KeyEvent.VK_SHIFT:
                            selecting = true;
                            if (Editor.this.getMarkLocation() < 0) {
                                Editor.this.setMarkLocation(Editor.this.getCaretLocation());
                            }
                            break;
                        case KeyEvent.VK_HOME:
                            if (e.isControlDown()) {
                                seek(0);
                                setCaretLocation(0);
                            } else {
                                setCaretLocation(getCaretLocation() - getCaretLocation() % cols);
                            }
                            if (!selecting) {
                                setMarkLocation(-1);
                            }
                            break;
                        case KeyEvent.VK_END:
                            if (e.isControlDown()) {
                                seek(((eof + cols - 1) / cols * cols) - (cols * rows));
                                repaint();
                                setCaretLocation(((eof) % cols) + (cols * (rows - 1)));
                            } else {
                                setCaretLocation(Math.min(getCaretLocation() + (cols - 1 - getCaretLocation() % cols), eof));
                            }
                            if (!selecting) {
                                setMarkLocation(-1);
                            }
                            break;
                        case KeyEvent.VK_PAGE_DOWN:
                            skip(cols);
                            break;
                        case KeyEvent.VK_PAGE_UP:
                            skip(-cols);
                            break;
                    }
                } catch (PropertyVetoException ex) {
                    Logger.getLogger(Editor.class.getName()).log(Level.FINER, null, ex);
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                int c = e.getKeyCode();
                switch (c) {
                    case KeyEvent.VK_SHIFT:
                        selecting = false;
                        break;
                }
            }
        });
        this.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    int c = tD.viewToCell(e.getPoint());
                    if (c >= 0) {
                        int i = (c + 1) / 3;
                        try {
                            Editor.this.setCaretLocation(i + offset);
                        } catch (PropertyVetoException ex) {
                        }
                    }

                    c = tT.viewToCell(e.getPoint());
                    if (c >= 0) {
                        try {
                            Editor.this.setCaretLocation(c + offset);
                        } catch (PropertyVetoException ex) {
                        }
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
            }
        });
        this.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (SwingUtilities.isLeftMouseButton(e)) {
                    int c = tD.viewToCell(e.getPoint());
                    if (c >= 0) {
                        int i = (c + 1) / 3;
                        try {
                            if (!selecting) {
                                Editor.this.setMarkLocation(i + offset);
                            }
                            Editor.this.setCaretLocation(i + offset);
                        } catch (PropertyVetoException ex) {
                        }
                    }
                    c = tT.viewToCell(e.getPoint());
                    if (c >= 0) {
                        try {
                            if (!selecting) {
                                Editor.this.setMarkLocation(c + offset);
                            }
                            Editor.this.setCaretLocation(c + offset);
                        } catch (PropertyVetoException ex) {
                        }
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }
        });
        this.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
                    skip(e.getUnitsToScroll() * cols);
                    Editor.this.repaint();
                } else {
                    System.err.println(offset);
                }
            }
        });
        this.propertyChangeSupport.addPropertyChangeListener(PROP_CARETLOCATION, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                int oldPos = (int) evt.getOldValue();
                int newPos = (int) evt.getNewValue();
                if (newPos < offset) {
                    seek((newPos - 1 + ((newPos - oldPos + cols - 1) / cols)) / cols);
                } else if (newPos >= offset + (rows * cols)) {
                    skip(newPos);
                }

//                if (selecting) {
//                Editor.this.repaint(calcPolygon(Editor.this.getMarkLocation(), oldPos).getBounds());
//                Editor.this.repaint(calcPolygon(Editor.this.getMarkLocation(), newPos).getBounds());
//                } else {
//                Editor.this.repaint(getCellRect(oldPos));
//                Editor.this.repaint(getCellRect(newPos));
//                }
                try {
                    buf.position(getCaretLocation() - offset);
                    int pos = buf.position();
                    long v;

                    int[] cols = {0, 6, 18};
                    int l = 0;

                    calc = new Terminal(29, 7);
                    calc.position(cols[0], l);
                    calc.write("   8");
                    calc.position(cols[0], l + 1);
                    calc.write("±  8");
                    calc.position(cols[0], l + 2);
                    calc.write("  16");
                    calc.position(cols[0], l + 3);
                    calc.write("± 16");
                    calc.position(cols[0], l + 4);
                    calc.write("  32");
                    calc.position(cols[0], l + 5);
                    calc.write("± 32");

                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    calc.position(cols[1], l);
                    calc.write("" + (buf.get() & 0xFF));
                    buf.position(pos);

                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    v = buf.get();
                    calc.position(cols[1] + (v < 0 ? -1 : 0), l + 1);
                    calc.write("" + v);
                    buf.position(pos);



                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    calc.position(cols[1], l + 2);
                    calc.write("" + (buf.getShort() & 0xFFFF));
                    buf.position(pos);

                    buf.order(ByteOrder.BIG_ENDIAN);
                    calc.position(cols[2], l + 2);
                    calc.write("" + (buf.getShort() & 0xFFFF));
                    buf.position(pos);

                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    v = buf.getShort();
                    calc.position(cols[1] + (v < 0 ? -1 : 0), l + 3);
                    calc.write("" + v);
                    buf.position(pos);

                    buf.order(ByteOrder.BIG_ENDIAN);
                    v = buf.getShort();
                    calc.position(cols[2] + (v < 0 ? -1 : 0), l + 3);
                    calc.write("" + v);
                    buf.position(pos);



                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    calc.position(cols[1], l + 4);
                    calc.write("" + ((long) buf.getInt() & 0xFFFFFFFFL));
                    buf.position(pos);

                    buf.order(ByteOrder.BIG_ENDIAN);
                    calc.position(cols[2], l + 4);
                    calc.write("" + ((long) buf.getInt() & 0xFFFFFFFFL));
                    buf.position(pos);

                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    v = buf.getInt();
                    calc.position(cols[1] + (v < 0 ? -1 : 0), l + 5);
                    calc.write("" + v);
                    buf.position(pos);

                    buf.order(ByteOrder.BIG_ENDIAN);
                    v = buf.getInt();
                    calc.position(cols[2] + (v < 0 ? -1 : 0), l + 5);
                    calc.write("" + v);
                    buf.position(pos);
                } catch (BufferUnderflowException e) {
                }

                repaint();
            }
        });
        this.vetoableChangeSupport.addVetoableChangeListener(PROP_CARETLOCATION, new VetoableChangeListener() {
            @Override
            public void vetoableChange(PropertyChangeEvent evt) throws PropertyVetoException {
                int v = (int) evt.getNewValue();
                long max = eof;
                int min = 0;
                if (v < min || v > max) {
                    throw new PropertyVetoException("Caret would be out of bounds", evt);
                }
            }
        });
        this.setFocusable(true);
        requestFocusInWindow();
    }
    private Dimension m = new Dimension();
    private int caretLocation = 10;
    private int markLocation = -1;
    private int cols = 16;
    private int rows = 16;
    private int offset = 0;
    boolean selecting;

    void seek(int seek) {
        if (seek < 0) {
            seek = 0;
        }
        if (seek > eof) {
            seek = eof;
        }
        offset = seek;
        repaint();
    }

    void skip(int delta) {
        seek(offset + delta);
    }

    @Override
    public void repaint(Rectangle r) {
        super.repaint(r.x, r.y, r.width + 1, r.height + 1);
//        repaint();
    }
    Terminal tD, tT;

    @Override
    public void paint(Graphics graphics) {
        //<editor-fold defaultstate="collapsed" desc="Init">
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);

        g.setColor(this.getBackground());
        g.fillRect(0, 0, this.getWidth(), this.getHeight());

        g.setColor(this.getForeground());
        int fontSize = 12;
        Font f = new Font(Font.MONOSPACED, Font.PLAIN, (int) Math.round(fontSize * Toolkit.getDefaultToolkit().getScreenResolution() / 72.0)); // Java2D = 72 DPI
        g.setFont(f);
        m.width = g.getFontMetrics().getMaxAdvance();
        m.height = g.getFontMetrics().getMaxAscent() + g.getFontMetrics().getMaxDescent();
        int leading = 2;
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Column numbers">
        Terminal header = new Terminal(3 * cols - 1, 1);
        header.xPos = m.width * 9;
        for (int i = 0; i < header.w; i++) {
            header.bg[i] = Color.WHITE;
            header.fg[i] = Color.BLACK;
            if (i % 3 == 0) {
                header.position(i, 0);
                header.write(String.format("%02X", (i / 3) & 0xFFFFF));
            }
        }
        header.print(g);
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Row numbers">
        Terminal lines = new Terminal(8, rows);
        lines.yPos = m.height + leading;
        for (int i = 0; i < rows; i++) {
            for (int x = 0; x < lines.w; x++) {
                lines.fg[x + i * lines.w] = Color.GREEN;
                lines.bg[x + i * lines.w] = Color.DARK_GRAY;
            }
            String address = String.format("%08X", (i * cols + offset) & 0xFFFFF);
            lines.position(0, i);
            lines.write(address);
        }
        lines.print(g);
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Data">
        if (rf != null) {
            try {
                rf.seek(offset & 0xFFFFFFFF);
                byte[] array = new byte[Math.min(cols * (rows + 1), (int) rf.length() - offset)];
                rf.read(array);
                buf = ByteBuffer.wrap(array);
            } catch (IOException ex) {
                Logger.getLogger(Editor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (buf != null) {
            tD = new Terminal(cols * 3, rows);
            tD.xPos = m.width * 9;
            tD.yPos = m.height + leading;
            tT = new Terminal(cols, rows);
            tT.xPos = m.width * 9 + (m.width * cols * 3);
            tT.yPos = m.height + leading;
            int i = 0;
            byte b[] = new byte[cols];
            while (buf.hasRemaining()) {
                int read = Math.min(buf.remaining(), b.length);
                buf.get(b, 0, read);

                StringBuilder sb = new StringBuilder(cols * 3 - 1);
                for (int s = 0; s < read; s++) {
                    if (s > 0) {
                        sb.append(" ");
                    }
                    sb.append(String.format("%02X", (b[s] & 0xFF) & 0xFFFFF));
                }
                tD.position(0, i);
                tD.write(sb.toString());

                StringBuilder sb2 = new StringBuilder(cols * 3 - 1);
                for (int s = 0; s < read; s++) {
                    sb2.append(disp(b[s] & 0xFF));
                }
                tT.position(0, i);
                tT.write(sb2.toString());

                if (++i >= rows) {
                    break;
                }
            }
            tD.print(g);
            tT.print(g);
        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Selection">
        if (getMarkLocation() >= 0) {
            Polygon p = calcPolygon(tD, this.getMarkLocation(), this.getCaretLocation(), 2, 1);

            g.setColor(Color.RED);
            g.drawPolygon(p);

            p = calcPolygon(tT, this.getMarkLocation(), this.getCaretLocation(), 1, 0);
            g.drawPolygon(p);

            g.setColor(Color.YELLOW);
            g.draw(getCellRect(getMarkLocation()));
        }
        g.setColor(Color.WHITE);
        g.draw(getCellRect(getCaretLocation()));
        //</editor-fold>
        if (calc != null) {
            calc.yPos = (m.height + leading) * (rows + 1);
            calc.print(g);
        }
    }

    Rectangle getCellRect(int address) {
        address -= offset;
        Point p = tD.cellToView(address * 3);
        return new Rectangle(p.x, p.y, m.width * 2, m.height);
    }

    Polygon calcPolygon(Terminal term, int markIdx, int caretIdx, int width, int spacing) {
        caretIdx -= offset;
        int caretCol = (caretIdx % cols);
        int caretRow = (caretIdx / cols);
        if (caretIdx < 0) {
            caretIdx = 0;
        } else if (caretIdx > (cols * rows)) {
            caretIdx = (cols * rows) - 1;
        }
        Point caretPos = term.cellToView(caretIdx * (width + spacing));
        caretPos.translate(-term.xPos, -term.yPos);

        markIdx -= offset;
        int markCol = (markIdx % cols);
        int markRow = (markIdx / cols);
        if (markIdx < 0) {
            markIdx = 0;
        } else if (markIdx > (cols * rows)) {
            markIdx = (cols * rows) - 1;
        }
        Point markPos = term.cellToView(markIdx * (width + spacing));
        markPos.translate(-term.xPos, -term.yPos);

        Point rel = new Point(caretIdx - markIdx, caretRow - markRow);

        if (rel.x >= 0) { // further right
            caretPos.x += m.width * width;
        } else {
            markPos.x += m.width * width;
        }
        if (rel.y >= 0) { // further down
            caretPos.y += m.height;
        } else {
            markPos.y += m.height;
        }

        Polygon p = new Polygon();
        p.addPoint(markPos.x, markPos.y);

        if (rel.y > 0) {
            p.addPoint((cols * (width + spacing) - spacing) * m.width, markPos.y);
            p.addPoint((cols * (width + spacing) - spacing) * m.width, caretPos.y - m.height);
            p.addPoint(caretPos.x, caretPos.y - m.height);
        } else if (rel.y < 0) {
            p.addPoint(0, markPos.y);
            p.addPoint(0, caretPos.y + m.height);
            p.addPoint(caretPos.x, caretPos.y + m.height);
        } else {
            p.addPoint(caretPos.x, markPos.y);
        }

        p.addPoint(caretPos.x, caretPos.y);

        if (rel.y > 0) {
            p.addPoint(0, caretPos.y);
            p.addPoint(0, markPos.y + m.height);
            p.addPoint(markPos.x, markPos.y + m.height);
        } else if (rel.y < 0) {
            p.addPoint((cols * (width + spacing) - spacing) * m.width, caretPos.y);
            p.addPoint((cols * (width + spacing) - spacing) * m.width, markPos.y - m.height);
            p.addPoint(markPos.x, markPos.y - m.height);
        } else {
            p.addPoint(markPos.x, caretPos.y);
        }
        p.translate(term.xPos, term.yPos);
        return p;
    }

    private String disp(int b) {
        char c = (char) b;
        boolean valid = !Character.isWhitespace(c) && !Character.isISOControl(c);
        if (!valid) {
            c = '.';
        }
        return "" + c;
    }

    /**
     * @return the caretLocation
     */
    public int getCaretLocation() {
        return caretLocation;
    }

    /**
     * @param caretLocation the caretLocation to set
     */
    public void setCaretLocation(int caretLocation) throws PropertyVetoException {
        int oldCaretLocation = this.caretLocation;
        if (oldCaretLocation == caretLocation) {
            return;
        }
        vetoableChangeSupport.fireVetoableChange(PROP_CARETLOCATION, oldCaretLocation, caretLocation);
        this.caretLocation = caretLocation;
        propertyChangeSupport.firePropertyChange(PROP_CARETLOCATION, oldCaretLocation, caretLocation);
    }

    /**
     * @return the markLocation
     */
    public int getMarkLocation() {
        return markLocation;
    }

    /**
     * @param markLocation the markLocation to set
     */
    public void setMarkLocation(int markLocation) throws PropertyVetoException {
        int oldMarkLocation = this.markLocation;
        if (oldMarkLocation == markLocation) {
            return;
        }
        vetoableChangeSupport.fireVetoableChange(PROP_MARKLOCATION, oldMarkLocation, markLocation);
        this.markLocation = markLocation;
        propertyChangeSupport.firePropertyChange(PROP_MARKLOCATION, oldMarkLocation, markLocation);
    }
    ByteBuffer buf;
    RandomAccessFile rf;
    int eof;

    void setData(RandomAccessFile rf) {
        this.rf = rf;
        try {
            this.eof = (int) rf.length() - 1;
        } catch (IOException ex) {
            Logger.getLogger(Editor.class.getName()).log(Level.SEVERE, null, ex);
        }
        repaint();
    }
}
