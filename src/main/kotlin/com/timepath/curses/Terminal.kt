package com.timepath.curses


import javax.swing.*
import java.awt.*
import java.awt.geom.AffineTransform
import java.util.Arrays
import java.util.logging.Logger


public open class Terminal(w: Int = 0, h: Int = 0) : JComponent() {

    /**
     * Java2D assumes 72 DPI.
     */
    private val termFont = Font(Font.MONOSPACED, Font.PLAIN, Math.round((FONT_SIZE * Toolkit.getDefaultToolkit().getScreenResolution()).toDouble() / 72.0).toInt())
    private val fontMetrics = getFontMetrics(termFont)
    public var xPos: Int = 0
    public var yPos: Int = 0
    public var bgBuf: Array<Color?>
    public var fgBuf: Array<Color?>
    protected var metrics: Dimension? = null
    var termWidth: Int = 0
    var termHeight: Int = 0
    var charBuf: CharArray
    private val caret = Point(0, 0)

            ;{
        termWidth = w
        termHeight = h
        charBuf = CharArray(w * h)
        bgBuf = arrayOfNulls<Color>(w * h)
        fgBuf = arrayOfNulls<Color>(w * h)
        clear()

        metrics = Dimension(fontMetrics.stringWidth(" "), fontMetrics.getHeight() - fontMetrics.getLeading())
    }

    public fun clear() {
        Arrays.fill(charBuf, 0.toChar())
        Arrays.fill(bgBuf, Color.BLACK)
        Arrays.fill(fgBuf, Color.WHITE)
    }

    override fun paint(g: Graphics) {
        val g2 = g as Graphics2D
        val oldColor = g2.getColor()
        val oldAt = g2.getTransform()
        val newAt = AffineTransform()
        newAt.translate((xPos * metrics!!.width).toDouble(), (yPos * metrics!!.height).toDouble())
        g2.transform(newAt)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP)
        g2.setFont(termFont)
        for (y in 0..termHeight - 1) {
            for (x in 0..termWidth - 1) {
                val r = Rectangle(x * metrics!!.width, y * metrics!!.height, metrics!!.width, metrics!!.height)
                g2.setColor(bgBuf[x + (y * termWidth)])
                g2.fillRect(r.x, r.y, r.width, r.height)
                g2.setColor(fgBuf[x + (y * termWidth)])
                val character = charBuf[x + (y * termWidth)]
                if (character.toInt() == 0) {
                    continue
                }
                g2.drawString(character.toString(), r.x, r.y + fontMetrics.getAscent())
            }
        }
        g2.setTransform(oldAt)
        g2.setColor(oldColor)
    }

    override fun getPreferredSize(): Dimension {
        if (metrics == null) {
            return super.getPreferredSize()
        }
        return Dimension(termWidth * metrics!!.width, termHeight * metrics!!.height)
    }

    override fun getMinimumSize(): Dimension {
        return getPreferredSize()
    }

    public fun position(x: Int, y: Int) {
        caret.setLocation(x, y)
    }

    public fun write(o: Any) {
        val text = o.toString()
        val chars = text.toCharArray()
        for (i in chars.indices) {
            val idx = caret.x + i + (caret.y * termWidth)
            if ((idx >= 0) && (idx < charBuf.size())) {
                charBuf[idx] = chars[i]
            }
        }
    }

    public fun cellToView(ptr: Long): Point {
        val x = ptr % termWidth.toLong()
        val y = ptr / termWidth.toLong()
        val p = Point((x * metrics!!.width.toLong()).toInt(), (y * metrics!!.height.toLong()).toInt())
        p.translate(xPos * metrics!!.width, yPos * metrics!!.height)
        return p
    }

    public fun viewToCell(p: Point): Int {
        p.translate(-xPos * metrics!!.width, -yPos * metrics!!.height)
        if ((p.x < 0) || (p.x >= (termWidth * metrics!!.width))) {
            return -1
        }
        if ((p.y < 0) || (p.y >= (termHeight * metrics!!.height))) {
            return -1
        }
        val x = p.x / metrics!!.width
        val y = p.y / metrics!!.height
        return (termWidth * y) + x
    }

    class object {

        private val LOG = Logger.getLogger(javaClass<Terminal>().getName())
        private val FONT_SIZE = 12
    }
}
