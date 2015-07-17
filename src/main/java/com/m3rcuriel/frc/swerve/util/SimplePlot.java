package com.m3rcuriel.frc.swerve.util;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.LinkedList;

public class SimplePlot extends javax.swing.JPanel implements ClipboardOwner {

    private final int yPAD = 60;
    private final int xPAD = 70;

    private double upperXtic;
    private double lowerXtic;
    private double upperYtic;
    private double lowerYtic;

    private double yMax;
    private double yMin;
    private double xMax;
    private double xMin;

    private double xTicStepSize;
    private double yTicStepSize;

    boolean userSetYTic;
    boolean userSetXTic;

    private String xAxisLabel;
    private String yAxisLabel;

    private String titleLabel;
    protected static int count = 0;

    public JFrame plotFrame;

    private JPopupMenu menu = new JPopupMenu("Popup");

    private LinkedList<CartesianNode> nodeList;

    public SimplePlot(double[] xData, double[] yData, Color lineColor, Color markerColor) {
        xAxisLabel = "X axis";
        yAxisLabel = "Y axis";

        upperXtic = -Double.MAX_VALUE;
        lowerXtic = Double.MAX_VALUE;
        upperYtic = -Double.MAX_VALUE;
        lowerYtic = Double.MAX_VALUE;

        this.userSetXTic = false;
        this.userSetYTic = false;
        //TODO allow user tics to limit window size
        nodeList = new LinkedList<>();

        addData(xData, yData, lineColor, markerColor);

        count++;
        plotFrame = new JFrame("Figure " + count);
        plotFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        titleLabel = plotFrame.getTitle();
        plotFrame.add(this);
        plotFrame.setSize(600, 600);
        plotFrame.setLocationByPlatform(true);

        menu(plotFrame, this);
    }

    public void showPlot() {
        plotFrame.setVisible(!plotFrame.isVisible());
    }

    public void showPlot(boolean show) {
        plotFrame.setVisible(show);
    }

    public void addData(double[] x, double[] y, Color lineColor, Color marker) {
        CartesianNode data = new CartesianNode();

        data.lineColor = lineColor;

        if(marker == null)
            data.lineMarker = false;
        else {
            data.lineMarker = true;
            data.markerColor = marker;
        }

        data.y = y.clone();

        if(x != null) {
            for(CartesianNode node: nodeList)
                if(node.x == null)
                    throw new Error("Previous series must have x and y data");

            if(x.length != y.length) {
                throw new Error("Dimensions of X and Y must match");
            }

            data.x = x.clone();
        }

        nodeList.add(data);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        Line2D.Double axis_Y = new Line2D.Double(xPAD, yPAD, xPAD, h - yPAD);
        Line2D.Double axis_X = new Line2D.Double(xPAD, h - yPAD, w - xPAD, h - yPAD);
        g2.draw(axis_X);
        g2.draw(axis_Y);

        getMinMax(nodeList);

        drawYTicks(g2, axis_Y, 15, yMax, yMin);
        drawXTicks(g2, axis_X, 15, xMax, xMin);

        plot(g2);

        setXLabel(g2, xAxisLabel);
        setYLabel(g2, yAxisLabel);
        setTitle(g2, titleLabel);
    }

    private void plot(Graphics2D g2) {
        int w = super.getWidth();
        int h = super.getHeight();

        Color tempColor = g2.getColor();

        for(CartesianNode node : nodeList)  {
            double xScale = (double) (w - 2 * xPAD) / upperXtic - lowerXtic;
            double yScale = (double) (h - 2 * yPAD) / upperYtic - lowerYtic;

            for(int j = 0; j < node.y.length - 1; j++) {
                double x1, x2;

                if(node.x == null) {
                    x1 = xPAD + j * xScale;
                    x2 = xPAD + (j + 1) * xScale;
                } else {
                    x1 = xPAD + xScale * node.x[j] + lowerXtic * xScale;
                    x2 = xPAD + xScale * node.x[j + 1] + lowerXtic * xScale;
                }
                double y1 = h - yPAD - yScale * node.y[j] + lowerYtic * yScale;
                double y2 = h - yPAD - yScale * node.y[j + 1] + lowerYtic * yScale;
                g2.setPaint(node.lineColor);
                g2.draw(new Line2D.Double(x1, y1, x2, y2));

                if(node.lineMarker) {
                    g2.setPaint(node.markerColor);
                    g2.fill(new Ellipse2D.Double(x1 - 2, y1 - 2, 4, 4));
                    g2.fill(new Ellipse2D.Double(x2 - 2, y2 - 2, 4, 4));
                }
            }
        }
        g2.setColor(tempColor);
    }

    public void setYLabel(String s) {
        yAxisLabel = s;
    }

    public void setXLabel(String s) {
        xAxisLabel = s;
    }

    public void setTitle(String s) {
        titleLabel = s;
    }

    private void setYLabel(Graphics2D g2, String s) {
        FontMetrics fm = getFontMetrics(getFont());
        int width = fm.stringWidth(s);

        AffineTransform temp = g2.getTransform();

        AffineTransform at = new AffineTransform();

        at.setToRotation(-Math.PI / 2, 10, getHeight() / 2 + width / 2);
        g2.setTransform(at);

        g2.drawString(s, 10, 7 + getHeight() / 2 + width / 2);

        g2.setTransform(temp);
    }

    private void setXLabel(Graphics2D g2, String s) {
        FontMetrics fm = getFontMetrics(getFont());
        int width = fm.stringWidth(s);

        g2.drawString(s, getWidth() / 2 - width / 2, getHeight() - 10);
    }

    private void setTitle(Graphics2D g2, String s) {
        FontMetrics fm = getFontMetrics(getFont());

        String[] lines = s.split("\n");

        int height = xPAD / 2 - ((lines.length - 1) * fm.getHeight());

        for (String line : lines) {
            int width = fm.stringWidth(line);
            g2.drawString(line, getWidth() / 2 - width / 2, height);
            height += fm.getHeight();
        }
    }

    private void drawYTicks(Graphics2D g2, Line2D axis_Y, int tickCount, double Max, double Min) {
        if(!userSetYTic) {
            double range = Max - Min;

            double unroundedTickSize = range / (tickCount - 1);
            double x = Math.ceil(Math.log10(unroundedTickSize) - 1);
            double pow10x = Math.pow(10, x);
            yTicStepSize = Math.ceil(unroundedTickSize / pow10x) * pow10x;

            if (Min < 0)
                lowerYtic = yTicStepSize * Math.floor(Min / yTicStepSize);
            else
                lowerYtic = yTicStepSize * Math.ceil(Min / yTicStepSize);

            if (Max < 0)
                upperYtic = yTicStepSize * Math.floor(1 + Max / yTicStepSize);
            else
                upperYtic = yTicStepSize * Math.ceil(1 + Max / yTicStepSize);
        }

        double x0 = axis_Y.getX1();
        double y0 = axis_Y.getY1();
        double xf = axis_Y.getX2();
        double yf = axis_Y.getY2();

        int roundedTicks = (int) ((upperYtic - lowerYtic) / yTicStepSize);
        double distance = Math.sqrt(Math.pow(xf - x0, 2) + Math.pow(yf - y0, 2)) / roundedTicks;

        double upper = upperYtic;
        for (int i = 0; i <= roundedTicks; i++) {
            double newY = y0;

            String number = new DecimalFormat("#.#").format(upper);
            FontMetrics fm = getFontMetrics(getFont());
            int width = fm.stringWidth(number);

            g2.draw(new Line2D.Double(x0, newY, x0 - 10, newY));
            g2.drawString(number, (float) x0 - 15 - width, (float) newY);

            upper = upper - yTicStepSize;
            y0 = newY + distance;
        }
    }

    private void drawXTicks(Graphics2D g2, Line2D axis_X, int tickCount, double Max, double Min) {
        drawXTicks(g2, axis_X, tickCount, Max, Min, 1);
    }

    private void drawXTicks(Graphics2D g2, Line2D axis_X, int tickCount, double Max, double Min, double skip) {
        if(!userSetXTic) {
            double range = Max - Min;

            double unroundedTickSize = range / (tickCount - 1);
            double x = Math.ceil(Math.log10(unroundedTickSize) - 1);
            double pow10x = Math.pow(10, x);
            xTicStepSize = Math.ceil(unroundedTickSize / pow10x) * pow10x;
            //TODO fix whatever the fuck this is to allow more accurate ticks

            if (Min < 0) {
                lowerXtic = xTicStepSize * Math.floor(Min / xTicStepSize);
            } else {
                lowerXtic = xTicStepSize * Math.ceil(Min / xTicStepSize);
            }

            if (Max < 0) {
                upperXtic = xTicStepSize * Math.floor(1 + Max / xTicStepSize);
            } else {
                upperXtic = xTicStepSize * Math.ceil(1 + Max / xTicStepSize);
            }
        }
        double x0 = axis_X.getX1();
        double y0 = axis_X.getY1();
        double xf = axis_X.getX2();
        double yf = axis_X.getY2();

        int roundedTicks = (int) ((upperXtic - lowerXtic) / xTicStepSize);

        double distance = Math.sqrt(Math.pow(xf - x0, 2) + Math.pow(yf - y0, 2)) / roundedTicks;

        double lower = lowerXtic;
        for (int i = 0; i <= roundedTicks; i++) {
            double newX = x0;

            String number = new DecimalFormat("#.#").format(lower);
            FontMetrics fm = getFontMetrics(getFont());
            int width = fm.stringWidth(number);

            g2.draw(new Line2D.Double(newX, yf, newX, yf + 10));

            if (i % skip == 0) {
                g2.drawString(number, (float) (newX - (width / 2.0)), (float) yf + 25);
            }

            lower = lower + xTicStepSize;
            x0 = newX + distance;
        }
    }
    public void setXTic(double lowerBound, double upperBound, double stepSize) {
        this.userSetXTic = true;

        this.upperXtic = upperBound;
        this.lowerXtic = lowerBound;
        this.xTicStepSize = stepSize;
    }

    public void setYTic(double lowerBound, double upperBound, double stepSize) {
        this.userSetYTic = true;

        this.upperYtic = upperBound;
        this.lowerYtic = lowerBound;
        this.yTicStepSize = stepSize;
    }

    private void getMinMax(LinkedList<CartesianNode> list) {
        for(CartesianNode node: list) {
            double yNodeMax = getMax(node.y);
            double yNodeMin = getMin(node.y);

            if(yNodeMin < yMin) {
                yMin = yNodeMin;
            }

            if(yNodeMax > yMax) {
                yMax = yNodeMax;
            }

            if(node.x != null) {
                double xNodeMax = getMax(node.x);
                double xNodeMin = getMin(node.x);
                if(xNodeMin < xMin) {
                    xMin = xNodeMin;
                }

                if(xNodeMax > xMax) {
                    xMax = xNodeMax;
                }
            } else {
                xMax = node.y.length - 1;
                xMin = 0;
            }
        }
    }

    private double getMax(double[] data) {
        double max = -Double.MAX_VALUE;
        for(int i = 0; i < data.length; i++) {
            if(data[i] > max)
                max = data[i];
        }
        return max;
    }

    private double getMin(double[] data) {
        double min = Double.MAX_VALUE;
        for(int i = 0; i < data.length; i++) {
            if(data[i] < min)
                min = data[i];
        }
        return min;
    }

    @Override
    public void lostOwnership(Clipboard c, Transferable tRansferable) {

    }
    
    private void menu(JFrame frame, final SimplePlot plot) {
        frame.addMouseListener(new PopupTriggerListener());

        JMenuItem item = new JMenuItem("Copy graph");

        item.addActionListener(ae -> {
                BufferedImage i = new BufferedImage(plot.getSize().width, plot.getSize().height, BufferedImage.TRANSLUCENT);
                plot.setOpaque(false);
                plot.paint(i.createGraphics());
                TransferableImage trans = new TransferableImage(i);
                Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
                c.setContents(trans, plot);
            }
        );

        menu.add(item);

        item = new JMenuItem("Desktop ScreenShot");

        item.addActionListener(ae -> {
                try {
                    Robot robot = new Robot();
                    Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                    BufferedImage i = robot.createScreenCapture(screen);
                    TransferableImage trans = new TransferableImage(i);
                    Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
                    c.setContents(trans, plot);
                } catch (AWTException ex) {
                    ex.printStackTrace();
                    System.exit(1);
                }
            }
        );

        menu.add(item);
    }

    class PopupTriggerListener extends MouseAdapter {
        public void mousePressed(MouseEvent event) {
            if(event.isPopupTrigger()) {
                menu.show(event.getComponent(), event.getX(), event.getY());
            }
        }

        public void mouseReleased(MouseEvent event) {
            if(event.isPopupTrigger()) {
                menu.show(event.getComponent(), event.getX(), event.getY());
            }
        }

        public void mouseClicked(MouseEvent event) {

        }
    }

    private class CartesianNode {
        double[] x;
        double[] y;
        Color lineColor;

        boolean lineMarker;
        Color markerColor;

        public CartesianNode() {
            x = y = null;
            lineMarker = false;
        }
    }

    private class TransferableImage implements Transferable {
        Image i;

        public TransferableImage(Image i) {
            this.i = i;
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if(flavor.equals(DataFlavor.imageFlavor) && i != null) {
                return i;
            } else {
                throw new UnsupportedFlavorException(flavor);
            }
        }

        public DataFlavor[] getTransferDataFlavors() {
            DataFlavor[] flavors = {DataFlavor.imageFlavor};
            return flavors;
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            DataFlavor[] flavors = getTransferDataFlavors();
            for(DataFlavor gFlavor : flavors) {
                if(flavor.equals(gFlavor)) {
                    return true;
                }
            }
            return false;
        }
    }
}