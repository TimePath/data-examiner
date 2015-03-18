package com.timepath.hex

import com.timepath.io.BitBuffer
import com.timepath.plaf.x.filechooser.NativeFileChooser

import javax.swing.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.platform.platformStatic
import java.io.RandomAccessFile

/**
 * @author TimePath
 */
SuppressWarnings("serial")
public class Main private() : JFrame() {
    private val jTabbedPane1: JTabbedPane

    {
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
        jTabbedPane1 = JTabbedPane()
        setJMenuBar(with(JMenuBar()) {
            add(with(JMenu("File")) {
                setMnemonic('F')
                add(with(JMenuItem("Open")) {
                    setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_MASK))
                    addActionListener { choose() }
                    this
                })
                this
            })
            add(with(JMenuItem("Test")) {
                addActionListener {
                    val bytes = byteArray(33, 123, 187.toByte(), 115, 0, 0)
                    val n = 3
                    val buf = ByteBuffer.allocate(bytes.size())
                    buf.put(bytes)
                    val bb = BitBuffer(buf)
                    buf.rewind()
                    bb.getBits(n)
                    val expect = bb.getString()
                    val tab = createTab(expect + ">>" + n)
                    tab.setData(buf)
                    tab.bitShift = 3
                    tab.update()
                }
                this
            })
            this
        })
        GroupLayout(getContentPane()).let {
            this.getContentPane().setLayout(it)
            it.setHorizontalGroup(it.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(jTabbedPane1, GroupLayout.DEFAULT_SIZE, 400, java.lang.Short.MAX_VALUE.toInt()))
            it.setVerticalGroup(it.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(jTabbedPane1, GroupLayout.DEFAULT_SIZE, 279, java.lang.Short.MAX_VALUE.toInt()))
        }
        this.pack()
    }

    private fun choose() = try {
        NativeFileChooser().choose()?.let {
            val file = it[0]
            try {
                createTab(file.getName()).let {
                    it.setData(RandomAccessFile(file, "rw"))
                }
            } catch (ex: FileNotFoundException) {
                LOG.log(Level.SEVERE, null, ex)
            }
        }
    } catch (ex: IOException) {
        LOG.log(Level.SEVERE, null, ex)
    }

    private fun createTab(name: String) = HexEditor().let {
        jTabbedPane1.add(name, it)
        it.requestFocusInWindow()
        this.pack()
        it
    }

    class object {

        private val LOG = Logger.getLogger(javaClass<Main>().getName())

        public platformStatic fun main(args: Array<String>) {
            SwingUtilities.invokeLater { Main().setVisible(true) }
        }
    }
}
