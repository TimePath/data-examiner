package com.timepath.hex

import com.timepath.io.BitBuffer
import com.timepath.plaf.x.filechooser.NativeFileChooser

import javax.swing.*
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.platform.platformStatic

/**
 * @author TimePath
 */
SuppressWarnings("serial")
public class Main private() : JFrame() {
    private val jTabbedPane1: JTabbedPane

    {
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
        jTabbedPane1 = JTabbedPane()
        setJMenuBar(object : JMenuBar() {
            {
                add(object : JMenu("File") {
                    {
                        setMnemonic('F')
                        add(object : JMenuItem("Open") {
                            {
                                setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_MASK))
                                addActionListener(object : ActionListener {
                                    override fun actionPerformed(e: ActionEvent) {
                                        choose()
                                    }
                                })
                            }
                        })
                    }
                })
                add(object : JMenuItem("Test") {
                    {
                        addActionListener(object : ActionListener {
                            override fun actionPerformed(e: ActionEvent) {
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
                        })
                    }
                })
            }
        })
        val layout = GroupLayout(getContentPane())
        this.getContentPane().setLayout(layout)
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(jTabbedPane1, GroupLayout.DEFAULT_SIZE, 400, java.lang.Short.MAX_VALUE.toInt()))
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(jTabbedPane1, GroupLayout.DEFAULT_SIZE, 279, java.lang.Short.MAX_VALUE.toInt()))
        this.pack()
    }

    private fun choose() {
        val fc = NativeFileChooser()
        try {
            val fs = fc.choose()
            if (fs == null) return
            val file = fs[0]
            try {
                createTab(file.getName()).setData(HexEditor.mapFile(file))
            } catch (ex: FileNotFoundException) {
                LOG.log(Level.SEVERE, null, ex)
            }

        } catch (ex: IOException) {
            LOG.log(Level.SEVERE, null, ex)
        }

    }

    private fun createTab(name: String): HexEditor {
        val t = HexEditor()
        jTabbedPane1.add(name, t)
        t.requestFocusInWindow()
        pack()
        return t
    }

    class object {

        private val LOG = Logger.getLogger(javaClass<Main>().getName())

        public platformStatic fun main(args: Array<String>) {
            EventQueue.invokeLater(object : Runnable {
                override fun run() {
                    Main().setVisible(true)
                }
            })
        }
    }
}
