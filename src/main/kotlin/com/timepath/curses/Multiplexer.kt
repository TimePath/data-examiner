package com.timepath.curses


import java.awt.*
import java.util.logging.Logger

public open class Multiplexer(private val terms: MutableCollection<Terminal> = linkedListOf()) : Terminal() {

    protected fun add(vararg args: Terminal): Unit = args.forEach {
        terms.add(it)
        termWidth = Math.max(it.xPos + it.termWidth, termWidth)
        termHeight = Math.max(it.yPos + it.termHeight, termHeight)
    }

    override fun paint(g: Graphics) = (g as Graphics2D).let { g ->
        g.setColor(getBackground())
        g.fillRect(0, 0, termWidth * metrics.width, termHeight * metrics.height)
        for (t in terms) {
            t.paint(g)
        }
    }
}
