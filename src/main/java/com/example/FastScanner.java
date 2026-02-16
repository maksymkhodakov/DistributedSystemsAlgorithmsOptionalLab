package com.example;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Fast integer input scanner.
 */
public final class FastScanner {

    private final InputStream in;
    private final byte[] buffer = new byte[1 << 16];
    private int ptr = 0;
    private int len = 0;

    public FastScanner(InputStream in) {
        this.in = in;
    }

    public int nextInt() throws IOException {
        int c;
        do {
            c = read();
            if (c == -1) throw new EOFException();
        } while (c <= ' ');

        int sign = 1;
        if (c == '-') {
            sign = -1;
            c = read();
        }

        int val = 0;
        while (c > ' ') {
            val = val * 10 + (c - '0');
            c = read();
        }
        return val * sign;
    }

    private int read() throws IOException {
        if (ptr >= len) {
            len = in.read(buffer);
            ptr = 0;
            if (len <= 0) return -1;
        }
        return buffer[ptr++];
    }
}
