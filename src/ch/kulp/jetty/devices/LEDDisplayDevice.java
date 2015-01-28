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

public class LEDDisplayDevice implements MappedDevice {
    private static final int REFRESH_HZ = 60;
    public static final int DISPLAY_COLS = 4;
    protected static final int CELL_HEIGHT = 64, DIGIT_WIDTH = 38, DOT_WIDTH = 6, CELL_WIDTH = DIGIT_WIDTH + DOT_WIDTH;
    private static final int BASE_ADDR = 0x20000;
    protected Canvas canvas = new LEDDisplayCanvas();
    protected BufferedImage[] cellImages = new BufferedImage[16];
    protected BufferedImage[] dotImages = new BufferedImage[2];
    protected BufferedImage backingImage = new BufferedImage(DISPLAY_COLS * CELL_WIDTH, CELL_HEIGHT,
            BufferedImage.TYPE_INT_RGB);
    protected JFrame frame = new JFrame(getClass().getSimpleName());
    final protected Dimension displaySize = new Dimension(CELL_WIDTH * 4, CELL_HEIGHT);
    short store;
    protected Thread refresher;

    {
        try {
            for (int i = 0; i < 16; i++)
                cellImages[i] = ImageIO.read(new File(String.format("rsrc/64/%x.png", i)));
            dotImages[0] = ImageIO.read(new File("rsrc/64/dot_off.png"));
            dotImages[1] = ImageIO.read(new File("rsrc/64/dot_on.png"));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        canvas.setSize(displaySize);
        canvas.setBackground(Color.BLACK);

        frame.add(canvas);
        frame.getContentPane().setPreferredSize(displaySize);
        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        Graphics graphics = backingImage.getGraphics();
        putDigit(graphics, 0, 0);
        putDigit(graphics, 1, 0);
        putDigit(graphics, 2, 0);
        putDigit(graphics, 3, 0);

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

    private void putDigit(Graphics g, int pos, int digit) {
        int dx = (DISPLAY_COLS - pos - 1) * CELL_WIDTH;
        g.drawImage(cellImages[digit], dx, 0, dx + DIGIT_WIDTH, CELL_HEIGHT, 0, 0, DIGIT_WIDTH, CELL_HEIGHT, null);
    }

    private void putDot(Graphics g, int pos, boolean on) {
        int dx = (DISPLAY_COLS - pos - 1) * CELL_WIDTH;
        g.drawImage(dotImages[on ? 1 : 0], dx + DIGIT_WIDTH, 0, dx + CELL_WIDTH, CELL_HEIGHT, 0, 0, DOT_WIDTH,
                CELL_HEIGHT, null);
    }

    @SuppressWarnings("serial")
    public class LEDDisplayCanvas extends Canvas {
        @Override
        public void paint(Graphics g) {
            g.drawImage(backingImage, 0, 0, Color.BLACK, null);
        }
    }

    @Override
    public int fetch(int addr) {
        // Note that real hardware doesn't do this yet
        return store;
    }

    @Override
    public void store(int addr, int rhs) {
        Graphics g = backingImage.getGraphics();
        store = (short) (rhs & 0xffff);
        switch (addr - getMappedBase()) {
            case 0:
                for (int i = 0; i < 4; i++)
                    putDigit(g, i, (rhs >> (i * 4)) & 0xf);
                break;
            case 1:
                for (int i = 0; i < 4; i++)
                    putDot(g, i, ((rhs >> i) & 0x1) == 1);
                break;
            default:
                // Do nothing
                break;
        }
    }

    @Override
    public int getMappedBase() {
        return BASE_ADDR;
    }

    @Override
    public int getMappedLength() {
        return 2;
    }
}
