package com.timepath.hex

import com.timepath.DataUtils
import com.timepath.curses.Multiplexer
import com.timepath.curses.Terminal
import com.timepath.io.BitBuffer

import javax.swing.*
import java.awt.*
import java.awt.event.*
import java.beans.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.properties.Delegates

public data class Selection(var mark: Long, var caret: Long, var color: Color?)

public class HexEditor : Multiplexer(), KeyListener, MouseMotionListener, MouseListener, MouseWheelListener {
    protected val tags: MutableList<Selection> = LinkedList()
    protected val termData: Terminal
    protected val termText: Terminal
    protected val termLines: Terminal
    protected val termHeader: Terminal
    protected val termShift: Terminal
    protected val termCalc: Terminal
    protected var propertyChangeSupport: PropertyChangeSupport = PropertyChangeSupport(this)
    protected var vetoableChangeSupport: VetoableChangeSupport = VetoableChangeSupport(this)
    protected var bitBuffer: BitBuffer? = null
    protected var sourceBuf: ByteBuffer? = null
    var caretLocation: Long = 0
        set(caretLocation) {
            val oldCaretLocation = $caretLocation
            if (oldCaretLocation == caretLocation) return
            vetoableChangeSupport.fireVetoableChange(PROP_CARETLOCATION, oldCaretLocation, caretLocation)
            $caretLocation = caretLocation
            propertyChangeSupport.firePropertyChange(PROP_CARETLOCATION, oldCaretLocation, caretLocation)
        }
    protected var cols: Int = 16
    protected var limit: Int = 0
    public var markLocation: Long = 0
        set(markLocation) {
            val oldMarkLocation = $markLocation
            if (oldMarkLocation == markLocation) return
            vetoableChangeSupport.fireVetoableChange(PROP_MARKLOCATION, oldMarkLocation, markLocation)
            $markLocation = markLocation
            propertyChangeSupport.firePropertyChange(PROP_MARKLOCATION, oldMarkLocation, markLocation)
        }
    protected var offset: Long = 0
    protected var sourceRAF: RandomAccessFile? = null
    protected var rows: Int = 16
    SuppressWarnings("BooleanVariableAlwaysNegated")
    protected var selecting: Boolean = false
    var bitShift: Int = 0
        set(value) {
            var value = value
            if ((value < 0) || (value >= 8)) {
                // Shifting off current byte
                try {
                    caretLocation += Math.round(Math.signum(value.toFloat())).toLong()
                } catch (ignored: PropertyVetoException) {
                }

                // Bring back into acceptable range
                value += 8
                value %= 8
            }
            $bitShift = value
        }

    {
        termData = Terminal(cols * 3, rows)
        termData.xPos = 9
        termData.yPos = 1
        termText = Terminal(cols, rows)
        termText.xPos = 9 + cols * 3
        termText.yPos = 1
        termCalc = Terminal(54, 6)
        termCalc.yPos = 1 + rows + 1
        termHeader = Terminal((3 * cols) - 1, 1)
        termHeader.xPos = 9
        initColumns()
        termLines = Terminal(8, rows)
        termLines.yPos = 1
        Arrays.fill(termLines.fgBuf, Color.GREEN)
        Arrays.fill(termLines.bgBuf, Color.DARK_GRAY)
        termShift = Terminal(1, 1)
        Arrays.fill(termShift.fgBuf, Color.CYAN)
        Arrays.fill(termShift.bgBuf, Color.BLACK)
        setBackground(Color.BLACK)
        add(termData, termText, termLines, termHeader, termShift, termCalc)
        addKeyListener(this)
        addMouseMotionListener(this)
        addMouseListener(this)
        addMouseWheelListener(this)
        vetoableChangeSupport.addVetoableChangeListener(PROP_CARETLOCATION, object : VetoableChangeListener {
            throws(javaClass<PropertyVetoException>())
            override fun vetoableChange(evt: PropertyChangeEvent) {
                val v = evt.getNewValue() as Long
                if ((v < 0) || (v > limit)) {
                    throw PropertyVetoException("Caret would be out of bounds", evt)
                }
            }
        })
        propertyChangeSupport.addPropertyChangeListener(PROP_CARETLOCATION, object : PropertyChangeListener {
            override fun propertyChange(evt: PropertyChangeEvent) {
                val newPos = evt.getNewValue() as Long
                if (newPos < offset) {
                    // on previous page
                    skip((-(cols * rows)).toLong())
                } else if (newPos >= (offset + (cols * rows).toLong())) {
                    // on next page
                    skip((cols * rows).toLong())
                }
                if (!selecting) {
                    try {
                        markLocation = newPos
                    } catch (ex: PropertyVetoException) {
                        LOG.log(Level.SEVERE, null, ex)
                    }

                }
            }
        })
        setFocusable(true)
        reset()
    }

    protected fun initColumns() {
        Arrays.fill(termHeader.bgBuf, Color.WHITE)
        Arrays.fill(termHeader.fgBuf, Color.BLACK)
        val sb = StringBuilder(cols * 3)
        for (i in 0..cols - 1) {
            sb.append(java.lang.String.format(" %02X", i and 255))
        }
        termHeader.position(0, 0)
        termHeader.write(sb.substring(1))
    }

    public fun skip(delta: Long) {
        seek(offset + delta)
    }

    public fun seek(seek: Long) {
        var seek = seek
        seek = Math.max(Math.min(seek, (limit - (limit % cols)).toLong()), 0)
        if (sourceRAF != null) {
            try {
                sourceRAF!!.seek(seek)
                val array = ByteArray(Math.min((cols * rows).toLong(), sourceRAF!!.length() - seek).toInt())
                sourceRAF!!.read(array)
                bitBuffer = BitBuffer(ByteBuffer.wrap(array))
                bitBuffer!!.position(0, bitShift)
                offset = seek
            } catch (ex: IOException) {
                LOG.log(Level.SEVERE, null, ex)
            }

        } else if (sourceBuf != null) {
            sourceBuf!!.position(seek.toInt())
            bitBuffer = BitBuffer(DataUtils.getSlice(sourceBuf, sourceBuf!!.remaining()))
            bitBuffer!!.position(0, bitShift)
            offset = seek
        }
    }

    protected fun reset() {
        markLocation = (-1).toLong()
        caretLocation = 0
    }

    override fun keyTyped(e: KeyEvent) {
    }

    override fun keyPressed(e: KeyEvent) {
        try {
            var update = true
            when (e.getKeyCode()) {
                KeyEvent.VK_UP -> caretLocation -= cols.toLong()
                KeyEvent.VK_DOWN -> caretLocation = Math.min(caretLocation + cols.toLong(), limit.toLong())
                KeyEvent.VK_LEFT -> caretLocation--
                KeyEvent.VK_RIGHT -> caretLocation++
                KeyEvent.VK_SHIFT -> selecting = true
                KeyEvent.VK_HOME -> if (e.isControlDown()) {
                    seek(0)
                } else {
                    caretLocation -= (caretLocation % cols.toLong())
                }
                KeyEvent.VK_END -> if (e.isControlDown()) {
                    val rowsTotal = ((limit + cols) - 1) / rows
                    seek(((cols * rowsTotal) - (cols * rows)).toLong())
                } else {
                    caretLocation = (Math.min((caretLocation + cols.toLong()) - 1 - (caretLocation % cols.toLong()), limit.toLong()))
                }
                KeyEvent.VK_PAGE_DOWN -> skip(cols.toLong())
                KeyEvent.VK_PAGE_UP -> skip((-cols).toLong())
                KeyEvent.VK_ENTER -> tags.add(Selection(markLocation, caretLocation, Color.RED))
                else -> update = false
            }
            if (update) update()
        } catch (ex: PropertyVetoException) {
            LOG.log(Level.FINER, null, ex)
        }

    }

    override fun keyReleased(e: KeyEvent) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            selecting = false
        }
    }

    override fun mouseDragged(e: MouseEvent) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            mousePressed(e)
        }
    }

    override fun mouseMoved(e: MouseEvent) {
    }

    override fun mouseClicked(e: MouseEvent) {
    }

    override fun mousePressed(e: MouseEvent) {
        requestFocusInWindow()
        if (SwingUtilities.isLeftMouseButton(e)) {
            var cell: Int
            cell = termData.viewToCell(e.getPoint())
            if (cell >= 0) {
                if (((cell + 1) % (cols * 3)) != 0) {
                    val i = (cell + 1) / 3
                    try {
                        caretLocation = (offset + i.toLong())
                    } catch (ignored: PropertyVetoException) {
                    }

                }
            }
            cell = termText.viewToCell(e.getPoint())
            if (cell >= 0) {
                try {
                    caretLocation = (offset + cell.toLong())
                } catch (ignored: PropertyVetoException) {
                }

            }
            update()
        }
    }

    public fun update() {
        updateRows()
        termShift.position(0, 0)
        termShift.write(bitShift)
        updateData()
        try {
            updateStats()
        } catch (ignored: BufferUnderflowException) {
        }

        repaint()
    }

    protected fun updateRows() {
        for (i in 0..rows - 1) {
            val address = java.lang.String.format("%08X", (i * cols).toLong() + offset)
            termLines.position(0, i)
            termLines.write(address)
        }
    }

    protected fun updateData() {
        termData.clear()
        termText.clear()
        if (bitBuffer == null) return
        bitBuffer!!.position(0, bitShift)
        var row = 0
        val bytes = ByteArray(cols)
        while (bitBuffer!!.hasRemaining()) {
            val read = Math.min(bitBuffer!!.remaining(), bytes.size)
            bitBuffer!!.get(bytes, 0, read)
            val sb = StringBuilder(read * 3)
            for (i in 0..read - 1) {
                sb.append(java.lang.String.format(" %02X", bytes[i].toInt() and 255))
            }
            termData.position(0, row)
            termData.write(sb.substring(1))
            val sb2 = StringBuilder(read)
            for (i in 0..read - 1) {
                sb2.append(displayChar(bytes[i].toInt() and 255))
            }
            termText.position(0, row)
            termText.write(sb2.toString())
            if (++row >= rows) break
        }
    }

    protected fun displayChar(i: Int): String {
        return java.lang.String.valueOf(if ((Character.isWhitespace(i) || Character.isISOControl(i))) '.' else i.toChar())
    }

    protected fun updateStats() {
        val pos = (caretLocation - offset).toInt()
        termCalc.clear()
        if ((bitBuffer == null) || (pos > bitBuffer!!.limit()) || (pos < 0)) return
        bitBuffer!!.position(pos, bitShift)
        val temp = ByteArray(Math.min(bitBuffer!!.remaining(), 4))
        bitBuffer!!.get(temp)
        bitBuffer!!.position(pos, bitShift)
        val calcBuf = ByteBuffer.wrap(temp)
        val idx = intArray(0, 6, 18)
        val yOff = 0
        termCalc.position(idx[0], yOff)
        termCalc.write("   8")
        termCalc.position(idx[0], yOff + 1)
        termCalc.write("±  8")
        termCalc.position(idx[0], yOff + 2)
        termCalc.write("  16")
        termCalc.position(idx[0], yOff + 3)
        termCalc.write("± 16")
        termCalc.position(idx[0], yOff + 4)
        termCalc.write("  32")
        termCalc.position(idx[0], yOff + 5)
        termCalc.write("± 32")
        // byte
        calcBuf.position(0)
        var value = calcBuf.get().toLong()
        termCalc.position(idx[1], yOff)
        termCalc.write(value and 255)
        termCalc.position(idx[1] + (if ((value < 0)) -1 else 0), yOff + 1)
        termCalc.write(value)
        // binary
        for (i in temp.indices) {
            termCalc.position(idx[2] + (i * 9), yOff)
            termCalc.write(StringBuilder(binaryDump((temp[i].toInt() and 255).toLong())))
        }
        // short
        calcBuf.position(0)
        calcBuf.order(ByteOrder.LITTLE_ENDIAN)
        value = calcBuf.getShort().toLong()
        termCalc.position(idx[1], yOff + 2)
        termCalc.write(value and 65535)
        termCalc.position(idx[1] + (if ((value < 0)) -1 else 0), yOff + 3)
        termCalc.write(value)
        calcBuf.position(0)
        calcBuf.order(ByteOrder.BIG_ENDIAN)
        value = calcBuf.getShort().toLong()
        termCalc.position(idx[2], yOff + 2)
        termCalc.write(value and 65535)
        termCalc.position(idx[2] + (if ((value < 0)) -1 else 0), yOff + 3)
        termCalc.write(value)
        // int
        calcBuf.position(0)
        calcBuf.order(ByteOrder.LITTLE_ENDIAN)
        value = calcBuf.getInt().toLong()
        termCalc.position(idx[1], yOff + 4)
        termCalc.write(value and 0xFFFFFFFF)
        termCalc.position(idx[1] + (if ((value < 0)) -1 else 0), yOff + 5)
        termCalc.write(value)
        calcBuf.position(0)
        calcBuf.order(ByteOrder.BIG_ENDIAN)
        value = calcBuf.getInt().toLong()
        termCalc.position(idx[2], yOff + 4)
        termCalc.write(value and 0xFFFFFFFF)
        termCalc.position(idx[2] + (if ((value < 0)) -1 else 0), yOff + 5)
        termCalc.write(value)
    }

    protected fun binaryDump(l: Long): String {
        return java.lang.String.format("%8s", java.lang.Long.toBinaryString(l)).replace(' ', '0')
    }

    override fun mouseReleased(e: MouseEvent) {
    }

    override fun mouseEntered(e: MouseEvent) {
    }

    override fun mouseExited(e: MouseEvent) {
    }

    override fun mouseWheelMoved(e: MouseWheelEvent) {
        if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
            if (e.isControlDown()) {
                if (e.getWheelRotation() > 0) {
                    bitShift++
                } else if (e.getWheelRotation() < 0) {
                    bitShift--
                }
            } else {
                skip((e.getUnitsToScroll() * cols).toLong())
            }
            update()
        }
    }

    override fun paint(g: Graphics) {
        super<Multiplexer>.paint(g)
        val g2 = g as Graphics2D
        for (i in 0..(tags.size() + 1) - 1) {
            val sel = if ((i == tags.size())) Selection(markLocation, caretLocation, Color.RED) else tags.get(i)
            g2.setColor(sel.color)
            if (sel.mark >= 0) {
                var p = calcPolygon(termData, sel.mark, sel.caret, 2, 1)
                g2.drawPolygon(p)
                p = calcPolygon(termText, sel.mark, sel.caret, 1, 0)
                g2.drawPolygon(p)
            }
        }
        val markLoc = markLocation
        if ((markLoc >= offset) && (markLoc < (offset + (cols * rows).toLong()))) {
            g2.setColor(Color.YELLOW)
            g2.draw(getCellRect(termData, markLoc, 2, 1))
            g2.draw(getCellRect(termText, markLoc, 1, 0))
        }
        val caretLoc = caretLocation
        if ((caretLoc >= offset) && (caretLoc < (offset + (cols * rows).toLong()))) {
            g2.setColor(Color.WHITE)
            g2.draw(getCellRect(termData, caretLoc, 2, 1))
            g2.draw(getCellRect(termText, caretLoc, 1, 0))
        }
    }

    protected fun getCellRect(term: Terminal, address: Long, width: Int, spacing: Int): Shape {
        var address = address
        address -= offset
        val p = term.cellToView(address * (width + spacing).toLong())
        return Rectangle(p.x, p.y, metrics!!.width * width, metrics!!.height)
    }

    protected fun calcPolygon(term: Terminal, markIdx: Long, caretIdx: Long, width: Int, spacing: Int): Polygon {
        var markIdx = markIdx
        var caretIdx = caretIdx
        caretIdx -= offset
        val caretRow = caretIdx / cols.toLong()
        if (caretIdx < 0) {
            caretIdx = 0
        } else if (caretIdx > (cols * rows)) {
            caretIdx = (cols * rows - 1).toLong()
        }
        val caretPos = term.cellToView(caretIdx * (width + spacing).toLong())
        caretPos.translate(-term.xPos * metrics!!.width, -term.yPos * metrics!!.height)
        markIdx -= offset
        val markRow = markIdx / cols.toLong()
        if (markIdx < 0) {
            markIdx = 0
        } else if (markIdx > (cols * rows)) {
            markIdx = (cols * rows - 1).toLong()
        }
        val markPos = term.cellToView(markIdx * (width + spacing).toLong())
        markPos.translate(-term.xPos * metrics!!.width, -term.yPos * metrics!!.height)
        val rel = Point((caretIdx - markIdx).toInt(), (caretRow - markRow).toInt())
        if (rel.x >= 0) {
            // further right
            caretPos.x += metrics!!.width * width
        } else {
            markPos.x += metrics!!.width * width
        }
        if (rel.y >= 0) {
            // further down
            caretPos.y += metrics!!.height
        } else {
            markPos.y += metrics!!.height
        }
        val p = Polygon()
        p.addPoint(markPos.x, markPos.y)
        if (rel.y > 0) {
            p.addPoint(((cols * (width + spacing)) - spacing) * metrics!!.width, markPos.y)
            p.addPoint(((cols * (width + spacing)) - spacing) * metrics!!.width, caretPos.y - metrics!!.height)
            p.addPoint(caretPos.x, caretPos.y - metrics!!.height)
        } else if (rel.y < 0) {
            p.addPoint(0, markPos.y)
            p.addPoint(0, caretPos.y + metrics!!.height)
            p.addPoint(caretPos.x, caretPos.y + metrics!!.height)
        } else {
            p.addPoint(caretPos.x, markPos.y)
        }
        p.addPoint(caretPos.x, caretPos.y)
        if (rel.y > 0) {
            p.addPoint(0, caretPos.y)
            p.addPoint(0, markPos.y + metrics!!.height)
            p.addPoint(markPos.x, markPos.y + metrics!!.height)
        } else if (rel.y < 0) {
            p.addPoint(((cols * (width + spacing)) - spacing) * metrics!!.width, caretPos.y)
            p.addPoint(((cols * (width + spacing)) - spacing) * metrics!!.width, markPos.y - metrics!!.height)
            p.addPoint(markPos.x, markPos.y - metrics!!.height)
        } else {
            p.addPoint(markPos.x, caretPos.y)
        }
        p.translate(term.xPos * metrics!!.width, term.yPos * metrics!!.height)
        return p
    }

    public fun setData(rf: RandomAccessFile?) {
        reset()
        sourceRAF = rf
        if (rf != null) {
            try {
                limit = rf.length().toInt() - 1
            } catch (ex: IOException) {
                LOG.log(Level.SEVERE, null, ex)
            }

        }
        seek(0)
        update()
    }

    public fun setData(buf: ByteBuffer?) {
        reset()
        sourceBuf = buf
        if (buf != null) {
            bitBuffer = BitBuffer(buf)
            limit = bitBuffer!!.capacity() - 1
        }
        seek(0)
        update()
    }

    class object {

        protected val PROP_CARETLOCATION: String = "PROP_CARETLOCATION"
        protected val PROP_MARKLOCATION: String = "PROP_MARKLOCATION"
        private val LOG = Logger.getLogger(javaClass<HexEditor>().getName())

        throws(javaClass<FileNotFoundException>())
        public fun mapFile(file: File): RandomAccessFile {
            return RandomAccessFile(file, "rw")
        }
    }
}
