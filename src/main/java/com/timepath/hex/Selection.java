package com.timepath.hex;

import java.awt.*;
import java.util.logging.Logger;

public class Selection {

    private static final Logger LOG = Logger.getLogger(Selection.class.getName());
    private long mark;
    private long caret;
    private Color color;

    Selection() {
    }

    public Selection(long mark, long caret, Color color) {
        this.mark = mark;
        this.caret = caret;
        this.color = color;
    }

    /**
     * @return the mark
     */
    public long getMark() {
        return mark;
    }

    /**
     * @param mark the mark to set
     */
    public void setMark(long mark) {
        this.mark = mark;
    }

    /**
     * @return the caret
     */
    public long getCaret() {
        return caret;
    }

    /**
     * @param caret the caret to set
     */
    public void setCaret(long caret) {
        this.caret = caret;
    }

    /**
     * @return the color
     */
    public Color getColor() {
        return color;
    }

    /**
     * @param color the color to set
     */
    public void setColor(Color color) {
        this.color = color;
    }
}
