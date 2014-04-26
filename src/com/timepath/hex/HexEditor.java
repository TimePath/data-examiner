package com.timepath.hex;

import com.timepath.curses.Multiplexer;
import com.timepath.curses.Terminal;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

@SuppressWarnings("serial")
public class HexEditor extends Multiplexer implements KeyListener, MouseMotionListener, MouseListener, MouseWheelListener {

    public static final String PROP_CARETLOCATION = "PROP_CARETLOCATION";
    public static final String PROP_MARKLOCATION = "PROP_MARKLOCATION";

    private static final Logger LOG = Logger.getLogger(HexEditor.class.getName());

    protected ByteBuffer buf;
    protected long caretLocation;
    protected int cols = 16;
    protected int eof;
    protected long markLocation;
    protected long offset;
    protected final transient PropertyChangeSupport propertyChangeSupport = new java.beans.PropertyChangeSupport(this);
    protected RandomAccessFile rf;
    protected int rows = 16;
    protected boolean selecting;
    private final List<Selection> tags = new LinkedList<Selection>();
    protected final Terminal termData, termCalc, termHeader, termLines, termText;
    protected final transient VetoableChangeSupport vetoableChangeSupport = new java.beans.VetoableChangeSupport(this);

    public HexEditor() {
        termData = new Terminal(cols * 3, rows);
        termData.xPos = 9;
        termData.yPos = 1;

        termText = new Terminal(cols, rows);
        termText.xPos = 9 + (cols * 3);
        termText.yPos = 1;

        termCalc = new Terminal(28, 6);
        termCalc.yPos = 1 + rows + 1;

        termHeader = new Terminal(3 * cols - 1, 1);
        termHeader.xPos = 9;
        initColumns();

        termLines = new Terminal(8, rows);
        termLines.yPos = 1;
        Arrays.fill(termLines.fgBuf, Color.GREEN);
        Arrays.fill(termLines.bgBuf, Color.DARK_GRAY);

        this.setBackground(Color.BLACK);

        super.add(termData, termText, termLines, termHeader, termCalc);

        this.addKeyListener(this);
        this.addMouseMotionListener(this);
        this.addMouseListener(this);
        this.addMouseWheelListener(this);
        this.vetoableChangeSupport.addVetoableChangeListener(PROP_CARETLOCATION, new VetoableChangeListener() {
            @Override
            public void vetoableChange(PropertyChangeEvent evt) throws PropertyVetoException {
                long v = (Long) evt.getNewValue();
                if (v < 0 || v > eof) {
                    throw new PropertyVetoException("Caret would be out of bounds", evt);
                }
            }
        });
        this.propertyChangeSupport.addPropertyChangeListener(PROP_CARETLOCATION, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                long oldPos = (Long) evt.getOldValue();
                long newPos = (Long) evt.getNewValue();
                if (newPos < offset) { // on previous page
                    seek(offset - (rows * cols));
                } else if (newPos >= offset + (rows * cols)) { // on next page
                    seek(offset + (rows * cols));
                }

                if (selecting) {
//                    this.repaint(calcPolygon(this.getMarkLocation(), oldPos).getBounds());
//                    this.repaint(calcPolygon(this.getMarkLocation(), newPos).getBounds());
                } else {
                    try {
                        setMarkLocation(newPos);
                    } catch (PropertyVetoException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
//                    this.repaint(getCellRect(oldPos));
//                    this.repaint(getCellRect(newPos));
                }
            }
        });
        this.setFocusable(true);

        reset();
    }

    public void update() {
        updateRows();

        updateOffset();

        if (buf == null) {
            return;
        }

        updateData();

        try {
            updateStats();
        } catch (BufferUnderflowException bue) {

        }
    }

    public void initColumns() {
        Arrays.fill(termHeader.bgBuf, Color.WHITE);
        Arrays.fill(termHeader.fgBuf, Color.BLACK);

        StringBuilder sb = new StringBuilder(cols * 3);
        for (int i = 0; i < cols; i++) {
            sb.append(String.format(" %02X", i & 0xFF));
        }
        termHeader.position(0, 0);
        termHeader.write(sb.substring(1));
    }

    public void updateRows() {
        for (int i = 0; i < rows; i++) {
            String address = String.format("%08X", (i * cols + offset) & 0xFFFFFFFF);
            termLines.position(0, i);
            termLines.write(address);
        }
    }

    public void updateOffset() {
        if (rf == null) {
            return;
        }
        try {
            rf.seek(offset & 0xFFFFFFFF);
            byte[] array = new byte[(int) Math.min(cols * rows, rf.length() - offset)];
            rf.read(array);
            buf = ByteBuffer.wrap(array);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    public void updateData() {
        termData.clear();
        termText.clear();

        int i = 0;
        byte b[] = new byte[cols];
        while (buf.hasRemaining()) {
            int read = Math.min(buf.remaining(), b.length);
            buf.get(b, 0, read);

            StringBuilder sb = new StringBuilder(read * 3);
            for (int s = 0; s < read; s++) {
                sb.append(String.format(" %02X", (b[s] & 0xFF) & 0xFFFFF));
            }
            termData.position(0, i);
            termData.write(sb.substring(1));

            StringBuilder sb2 = new StringBuilder(read);
            for (int s = 0; s < read; s++) {
                sb2.append(Utils.displayChar(b[s] & 0xFF));
            }
            termText.position(0, i);
            termText.write(sb2.toString());

            if (++i >= rows) {
                break;
            }
        }
    }

    public void updateStats() {
        int pos = (int) (getCaretLocation() - offset);
        termCalc.clear();

        if (pos > buf.limit() || pos < 0) {
            return;
        }

        buf.position(pos);
        pos = buf.position();

        int[] idx = {0, 6, 18};
        int l = 0;

        termCalc.position(idx[0], l);
        termCalc.write("   8");
        termCalc.position(idx[0], l + 1);
        termCalc.write("±  8");
        termCalc.position(idx[0], l + 2);
        termCalc.write("  16");
        termCalc.position(idx[0], l + 3);
        termCalc.write("± 16");
        termCalc.position(idx[0], l + 4);
        termCalc.write("  32");
        termCalc.position(idx[0], l + 5);
        termCalc.write("± 32");

        long v;

        // byte
        v = buf.get();
        termCalc.position(idx[1], l);
        termCalc.write(v & 0xFF);
        termCalc.position(idx[1] + (v < 0 ? -1 : 0), l + 1);
        termCalc.write(v);
        buf.position(pos);

        // short
        buf.order(ByteOrder.LITTLE_ENDIAN);
        v = buf.getShort();
        termCalc.position(idx[1], l + 2);
        termCalc.write(v & 0xFFFF);
        termCalc.position(idx[1] + (v < 0 ? -1 : 0), l + 3);
        termCalc.write(v);
        buf.position(pos);

        buf.order(ByteOrder.BIG_ENDIAN);
        v = buf.getShort();
        termCalc.position(idx[2], l + 2);
        termCalc.write(v & 0xFFFF);
        termCalc.position(idx[2] + (v < 0 ? -1 : 0), l + 3);
        termCalc.write(v);
        buf.position(pos);

        // int
        buf.order(ByteOrder.LITTLE_ENDIAN);
        v = buf.getInt();
        termCalc.position(idx[1], l + 4);
        termCalc.write(v & 0xFFFFFFFFL);
        termCalc.position(idx[1] + (v < 0 ? -1 : 0), l + 5);
        termCalc.write(v);
        buf.position(pos);

        buf.order(ByteOrder.BIG_ENDIAN);
        v = buf.getInt();
        termCalc.position(idx[2], l + 4);
        termCalc.write(v & 0xFFFFFFFFL);
        termCalc.position(idx[2] + (v < 0 ? -1 : 0), l + 5);
        termCalc.write(v);
        buf.position(pos);
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        try {
            boolean ignore = false;
            switch (e.getKeyCode()) {
                case KeyEvent.VK_UP:
                    setCaretLocation(getCaretLocation() - cols);
                    break;
                case KeyEvent.VK_DOWN:
                    setCaretLocation(Math.min(getCaretLocation() + cols, eof));
                    break;
                case KeyEvent.VK_LEFT:
                    setCaretLocation(getCaretLocation() - 1);
                    break;
                case KeyEvent.VK_RIGHT:
                    setCaretLocation(getCaretLocation() + 1);
                    break;
                case KeyEvent.VK_SHIFT:
                    selecting = true;
                    break;
                case KeyEvent.VK_HOME:
                    if (e.isControlDown()) {
                        seek(0);
                        setCaretLocation(0);
                    } else {
                        setCaretLocation(getCaretLocation() - (getCaretLocation() % cols));
                    }
                    break;
                case KeyEvent.VK_END:
                    if (e.isControlDown()) {
                        seek(((eof + cols - 1) / cols * cols) - (cols * rows));
                        setCaretLocation(((eof) % cols) + (cols * (rows - 1)));
                    } else {
                        setCaretLocation(Math.min(getCaretLocation() + (cols - 1 - getCaretLocation() % cols), eof));
                    }
                    break;
                case KeyEvent.VK_PAGE_DOWN:
                    skip(cols);
                    break;
                case KeyEvent.VK_PAGE_UP:
                    skip(-cols);
                    break;
                case KeyEvent.VK_ENTER:
                    tags.add(new Selection(getMarkLocation(), getCaretLocation(), Color.RED));
                    break;
                default:
                    ignore = true;
                    break;
            }
            if (!ignore) {
                update();
                repaint();
            }
        } catch (PropertyVetoException ex) {
            LOG.log(Level.FINER, null, ex);
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

    @Override
    public void mouseDragged(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            int c;
            if ((c = termData.viewToCell(e.getPoint())) >= 0) {
                int i = (c + 1) / 3;
                try {
                    this.setCaretLocation(i + offset);
                } catch (PropertyVetoException pve) {
                }
            }
            if ((c = termText.viewToCell(e.getPoint())) >= 0) {
                try {
                    this.setCaretLocation(c + offset);
                } catch (PropertyVetoException pve) {
                }
            }
            update();
            repaint();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        if (SwingUtilities.isLeftMouseButton(e)) {
            int c;
            if ((c = termData.viewToCell(e.getPoint())) >= 0) {
                int i = (c + 1) / 3;
                try {
                    this.setCaretLocation(i + offset);
                } catch (PropertyVetoException ex) {
                }
            }
            if ((c = termText.viewToCell(e.getPoint())) >= 0) {
                try {
                    this.setCaretLocation(c + offset);
                } catch (PropertyVetoException ex) {
                }
            }
            update();
            repaint();
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

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
            skip(e.getUnitsToScroll() * cols);
            update();
            repaint();
        }
    }

    protected void seek(long seek) {
        offset = Math.max(Math.min(seek, eof), 0);
    }

    protected void skip(long delta) {
        seek(offset + delta);
    }

    @Override
    public void paint(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        super.paint(g);

        Selection sel;
        for (int i = 0; i < tags.size() + 1; i++) {
            if (i == tags.size()) {
                sel = new Selection(this.getMarkLocation(), this.getCaretLocation(), Color.RED);
            } else {
                sel = tags.get(i);
            }
            g.setColor(sel.getColor());
            if (sel.getMark() >= 0) {
                Polygon p = calcPolygon(termData, sel.getMark(), sel.getCaret(), 2, 1);
                g.drawPolygon(p);

                p = calcPolygon(termText, sel.getMark(), sel.getCaret(), 1, 0);
                g.drawPolygon(p);
            }
        }

        long markLoc = getMarkLocation();
        if (markLoc >= offset && markLoc < offset + (cols * rows)) {
            g.setColor(Color.YELLOW);
            g.draw(getCellRect(termData, markLoc, 2, 1));
            g.draw(getCellRect(termText, markLoc, 1, 0));
        }

        long caretLoc = getCaretLocation();
        if (caretLoc >= offset && caretLoc < offset + (cols * rows)) {
            g.setColor(Color.WHITE);
            g.draw(getCellRect(termData, caretLoc, 2, 1));
            g.draw(getCellRect(termText, caretLoc, 1, 0));
        }
    }

    protected Rectangle getCellRect(Terminal term, long address, int width, int spacing) {
        address -= offset;
        Point p = term.cellToView(address * (width + spacing));
        return new Rectangle(p.x, p.y, m.width * width, m.height);
    }

    protected Polygon calcPolygon(Terminal term, long markIdx, long caretIdx, int width, int spacing) {
        caretIdx -= offset;
        long caretCol = (caretIdx % cols);
        long caretRow = (caretIdx / cols);
        if (caretIdx < 0) {
            caretIdx = 0;
        } else if (caretIdx > (cols * rows)) {
            caretIdx = (cols * rows) - 1;
        }
        Point caretPos = term.cellToView(caretIdx * (width + spacing));
        caretPos.translate(-term.xPos * m.width, -term.yPos * m.height);

        markIdx -= offset;
        long markCol = (markIdx % cols);
        long markRow = (markIdx / cols);
        if (markIdx < 0) {
            markIdx = 0;
        } else if (markIdx > (cols * rows)) {
            markIdx = (cols * rows) - 1;
        }
        Point markPos = term.cellToView(markIdx * (width + spacing));
        markPos.translate(-term.xPos * m.width, -term.yPos * m.height);

        Point rel = new Point((int) (caretIdx - markIdx), (int) (caretRow - markRow));

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
        p.translate(term.xPos * m.width, term.yPos * m.height);
        return p;
    }

    /**
     * @return the caretLocation
     */
    public long getCaretLocation() {
        return caretLocation;
    }

    /**
     * @param caretLocation the caretLocation to set
     * @throws java.beans.PropertyVetoException
     */
    public void setCaretLocation(long caretLocation) throws PropertyVetoException {
        long oldCaretLocation = this.caretLocation;
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
    public long getMarkLocation() {
        return markLocation;
    }

    /**
     * @param markLocation the markLocation to set
     * @throws java.beans.PropertyVetoException
     */
    public void setMarkLocation(long markLocation) throws PropertyVetoException {
        long oldMarkLocation = this.markLocation;
        if (oldMarkLocation == markLocation) {
            return;
        }
        vetoableChangeSupport.fireVetoableChange(PROP_MARKLOCATION, oldMarkLocation, markLocation);
        this.markLocation = markLocation;
        propertyChangeSupport.firePropertyChange(PROP_MARKLOCATION, oldMarkLocation, markLocation);
    }

    public void setData(RandomAccessFile rf) {
        reset();
        this.rf = rf;
        try {
            this.eof = (int) rf.length() - 1;
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        update();
        repaint();
    }

    private void reset() {
        markLocation = -1;
        caretLocation = 0;
        offset = 0;
    }
}
