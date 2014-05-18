package com.timepath.hex;

import com.timepath.plaf.x.filechooser.NativeFileChooser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
@SuppressWarnings("serial")
public class Main extends JFrame {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private JTabbedPane jTabbedPane1;

    /**
     * Creates new form HexEditor
     */
    private Main() {
        initComponents();
    }

    private void initComponents() {
        jTabbedPane1 = new JTabbedPane();
        JMenuBar jMenuBar1 = new JMenuBar();
        JMenu jMenu1 = new JMenu();
        JMenuItem jMenuItem1 = new JMenuItem();
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        jMenu1.setText("File");
        jMenu1.setMnemonic('F');
        jMenuItem1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_MASK));
        jMenuItem1.setText("Open");
        jMenuItem1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                choose();
            }
        });
        jMenu1.add(jMenuItem1);
        jMenuBar1.add(jMenu1);
        setJMenuBar(jMenuBar1);
        GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(jTabbedPane1, GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
                                 );
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                      .addComponent(jTabbedPane1, GroupLayout.DEFAULT_SIZE, 279, Short.MAX_VALUE)
                               );
        pack();
    }

    private void choose() {
        NativeFileChooser fc = new NativeFileChooser();
        try {
            File[] fs = fc.choose();
            if(fs == null) {
                return;
            }
            File file = fs[0];
            open(file);
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    private void open(File file) {
        HexEditor t = new HexEditor();
        try {
            t.setData(HexEditor.mapFile(file));
        } catch(FileNotFoundException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        JPanel p = new JPanel();
        p.add(t);
        jTabbedPane1.add(file.getName(), p);
        t.requestFocusInWindow();
        pack();
    }

    /**
     * @param args
     *         the command line arguments
     */
    @SuppressWarnings("MethodNamesDifferingOnlyByCase")
    public static void main(String... args) {
        /* Create and display the form */
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new Main().setVisible(true);
            }
        });
    }
}
