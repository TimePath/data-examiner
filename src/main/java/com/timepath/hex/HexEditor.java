package com.timepath.hex;

import com.timepath.curses.Multiplexer;
import com.timepath.curses.Terminal;
import com.timepath.io.BitBuffer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.io.File;
import java.io.FileNotFoundException;
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

public class HexEditor extends Multiplexer
        implements KeyListener, MouseMotionListener, MouseListener, MouseWheelListener {

    protected static final String PROP_CARETLOCATION = "PROP_CARETLOCATION";
    protected static final String PROP_MARKLOCATION = "PROP_MARKLOCATION";
    private static final Logger LOG = Logger.getLogger(HexEditor.class.getName());
    protected final List<Selection> tags = new LinkedList<>();
    protected final Terminal termData;
    protected final Terminal termText;
    protected final Terminal termLines;
    protected final Terminal termHeader;
    protected final Terminal termShift;
    protected final Terminal termCalc;
    protected PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
    protected VetoableChangeSupport vetoableChangeSupport = new VetoableChangeSupport(this);
    protected BitBuffer bitBuffer;
    protected ByteBuffer sourceBuf;
    protected long caretLocation;
    protected int cols = 16;
    protected int limit;
    protected long markLocation;
    protected long offset;
    protected RandomAccessFile sourceRAF;
    protected int rows = 16;
    @SuppressWarnings("BooleanVariableAlwaysNegated")
    protected boolean selecting;
    protected int bitShift;

    public HexEditor() {
        termData = new Terminal(cols * 3, rows);
        termData.xPos = 9;
        termData.yPos = 1;
        termText = new Terminal(cols, rows);
        termText.xPos = 9 + cols * 3;
        termText.yPos = 1;
        termCalc = new Terminal(54, 6);
        termCalc.yPos = 1 + rows + 1;
        termHeader = new Terminal((3 * cols) - 1, 1);
        termHeader.xPos = 9;
        initColumns();
        termLines = new Terminal(8, rows);
        termLines.yPos = 1;
        Arrays.fill(termLines.fgBuf, Color.GREEN);
        Arrays.fill(termLines.bgBuf, Color.DARK_GRAY);
        termShift = new Terminal(1, 1);
        Arrays.fill(termShift.fgBuf, Color.CYAN);
        Arrays.fill(termShift.bgBuf, Color.BLACK);
        setBackground(Color.BLACK);
        add(termData, termText, termLines, termHeader, termShift, termCalc);
        addKeyListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);
        addMouseWheelListener(this);
        vetoableChangeSupport.addVetoableChangeListener(PROP_CARETLOCATION, new VetoableChangeListener() {
            @Override
            public void vetoableChange(PropertyChangeEvent evt) throws PropertyVetoException {
                long v = (Long) evt.getNewValue();
                if ((v < 0) || (v > limit)) {
                    throw new PropertyVetoException("Caret would be out of bounds", evt);
                }
            }
        });
        propertyChangeSupport.addPropertyChangeListener(PROP_CARETLOCATION, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                long newPos = (Long) evt.getNewValue();
                if (newPos < offset) { // on previous page
                    skip(-(cols * rows));
                } else if (newPos >= (offset + (cols * rows))) { // on next page
                    skip(cols * rows);
                }
                if (!selecting) {
                    try {
                        setMarkLocation(newPos);
                    } catch (PropertyVetoException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
            }
        });
        setFocusable(true);
        reset();
    }

    public static RandomAccessFile mapFile(File file) throws FileNotFoundException {
        return new RandomAccessFile(file, "rw");
    }

    protected void initColumns() {
        Arrays.fill(termHeader.bgBuf, Color.WHITE);
        Arrays.fill(termHeader.fgBuf, Color.BLACK);
        StringBuilder sb = new StringBuilder(cols * 3);
        for (int i = 0; i < cols; i++) {
            sb.append(String.format(" %02X", i & 0xFF));
        }
        termHeader.position(0, 0);
        termHeader.write(sb.substring(1));
    }

    public void skip(long delta) {
        seek(offset + delta);
    }

    public void seek(long seek) {
        seek = Math.max(Math.min(seek, limit - (limit % cols)), 0);
        if (sourceRAF != null) {
            try {
                sourceRAF.seek(seek);
                byte[] array = new byte[(int) Math.min(cols * rows, sourceRAF.length() - seek)];
                sourceRAF.read(array);
                bitBuffer = new BitBuffer(ByteBuffer.wrap(array));
                bitBuffer.position(0, bitShift);
                offset = seek;
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        } else if (sourceBuf != null) {
            sourceBuf.position((int) seek);
            bitBuffer = new BitBuffer(getSlice(sourceBuf));
            bitBuffer.position(0, bitShift);
            offset = seek;
        }
    }

    protected ByteBuffer getSlice(ByteBuffer source) {
        return getSlice(source, source.remaining());
    }

    protected ByteBuffer getSlice(ByteBuffer source, int length) {
        int originalLimit = source.limit();
        source.limit(source.position() + length);
        ByteBuffer sub = source.slice();
        source.position(source.limit());
        source.limit(originalLimit);
        sub.order(ByteOrder.LITTLE_ENDIAN);
        return sub;
    }

    protected void reset() {
        markLocation = -1;
        caretLocation = 0;
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
        if (oldMarkLocation == markLocation) return;
        vetoableChangeSupport.fireVetoableChange(PROP_MARKLOCATION, oldMarkLocation, markLocation);
        this.markLocation = markLocation;
        propertyChangeSupport.firePropertyChange(PROP_MARKLOCATION, oldMarkLocation, markLocation);
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        try {
            boolean update = true;
            switch (e.getKeyCode()) {
                case KeyEvent.VK_UP:
                    setCaretLocation(caretLocation - cols);
                    break;
                case KeyEvent.VK_DOWN:
                    setCaretLocation(Math.min(caretLocation + cols, limit));
                    break;
                case KeyEvent.VK_LEFT:
                    setCaretLocation(caretLocation - 1);
                    break;
                case KeyEvent.VK_RIGHT:
                    setCaretLocation(caretLocation + 1);
                    break;
                case KeyEvent.VK_SHIFT:
                    selecting = true;
                    break;
                case KeyEvent.VK_HOME:
                    if (e.isControlDown()) {
                        seek(0);
                    } else {
                        setCaretLocation(caretLocation - (caretLocation % cols));
                    }
                    break;
                case KeyEvent.VK_END:
                    if (e.isControlDown()) {
                        int rowsTotal = ((limit + cols) - 1) / rows;
                        seek((cols * rowsTotal) - (cols * rows));
                    } else {
                        setCaretLocation(Math.min((caretLocation + cols) - 1 - (caretLocation % cols), limit));
                    }
                    break;
                case KeyEvent.VK_PAGE_DOWN:
                    skip(cols);
                    break;
                case KeyEvent.VK_PAGE_UP:
                    skip(-cols);
                    break;
                case KeyEvent.VK_ENTER:
                    tags.add(new Selection(markLocation, caretLocation, Color.RED));
                    break;
                default:
                    update = false;
                    break;
            }
            if (update) update();
        } catch (PropertyVetoException ex) {
            LOG.log(Level.FINER, null, ex);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            selecting = false;
        }
    }

    /**
     * @return the caretLocation
     */
    long getCaretLocation() {
        return caretLocation;
    }

    /**
     * @param caretLocation the caretLocation to set
     * @throws java.beans.PropertyVetoException
     */
    public void setCaretLocation(long caretLocation) throws PropertyVetoException {
        long oldCaretLocation = this.caretLocation;
        if (oldCaretLocation == caretLocation) return;
        vetoableChangeSupport.fireVetoableChange(PROP_CARETLOCATION, oldCaretLocation, caretLocation);
        this.caretLocation = caretLocation;
        propertyChangeSupport.firePropertyChange(PROP_CARETLOCATION, oldCaretLocation, caretLocation);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            mousePressed(e);
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
            int cell;
            if ((cell = termData.viewToCell(e.getPoint())) >= 0) {
                if (((cell + 1) % (cols * 3)) != 0) {
                    int i = (cell + 1) / 3;
                    try {
                        setCaretLocation(offset + i);
                    } catch (PropertyVetoException ignored) {
                    }
                }
            }
            if ((cell = termText.viewToCell(e.getPoint())) >= 0) {
                try {
                    setCaretLocation(offset + cell);
                } catch (PropertyVetoException ignored) {
                }
            }
            update();
        }
    }

    public void update() {
        updateRows();
        termShift.position(0, 0);
        termShift.write(bitShift);
        updateData();
        try {
            updateStats();
        } catch (BufferUnderflowException ignored) {
        }
        repaint();
    }

    protected void updateRows() {
        for (int i = 0; i < rows; i++) {
            String address = String.format("%08X", (i * cols) + offset);
            termLines.position(0, i);
            termLines.write(address);
        }
    }

    protected void updateData() {
        termData.clear();
        termText.clear();
        if (bitBuffer == null) return;
        bitBuffer.position(0, bitShift);
        int row = 0;
        byte[] bytes = new byte[cols];
        while (bitBuffer.hasRemaining()) {
            int read = Math.min(bitBuffer.remaining(), bytes.length);
            bitBuffer.get(bytes, 0, read);
            StringBuilder sb = new StringBuilder(read * 3);
            for (int i = 0; i < read; i++) {
                sb.append(String.format(" %02X", bytes[i] & 0xFF));
            }
            termData.position(0, row);
            termData.write(sb.substring(1));
            StringBuilder sb2 = new StringBuilder(read);
            for (int i = 0; i < read; i++) {
                sb2.append(displayChar(bytes[i] & 0xFF));
            }
            termText.position(0, row);
            termText.write(sb2.toString());
            if (++row >= rows) break;
        }
    }

    protected String displayChar(int i) {
        return String.valueOf((Character.isWhitespace(i) || Character.isISOControl(i)) ? '.' : (char) i);
    }

    protected void updateStats() {
        int pos = (int) (caretLocation - offset);
        termCalc.clear();
        if ((bitBuffer == null) || (pos > bitBuffer.limit()) || (pos < 0)) return;
        bitBuffer.position(pos, bitShift);
        byte[] temp = new byte[Math.min(bitBuffer.remaining(), 4)];
        bitBuffer.get(temp);
        bitBuffer.position(pos, bitShift);
        ByteBuffer calcBuf = ByteBuffer.wrap(temp);
        int[] idx = {0, 6, 18};
        int yOff = 0;
        termCalc.position(idx[0], yOff);
        termCalc.write("   8");
        termCalc.position(idx[0], yOff + 1);
        termCalc.write("±  8");
        termCalc.position(idx[0], yOff + 2);
        termCalc.write("  16");
        termCalc.position(idx[0], yOff + 3);
        termCalc.write("± 16");
        termCalc.position(idx[0], yOff + 4);
        termCalc.write("  32");
        termCalc.position(idx[0], yOff + 5);
        termCalc.write("± 32");
        // byte
        calcBuf.position(0);
        long value = calcBuf.get();
        termCalc.position(idx[1], yOff);
        termCalc.write(value & 0xFF);
        termCalc.position(idx[1] + ((value < 0) ? -1 : 0), yOff + 1);
        termCalc.write(value);
        // binary
        for (int i = 0; i < temp.length; i++) {
            termCalc.position(idx[2] + (i * 9), yOff);
            termCalc.write(new StringBuilder(binaryDump(temp[i] & 0xFF)));
        }
        // short
        calcBuf.position(0);
        calcBuf.order(ByteOrder.LITTLE_ENDIAN);
        value = calcBuf.getShort();
        termCalc.position(idx[1], yOff + 2);
        termCalc.write(value & 0xFFFF);
        termCalc.position(idx[1] + ((value < 0) ? -1 : 0), yOff + 3);
        termCalc.write(value);
        calcBuf.position(0);
        calcBuf.order(ByteOrder.BIG_ENDIAN);
        value = calcBuf.getShort();
        termCalc.position(idx[2], yOff + 2);
        termCalc.write(value & 0xFFFF);
        termCalc.position(idx[2] + ((value < 0) ? -1 : 0), yOff + 3);
        termCalc.write(value);
        // int
        calcBuf.position(0);
        calcBuf.order(ByteOrder.LITTLE_ENDIAN);
        value = calcBuf.getInt();
        termCalc.position(idx[1], yOff + 4);
        termCalc.write(value & 0xFFFFFFFFL);
        termCalc.position(idx[1] + ((value < 0) ? -1 : 0), yOff + 5);
        termCalc.write(value);
        calcBuf.position(0);
        calcBuf.order(ByteOrder.BIG_ENDIAN);
        value = calcBuf.getInt();
        termCalc.position(idx[2], yOff + 4);
        termCalc.write(value & 0xFFFFFFFFL);
        termCalc.position(idx[2] + ((value < 0) ? -1 : 0), yOff + 5);
        termCalc.write(value);
    }

    protected String binaryDump(long l) {
        return String.format("%8s", Long.toBinaryString(l)).replace(' ', '0');
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

    public void setBitShift(int bitShift) {
        if ((bitShift < 0) || (bitShift >= 8)) { // Shifting off current byte
            try {
                setCaretLocation(caretLocation + Math.round(Math.signum(bitShift)));
            } catch (PropertyVetoException ignored) {
            }
            // Bring back into acceptable range
            bitShift += 8;
            bitShift %= 8;
        }
        this.bitShift = bitShift;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
            if (e.isControlDown()) {
                if (e.getWheelRotation() > 0) {
                    setBitShift(bitShift + 1);
                } else if (e.getWheelRotation() < 0) {
                    setBitShift(bitShift - 1);
                }
            } else {
                skip(e.getUnitsToScroll() * cols);
            }
            update();
        }
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2 = (Graphics2D) g;
        for (int i = 0; i < (tags.size() + 1); i++) {
            Selection sel = (i == tags.size()) ? new Selection(markLocation, caretLocation, Color.RED) : tags.get(i);
            g2.setColor(sel.getColor());
            if (sel.getMark() >= 0) {
                Polygon p = calcPolygon(termData, sel.getMark(), sel.getCaret(), 2, 1);
                g2.drawPolygon(p);
                p = calcPolygon(termText, sel.getMark(), sel.getCaret(), 1, 0);
                g2.drawPolygon(p);
            }
        }
        long markLoc = markLocation;
        if ((markLoc >= offset) && (markLoc < (offset + (cols * rows)))) {
            g2.setColor(Color.YELLOW);
            g2.draw(getCellRect(termData, markLoc, 2, 1));
            g2.draw(getCellRect(termText, markLoc, 1, 0));
        }
        long caretLoc = caretLocation;
        if ((caretLoc >= offset) && (caretLoc < (offset + (cols * rows)))) {
            g2.setColor(Color.WHITE);
            g2.draw(getCellRect(termData, caretLoc, 2, 1));
            g2.draw(getCellRect(termText, caretLoc, 1, 0));
        }
    }

    protected Shape getCellRect(Terminal term, long address, int width, int spacing) {
        address -= offset;
        Point p = term.cellToView(address * (width + spacing));
        return new Rectangle(p.x, p.y, metrics.width * width, metrics.height);
    }

    protected Polygon calcPolygon(Terminal term, long markIdx, long caretIdx, int width, int spacing) {
        caretIdx -= offset;
        long caretRow = caretIdx / cols;
        if (caretIdx < 0) {
            caretIdx = 0;
        } else if (caretIdx > (cols * rows)) {
            caretIdx = cols * rows - 1;
        }
        Point caretPos = term.cellToView(caretIdx * (width + spacing));
        caretPos.translate(-term.xPos * metrics.width, -term.yPos * metrics.height);
        markIdx -= offset;
        long markRow = markIdx / cols;
        if (markIdx < 0) {
            markIdx = 0;
        } else if (markIdx > (cols * rows)) {
            markIdx = cols * rows - 1;
        }
        Point markPos = term.cellToView(markIdx * (width + spacing));
        markPos.translate(-term.xPos * metrics.width, -term.yPos * metrics.height);
        Point rel = new Point((int) (caretIdx - markIdx), (int) (caretRow - markRow));
        if (rel.x >= 0) { // further right
            caretPos.x += metrics.width * width;
        } else {
            markPos.x += metrics.width * width;
        }
        if (rel.y >= 0) { // further down
            caretPos.y += metrics.height;
        } else {
            markPos.y += metrics.height;
        }
        Polygon p = new Polygon();
        p.addPoint(markPos.x, markPos.y);
        if (rel.y > 0) {
            p.addPoint(((cols * (width + spacing)) - spacing) * metrics.width, markPos.y);
            p.addPoint(((cols * (width + spacing)) - spacing) * metrics.width, caretPos.y - metrics.height);
            p.addPoint(caretPos.x, caretPos.y - metrics.height);
        } else if (rel.y < 0) {
            p.addPoint(0, markPos.y);
            p.addPoint(0, caretPos.y + metrics.height);
            p.addPoint(caretPos.x, caretPos.y + metrics.height);
        } else {
            p.addPoint(caretPos.x, markPos.y);
        }
        p.addPoint(caretPos.x, caretPos.y);
        if (rel.y > 0) {
            p.addPoint(0, caretPos.y);
            p.addPoint(0, markPos.y + metrics.height);
            p.addPoint(markPos.x, markPos.y + metrics.height);
        } else if (rel.y < 0) {
            p.addPoint(((cols * (width + spacing)) - spacing) * metrics.width, caretPos.y);
            p.addPoint(((cols * (width + spacing)) - spacing) * metrics.width, markPos.y - metrics.height);
            p.addPoint(markPos.x, markPos.y - metrics.height);
        } else {
            p.addPoint(markPos.x, caretPos.y);
        }
        p.translate(term.xPos * metrics.width, term.yPos * metrics.height);
        return p;
    }

    public void setData(RandomAccessFile rf) {
        reset();
        sourceRAF = rf;
        if (rf != null) {
            try {
                limit = (int) rf.length() - 1;
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        seek(0);
        update();
    }

    public void setData(ByteBuffer buf) {
        reset();
        sourceBuf = buf;
        if (buf != null) {
            bitBuffer = new BitBuffer(buf);
            limit = bitBuffer.capacity() - 1;
        }
        seek(0);
        update();
    }
}
