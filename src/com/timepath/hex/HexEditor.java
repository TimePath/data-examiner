package com.timepath.hex;

import com.timepath.plaf.x.filechooser.NativeFileChooser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author timepath
 */
public class HexEditor extends javax.swing.JFrame {

    /**
     * Creates new form HexEditor
     */
    public HexEditor() {
        initComponents();
    }

    private void open(File f) {
        Editor t = new Editor();
        try {
            t.setData(mapFile(f));
        } catch (IOException ex) {
            Logger.getLogger(HexEditor.class.getName()).log(Level.SEVERE, null, ex);
        }
        this.jTabbedPane1.add(f.getName(), t);
        t.requestFocusInWindow();
    }

    //<editor-fold defaultstate="collapsed" desc="Helpers">
    public static RandomAccessFile mapFile(File f) throws IOException {
        RandomAccessFile rf = new RandomAccessFile(f, "rw");
//        FileInputStream fis = new FileInputStream(f);
//        FileChannel fc = fis.getChannel();
//        MappedByteBuffer mbb = fc.map(FileChannel.MapMode.READ_ONLY, 0, f.length());
        return rf;
    }

    public static String getString(ByteBuffer source) {
        ByteBuffer[] cloned = getTextBuffer(source.duplicate(), true);
        source.position(source.position() + (cloned[0].limit() - cloned[0].position()));
        return Charset.forName("UTF-8").decode(cloned[1]).toString();
    }

    public static String getText(ByteBuffer source) {
        return getText(source, false);
    }

    public static String getText(ByteBuffer source, boolean terminator) {
        return Charset.forName("UTF-8").decode(getTextBuffer(source, terminator)[1]).toString();
    }

    public static ByteBuffer[] getTextBuffer(ByteBuffer source, boolean terminatorCheck) {
        int originalPosition = source.position();
        int originalLimit = source.limit();
        int inclusiveEnd = source.limit();
        int trimmedEnd = source.limit();
        if (terminatorCheck) {
            while (source.remaining() > 0) {
                if (source.get() == 0x00) { // Check for null terminator
                    inclusiveEnd = source.position();
                    trimmedEnd = source.position() - 1;
                    break;
                }
            }
        }
        source.position(originalPosition);
        source.limit(inclusiveEnd);
        ByteBuffer inclusive = source.slice();
        source.limit(trimmedEnd);
        ByteBuffer trimmed = source.slice();
        source.limit(originalLimit);

        return new ByteBuffer[]{inclusive, trimmed};
    }

    public static String hexDump(ByteBuffer buf) {
        int originalPosition = buf.position();
        byte[] byt = new byte[buf.limit()];
        buf.get(byt);
        buf.position(originalPosition);
        return hex(byt);
    }

    public static String hex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        for (byte b : a) {
            sb.append(String.format("%02x", b & 0xff)).append(" ");
        }
        return sb.toString().toUpperCase().trim();
    }

    public static ByteBuffer getSlice(ByteBuffer source) {
        return getSlice(source, source.remaining());
    }

    public static ByteBuffer getSlice(ByteBuffer source, int length) {
        int originalLimit = source.limit();
        source.limit(source.position() + length);
        ByteBuffer sub = source.slice();
        source.position(source.limit());
        source.limit(originalLimit);
        sub.order(ByteOrder.LITTLE_ENDIAN);
        return sub;
    }

    public static ByteBuffer getSafeSlice(ByteBuffer source, int length) {
        if (length > source.remaining()) {
            length = source.remaining();
        }
        return getSlice(source, length);
    }
    //</editor-fold>

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTabbedPane1 = new javax.swing.JTabbedPane();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        jMenuItem1 = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        jMenu1.setText("File");

        jMenuItem1.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem1.setText("Open");
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem1ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem1);

        jMenuBar1.add(jMenu1);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 279, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem1ActionPerformed
        NativeFileChooser fc = new NativeFileChooser();
        try {
            File[] fs = fc.choose();
            if (fs == null) {
                return;
            }
            File f = fs[0];
            open(f);
        } catch (IOException ex) {
            Logger.getLogger(HexEditor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_jMenuItem1ActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(HexEditor.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(HexEditor.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(HexEditor.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(HexEditor.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new HexEditor().setVisible(true);
            }
        });
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JTabbedPane jTabbedPane1;
    // End of variables declaration//GEN-END:variables
}
