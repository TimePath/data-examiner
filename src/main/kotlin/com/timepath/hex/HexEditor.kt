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
import com.timepath.util.BeanProperty
import com.timepath.util.observe

public data class Selection(var mark: Long, var caret: Long, var color: Color?)

public class HexEditor : Multiplexer(), KeyListener, MouseMotionListener, MouseListener, MouseWheelListener {
    protected var cols: Int = 16
    protected var rows: Int = 16
    protected val tags: MutableList<Selection> = LinkedList()
    protected val termData: Terminal = Terminal(cols * 3, rows).let {
        it.xPos = 9
        it.yPos = 1
        it
    }
    protected val termText: Terminal = Terminal(cols, rows).let {
        it.xPos = 9 + cols * 3
        it.yPos = 1
        it
    }
    protected val termLines: Terminal = Terminal(8, rows).let {
        it.yPos = 1
        Arrays.fill(it.fgBuf, Color.GREEN)
        Arrays.fill(it.bgBuf, Color.DARK_GRAY)
        it
    }
    protected val termHeader: Terminal = Terminal((3 * cols) - 1, 1).let {
        it.xPos = 9
        it
    }
    protected val termShift: Terminal = Terminal(1, 1).let {
        Arrays.fill(it.fgBuf, Color.CYAN)
        Arrays.fill(it.bgBuf, Color.BLACK)
        it
    }
    protected val termCalc: Terminal = Terminal(54, 6).let {
        it.yPos = 1 + rows + 1
        it
    }
    protected val propertyChangeSupport: PropertyChangeSupport = PropertyChangeSupport(this)
    protected val vetoableChangeSupport: VetoableChangeSupport = VetoableChangeSupport(this)
    protected var bitBuffer: BitBuffer? = null
    protected var sourceBuf: ByteBuffer? = null
    public var caretLocation: Long by BeanProperty(0L, propertyChangeSupport, vetoableChangeSupport)
    protected var limit: Long = 0
    public var markLocation: Long by BeanProperty(0L, propertyChangeSupport, vetoableChangeSupport)
    protected var offset: Long = 0
    protected var sourceRAF: RandomAccessFile? = null
    SuppressWarnings("BooleanVariableAlwaysNegated")
    protected var selecting: Boolean = false
    var bitShift: Int = 0
        set(value) {
            var new = value
            if ((new < 0) || (new >= 8)) {
                // Shifting off current byte
                try {
                    caretLocation += Math.round(Math.signum(new.toFloat())).toLong()
                } catch (ignored: PropertyVetoException) {
                }
                // Bring back into acceptable range
                new += 8
                new %= 8
            }
            $bitShift = new
        }

    {
        initColumns()
        setBackground(Color.BLACK)
        add(termData, termText, termLines, termHeader, termShift, termCalc)
        addKeyListener(this)
        addMouseMotionListener(this)
        addMouseListener(this)
        addMouseWheelListener(this)
        ::caretLocation.let {
            it.observe(vetoableChangeSupport) {(old: Long, new: Long) ->
                when {
                    new !in 0..limit - 1 -> "Caret would be out of bounds"
                    else -> null
                }
            }
            it.observe(propertyChangeSupport) {(old: Long, new: Long) ->
                if (new < offset) {
                    // on previous page
                    skip((-(cols * rows)).toLong())
                } else if (new >= (offset + (cols * rows).toLong())) {
                    // on next page
                    skip((cols * rows).toLong())
                }
                if (!selecting) {
                    try {
                        markLocation = new
                    } catch (ex: PropertyVetoException) {
                        LOG.log(Level.SEVERE, null, ex)
                    }
                }
            }
        }
        setFocusable(true)
        reset()
    }

    protected fun initColumns() {
        Arrays.fill(termHeader.bgBuf, Color.WHITE)
        Arrays.fill(termHeader.fgBuf, Color.BLACK)
        val sb = StringBuilder(cols * 3)
        cols.indices.forEach {
            sb.append(" %02X".format(it and 0xFF))
        }
        termHeader.position(0, 0)
        termHeader.write(sb.substring(1))
    }

    public fun skip(delta: Long): Unit = seek(offset + delta)

    public fun seek(seek: Long) {
        var tmp = seek
        tmp = Math.max(Math.min(tmp, (limit - (limit % cols)).toLong()), 0)
        if (sourceRAF != null) {
            try {
                sourceRAF!!.seek(tmp)
                val array = ByteArray(Math.min((cols * rows).toLong(), sourceRAF!!.length() - tmp).toInt())
                sourceRAF!!.read(array)
                bitBuffer = BitBuffer(ByteBuffer.wrap(array))
                bitBuffer!!.position(0, bitShift)
                offset = tmp
            } catch (ex: IOException) {
                LOG.log(Level.SEVERE, null, ex)
            }

        } else if (sourceBuf != null) {
            sourceBuf!!.position(tmp.toInt())
            bitBuffer = BitBuffer(DataUtils.getSlice(sourceBuf, sourceBuf!!.remaining()))
            bitBuffer!!.position(0, bitShift)
            offset = tmp
        }
    }

    protected fun reset() {
        markLocation = -1L
        caretLocation = 0
    }

    override fun keyTyped(e: KeyEvent) = Unit

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

    override fun mouseMoved(e: MouseEvent) = Unit

    override fun mouseClicked(e: MouseEvent) = Unit

    override fun mousePressed(e: MouseEvent) {
        requestFocusInWindow()
        if (SwingUtilities.isLeftMouseButton(e)) {
            var cell = termData.viewToCell(e.getPoint())
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

    protected fun updateRows(): Unit = rows.indices.forEach {
        val address = "%08X".format((it * cols).toLong() + offset)
        termLines.position(0, it)
        termLines.write(address)
    }

    protected fun updateData() {
        termData.clear()
        termText.clear()
        if (bitBuffer == null) return
        bitBuffer!!.position(0, bitShift)
        var row = 0
        val bytes = ByteArray(cols)
        while (bitBuffer!!.hasRemaining()) {
            val read = Math.min(bitBuffer!!.remaining(), bytes.size())
            bitBuffer!!.get(bytes, 0, read)
            val sb = StringBuilder(read * 3)
            for (i in 0..read - 1) {
                sb.append(" %02X".format(bytes[i].toInt() and 0xFF))
            }
            termData.position(0, row)
            termData.write(sb.substring(1))
            val sb2 = StringBuilder(read)
            for (i in 0..read - 1) {
                sb2.append(displayChar((bytes[i].toInt() and 0xFF).toChar()))
            }
            termText.position(0, row)
            termText.write(sb2.toString())
            if (++row >= rows) break
        }
    }

    protected fun displayChar(c: Char): Char = when {
        c.isWhitespace(), c.isISOControl() -> '.'
        else -> c
    }

    protected fun updateStats() {
        val pos = (caretLocation - offset).toInt()
        termCalc.clear()
        when {
            bitBuffer == null,
            pos > bitBuffer!!.limit(),
            pos < 0
            -> return
        }
        val buf = bitBuffer!!
        buf.position(pos, bitShift)
        val temp = ByteArray(Math.min(buf.remaining(), 4))
        buf.get(temp)
        buf.position(pos, bitShift)
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
        termCalc.write(value and 0xFF)
        termCalc.position(idx[1] + (if ((value < 0)) -1 else 0), yOff + 1)
        termCalc.write(value)
        // binary
        for (i in temp.indices) {
            termCalc.position(idx[2] + (i * 9), yOff)
            termCalc.write(StringBuilder(binaryDump((temp[i].toInt() and 0xFF).toLong())))
        }
        // short
        calcBuf.position(0)
        calcBuf.order(ByteOrder.LITTLE_ENDIAN)
        value = calcBuf.getShort().toLong()
        termCalc.position(idx[1], yOff + 2)
        termCalc.write(value and 0xFFFF)
        termCalc.position(idx[1] + (if ((value < 0)) -1 else 0), yOff + 3)
        termCalc.write(value)
        calcBuf.position(0)
        calcBuf.order(ByteOrder.BIG_ENDIAN)
        value = calcBuf.getShort().toLong()
        termCalc.position(idx[2], yOff + 2)
        termCalc.write(value and 0xFFFF)
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
        return "%8s".format(java.lang.Long.toBinaryString(l)).replace(' ', '0')
    }

    override fun mouseReleased(e: MouseEvent) = Unit

    override fun mouseEntered(e: MouseEvent) = Unit

    override fun mouseExited(e: MouseEvent) = Unit

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
        (g as Graphics2D).let { g ->
            for (i in (tags.size() + 1).indices) {
                val sel = if ((i == tags.size())) Selection(markLocation, caretLocation, Color.RED) else tags[i]
                g.setColor(sel.color)
                if (sel.mark >= 0) {
                    g.drawPolygon(calcPolygon(termData, sel.mark, sel.caret, 2, 1))
                    g.drawPolygon(calcPolygon(termText, sel.mark, sel.caret, 1, 0))
                }
            }
            val markLoc = markLocation
            if ((markLoc >= offset) && (markLoc < (offset + (cols * rows).toLong()))) {
                g.setColor(Color.YELLOW)
                g.draw(getCellRect(termData, markLoc, 2, 1))
                g.draw(getCellRect(termText, markLoc, 1, 0))
            }
            val caretLoc = caretLocation
            if ((caretLoc >= offset) && (caretLoc < (offset + (cols * rows).toLong()))) {
                g.setColor(Color.WHITE)
                g.draw(getCellRect(termData, caretLoc, 2, 1))
                g.draw(getCellRect(termText, caretLoc, 1, 0))
            }
        }
    }

    protected fun getCellRect(term: Terminal, address: Long, width: Int, spacing: Int): Shape {
        term.cellToView((address - offset) * (width + spacing).toLong()).let {
            return Rectangle(it.x, it.y, metrics.width * width, metrics.height)
        }
    }

    protected fun calcPolygon(term: Terminal, markIdx: Long, caretIdx: Long, width: Int, spacing: Int): Polygon {
        var caretIdx = caretIdx - offset
        val caretRow = caretIdx / cols.toLong()
        caretIdx = when {
            caretIdx < 0 -> 0
            caretIdx > (cols * rows) -> (cols * rows - 1).toLong()
            else -> caretIdx
        }
        val caretPos = term.cellToView(caretIdx * (width + spacing).toLong())
        caretPos.translate(-term.xPos * metrics.width, -term.yPos * metrics.height)
        var markIdx = markIdx - offset
        val markRow = markIdx / cols.toLong()
        markIdx = when {
            markIdx < 0 -> 0
            markIdx > (cols * rows) -> (cols * rows - 1).toLong()
            else -> markIdx
        }
        val markPos = term.cellToView(markIdx * (width + spacing).toLong())
        markPos.translate(-term.xPos * metrics.width, -term.yPos * metrics.height)
        val rel = Point((caretIdx - markIdx).toInt(), (caretRow - markRow).toInt())
        when { // further right
            rel.x >= 0 -> caretPos.x += metrics.width * width
            else -> markPos.x += metrics.width * width
        }
        when { // further down
            rel.y >= 0 -> caretPos.y += metrics.height
            else -> markPos.y += metrics.height
        }
        with(Polygon()) {
            addPoint(markPos.x, markPos.y)
            when {
                rel.y > 0 -> {
                    addPoint(((cols * (width + spacing)) - spacing) * metrics.width, markPos.y)
                    addPoint(((cols * (width + spacing)) - spacing) * metrics.width, caretPos.y - metrics.height)
                    addPoint(caretPos.x, caretPos.y - metrics.height)
                }
                rel.y < 0 -> {
                    addPoint(0, markPos.y)
                    addPoint(0, caretPos.y + metrics.height)
                    addPoint(caretPos.x, caretPos.y + metrics.height)
                }
                else -> {
                    addPoint(caretPos.x, markPos.y)
                }
            }
            addPoint(caretPos.x, caretPos.y)
            when {
                rel.y > 0 -> {
                    addPoint(0, caretPos.y)
                    addPoint(0, markPos.y + metrics.height)
                    addPoint(markPos.x, markPos.y + metrics.height)
                }
                rel.y < 0 -> {
                    addPoint(((cols * (width + spacing)) - spacing) * metrics.width, caretPos.y)
                    addPoint(((cols * (width + spacing)) - spacing) * metrics.width, markPos.y - metrics.height)
                    addPoint(markPos.x, markPos.y - metrics.height)
                }
                else -> {
                    addPoint(markPos.x, caretPos.y)
                }
            }
            translate(term.xPos * metrics.width, term.yPos * metrics.height)
            return this
        }
    }

    public fun setData(rf: RandomAccessFile?) {
        reset()
        sourceRAF = rf
        if (rf != null) {
            try {
                limit = (rf.length().toInt() - 1).toLong()
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
            limit = (bitBuffer!!.capacity() - 1).toLong()
        }
        seek(0)
        update()
    }

    class object {

        private val LOG = Logger.getLogger(javaClass<HexEditor>().getName())

        throws(javaClass<FileNotFoundException>())
        public fun mapFile(file: File): RandomAccessFile {
            return RandomAccessFile(file, "rw")
        }
    }
}