package com.timepath.hex;

import java.awt.Color;
import java.util.logging.Logger;

public class Selection {

    private static final Logger LOG = Logger.getLogger(Selection.class.getName());

    long mark;
    long caret;
    Color color;

    Selection(long mark, long caret, Color c) {
        this.mark = mark;
        this.caret = caret;
        this.color = c;
    }
}
