package com.timepath.curses

import java.awt.*
import java.awt.geom.AffineTransform
import javax.swing.JComponent

public open class Terminal(w: Int = 0, h: Int = 0) : JComponent() {

    private val FONT_SIZE = 12
    /** Java2D assumes 72 DPI. */
    private val termFont = Font(Font.MONOSPACED, Font.PLAIN,
            Math.round((FONT_SIZE * Toolkit.getDefaultToolkit().getScreenResolution()).toDouble() / 72.0).toInt())
    private val fontMetrics = getFontMetrics(termFont)
    public var xPos: Int = 0
    public var yPos: Int = 0
    public var bgBuf: Array<Color> = Array(w * h) { Color.BLACK }
    public var fgBuf: Array<Color> = Array(w * h) { Color.WHITE }
    protected var metrics: Dimension = Dimension(fontMetrics.stringWidth(" "),
            fontMetrics.getHeight() - fontMetrics.getLeading())
    var termWidth: Int = w
    var termHeight: Int = h
    var charBuf: CharArray = CharArray(w * h)
    private val caret = Point(0, 0)

    public fun clear() {
        charBuf.fill(0.toChar())
        bgBuf.fill(Color.BLACK)
        fgBuf.fill(Color.WHITE)
    }

    override fun paint(g: Graphics) = (g as Graphics2D).let { g ->
        val oldColor = g.getColor()
        val oldAt = g.getTransform()
        val newAt = AffineTransform()
        newAt.translate((xPos * metrics.width).toDouble(), (yPos * metrics.height).toDouble())
        g.transform(newAt)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP)
        g.setFont(termFont)
        for (y in termHeight.indices) {
            for (x in termWidth.indices) {
                val px = x * metrics.width
                val py = y * metrics.height
                g.setColor(bgBuf[x + (y * termWidth)])
                g.fillRect(px, py, metrics.width, metrics.height)
                g.setColor(fgBuf[x + (y * termWidth)])
                val character = charBuf[x + (y * termWidth)]
                if (character.toInt() == 0) {
                    continue
                }
                g.drawString(character.toString(), px, py + fontMetrics.getAscent())
            }
        }
        g.setTransform(oldAt)
        g.setColor(oldColor)
    }

    override fun getPreferredSize() = Dimension(termWidth * metrics.width, termHeight * metrics.height)

    override fun getMinimumSize() = getPreferredSize()

    public fun position(x: Int, y: Int): Unit = caret.setLocation(x, y)

    public fun write(o: Any): Unit = o.toString().forEachIndexed { i, c ->
        val idx = caret.x + i + (caret.y * termWidth)
        if ((idx >= 0) && (idx < charBuf.size())) {
            charBuf[idx] = c
        }
    }

    public fun cellToView(ptr: Long): Point {
        val x = ptr % termWidth
        val y = ptr / termWidth
        return Point(
                ((x * metrics.width) + (xPos * metrics.width)).toInt(),
                ((y * metrics.height) + (yPos * metrics.height)).toInt()
        )
    }

    public fun viewToCell(p: Point): Int {
        p.translate(-xPos.times(metrics.width), -yPos * metrics.height)
        if ((p.x < 0) || (p.x >= (termWidth * metrics.width))) {
            return -1
        }
        if ((p.y < 0) || (p.y >= (termHeight * metrics.height))) {
            return -1
        }
        val x = p.x / metrics.width
        val y = p.y / metrics.height
        return (termWidth * y) + x
    }

}
