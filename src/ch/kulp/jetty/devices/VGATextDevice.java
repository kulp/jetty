package ch.kulp.jetty.devices;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import ch.kulp.jetty.MappedDevice;

public class VGATextDevice implements MappedDevice {
    private static final int REFRESH_HZ = 60;
    public static final int SCREEN_ROWS = 40, SCREEN_COLS = 80;
    protected static final int FONT_PIXEL_HEIGHT = 12, FONT_PIXEL_WIDTH = 8;
    private static final int BASE_ADDR = 0x10000;
    protected Canvas canvas = new VGATextCanvas();
    protected BufferedImage fontImage;
    protected BufferedImage backingImage = new BufferedImage(SCREEN_COLS * FONT_PIXEL_WIDTH, SCREEN_ROWS
            * FONT_PIXEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
    protected JFrame frame = new JFrame(getClass().getSimpleName());
    final protected Dimension screenSize = new Dimension(FONT_PIXEL_WIDTH * SCREEN_COLS, FONT_PIXEL_HEIGHT
            * SCREEN_ROWS);
    byte store[][] = new byte[SCREEN_COLS][SCREEN_ROWS];
    protected Thread refresher;

    {
        try {
            fontImage = ImageIO.read(new File("rsrc/font.png"));
            // fontImage =
            // ImageIO.read(getClass().getResourceAsStream("rsrc/font.png"));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        canvas.setSize(screenSize);
        canvas.setBackground(Color.BLACK);

        frame.add(canvas);
        frame.getContentPane().setPreferredSize(screenSize);
        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        refresher = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000 / REFRESH_HZ);
                    } catch (InterruptedException e) {
                    }
                    canvas.repaint();
                }
            }
        }, getClass().getSimpleName() + " refresher");
        refresher.start();
    }

    public void putGlyph(int row, int col, int glyphIndex) {
        putGlyph(canvas.getGraphics(), row, col, glyphIndex);
    }

    private void putGlyph(Graphics g, int row, int col, int glyphIndex) {
        int dy = row * FONT_PIXEL_HEIGHT;
        int dx = col * FONT_PIXEL_WIDTH;
        int sx = glyphIndex * FONT_PIXEL_WIDTH;
        g.drawImage(fontImage, dx, dy, dx + FONT_PIXEL_WIDTH, dy + FONT_PIXEL_HEIGHT, sx, 0, sx + FONT_PIXEL_WIDTH,
                FONT_PIXEL_HEIGHT, null);
    }

    @SuppressWarnings("serial")
    public class VGATextCanvas extends Canvas {
        @Override
        public void paint(Graphics g) {
            g.drawImage(backingImage, 0, 0, Color.BLACK, null);
        }
    }

    @Override
    public int fetch(int addr) {
        // TODO make fetches from video RAM work when it works in hardware
        return 0;
    }

    @Override
    public void store(int addr, int rhs) {
        int offset = addr - BASE_ADDR;
        int row = offset / SCREEN_COLS;
        int col = offset % SCREEN_COLS;
        setGlyph(row, col, rhs);
    }

    private void setGlyph(int row, int col, int rhs) {
        if (col < SCREEN_COLS && row < SCREEN_ROWS) {
            store[col][row] = (byte) (rhs & 255);
            putGlyph(backingImage.getGraphics(), row, col, store[col][row]);
        }
    }

    @Override
    public int getMappedBase() {
        return BASE_ADDR;
    }

    @Override
    public int getMappedLength() {
        return SCREEN_COLS * SCREEN_ROWS;
    }
}
