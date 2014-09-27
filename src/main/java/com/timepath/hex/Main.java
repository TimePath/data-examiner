package com.timepath.hex;

import com.timepath.io.BitBuffer;
import com.timepath.plaf.x.filechooser.NativeFileChooser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
@SuppressWarnings("serial")
public class Main extends JFrame {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private JTabbedPane jTabbedPane1;

    private Main() {
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        jTabbedPane1 = new JTabbedPane();
        setJMenuBar(new JMenuBar() {{
            add(new JMenu("File") {{
                setMnemonic('F');
                add(new JMenuItem("Open") {{
                    setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_MASK));
                    addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            choose();
                        }
                    });
                }});
            }});
            add(new JMenuItem("Test") {{
                addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        @NotNull byte[] bytes = {0x21, 0x7b, (byte) 0xbb, 0x73, 0x00, 0x00};
                        int n = 3;
                        ByteBuffer buf = ByteBuffer.allocate(bytes.length);
                        buf.put(bytes);
                        @NotNull BitBuffer bb = new BitBuffer(buf);
                        buf.rewind();
                        bb.getBits(n);
                        @NotNull String expect = bb.getString();
                        @NotNull HexEditor tab = createTab(expect + ">>" + n);
                        tab.setData(buf);
                        tab.setBitShift(3);
                        tab.update();
                    }
                });
            }});
        }});
        @NotNull GroupLayout layout = new GroupLayout(getContentPane());
        this.getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(jTabbedPane1, GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE));
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(jTabbedPane1, GroupLayout.DEFAULT_SIZE, 279, Short.MAX_VALUE));
        this.pack();
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new Main().setVisible(true);
            }
        });
    }

    private void choose() {
        @NotNull NativeFileChooser fc = new NativeFileChooser();
        try {
            @Nullable File[] fs = fc.choose();
            if (fs == null) return;
            File file = fs[0];
            try {
                createTab(file.getName()).setData(HexEditor.mapFile(file));
            } catch (FileNotFoundException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    @NotNull
    private HexEditor createTab(String name) {
        @NotNull HexEditor t = new HexEditor();
        jTabbedPane1.add(name, t);
        t.requestFocusInWindow();
        pack();
        return t;
    }
}
