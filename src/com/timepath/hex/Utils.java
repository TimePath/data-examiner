package com.timepath.hex;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.logging.Logger;

public class Utils {

    private static final Logger LOG = Logger.getLogger(Utils.class.getName());

    public static String displayChar(int c) {
        return String.valueOf(Character.isWhitespace(c)
                || Character.isISOControl(c) ? '.' : (char) c);
    }

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
        return hexFormat(byt);
    }

    public static String hexFormat(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 3);
        for (byte b : a) {
            sb.append(String.format("%02x ", b & 0xFF));
        }
        return sb.toString().toUpperCase();
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

    private Utils() {
    }

}
