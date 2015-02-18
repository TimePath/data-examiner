package com.timepath.curses


import java.awt.*
import java.util.Arrays
import java.util.logging.Logger

public open class Multiplexer(private val terms: MutableCollection<Terminal> = linkedListOf()) : Terminal() {

    {
        charBuf = CharArray(termWidth * termHeight)
        bgBuf = arrayOfNulls<Color>(termWidth * termHeight)
        Arrays.fill(bgBuf, Color.BLACK)
        fgBuf = arrayOfNulls<Color>(termWidth * termHeight)
        Arrays.fill(fgBuf, Color.WHITE)
    }

    protected fun add(vararg args: Terminal) {
        for (t in args) {
            terms.add(t)
            termWidth = Math.max(t.xPos + t.termWidth, termWidth)
            termHeight = Math.max(t.yPos + t.termHeight, termHeight)
        }
    }

    override fun paint(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setColor(getBackground())
        g2.fillRect(0, 0, termWidth * metrics!!.width, termHeight * metrics!!.height)
        for (t in terms) {
            t.paint(g2)
        }
    }

    class object {

        private val LOG = Logger.getLogger(javaClass<Multiplexer>().getName())
    }
}
