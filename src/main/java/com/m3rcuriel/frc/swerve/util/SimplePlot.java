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
import java.util.LinkedList;

/**
 * This class plots basic graphs using the Java AWT interface. It allows the user to plot many data series on one
 * figure, control scale, and set colors
 *
 * The purpose is to allow the user to view graphical data quickly or in real time, rather than exporting to CSV or
 * Matlab. Small and simple graphs are ideal.
 *
 * Additionally, the user can capture screenshots from the right click menu and copy and paste into reports or documents
 * quickly.
 *
 * This class currently only supports scattered line charts
 */
public class SimplePlot extends javax.swing.JPanel implements ClipboardOwner {

    private final int yPAD = 60; // amount to pad the plot vertically
    private final int xPAD = 70; // amount to pad the plot horizontally

    private double upperXtic; // highest tick value for x
    private double lowerXtic; // lowest tick value for x
    private double upperYtic;
    private double lowerYtic;

    private double yMax; // maximum y value
    private double yMin; // minimum y value
    private double xMax;
    private double xMin;

    private double xTicStepSize; // distance between ticks in x
    private double yTicStepSize;

    boolean userSetYTic; // user manually set tick distance
    boolean userSetXTic;

    private String xAxisLabel; // stores x axis string
    private String yAxisLabel;

    private String titleLabel; // stores title string
    protected static int count = 0; // counts instances of the plot to increment title

    public JFrame plotFrame; // JFrame for containing the plot itself

    private JPopupMenu menu = new JPopupMenu("Popup"); // right click popup menu

    private LinkedList<DataSeries> nodeList; // LinkedList which contains all data series

    /******************************************************************
     * Full constructor for SimplePlot taking data and color information
     * @param xData an array of doubles containing data in the x axis (may be null)
     * @param yData an array of doubles containing data in the y axis
     * @param lineColor color used for drawing connecting lines
     * @param markerColor color used for drawing dots (may be null)
     */
    public SimplePlot(double[] xData, double[] yData, Color lineColor, Color markerColor) {
        xAxisLabel = "X axis";
        yAxisLabel = "Y axis";

        // Initialize all max/min values to absolute max/min
        upperXtic = -Double.MAX_VALUE;
        lowerXtic = Double.MAX_VALUE;
        upperYtic = -Double.MAX_VALUE;
        lowerYtic = Double.MAX_VALUE;

        this.userSetXTic = false;
        this.userSetYTic = false;
        //TODO allow user tics to limit window size
        nodeList = new LinkedList<>();

        addData(xData, yData, lineColor, markerColor); // add initial series to graph

        count++; // increment instance count (statically)
        plotFrame = new JFrame("Figure " + count);
        plotFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); // prevent windows from sticking around in background
        titleLabel = plotFrame.getTitle(); // default title matches window title
        plotFrame.add(this); // add plot frame to window
        plotFrame.setSize(600, 600);
        plotFrame.setLocationByPlatform(true);

        menu(plotFrame, this); // initialize the context menu
    }

    /**
     * Toggle the visibility of a constructed plot object
     */
    public void showPlot() {
        plotFrame.setVisible(!plotFrame.isVisible());
    }

    /**
     * Set the visibility of a constructed plot object
     * @param show the visibility of the plot
     */
    public void showPlot(boolean show) {
        plotFrame.setVisible(show);
    }

    /**
     * Add a data series to the plot
     * @param x the series representing all x values (may be null)
     * @param y the series representing all y values
     * @param lineColor color used for drawing connecting lines
     * @param marker color used for drawing dots (may be null)
     */
    public void addData(double[] x, double[] y, Color lineColor, Color marker) {
        DataSeries data = new DataSeries(); // initialize data series object

        data.lineColor = lineColor;

        if(marker == null) // don't mark if we don't have to
            data.lineMarker = false;
        else {
            data.lineMarker = true;
            data.markerColor = marker;
        }

        data.y = y.clone(); // copy y data into DataSeries object

        if(x != null) {
            // can't add y data unless other data has x and y data
            for(DataSeries node: nodeList)
                if(node.x == null)
                    throw new Error("Previous series must have x and y data");

            //x and y data must match
            if(x.length != y.length) {
                throw new Error("Dimensions of X and Y must match");
            }

            // copy x values into DataSeries
            data.x = x.clone();
        }

        nodeList.add(data);
    }

    /**
     * called in a loop by swing to paint the graphics component
     * @param g the graphics context to render in
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // just in case
        Graphics2D g2 = (Graphics2D) g; // we're working in 2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // turn on antialiasing

        int w = getWidth();
        int h = getHeight();

        Line2D.Double axis_Y = new Line2D.Double(xPAD, yPAD, xPAD, h - yPAD); // initialize y axis line
        Line2D.Double axis_X = new Line2D.Double(xPAD, h - yPAD, w - xPAD, h - yPAD);
        g2.draw(axis_X); // draw x axis line
        g2.draw(axis_Y);

        getMinMax(nodeList); // store maximum and minimum values in the entire nodeList

        drawYTicks(g2, axis_Y, 15, yMax, yMin); // draw ticks for the y axis
        drawXTicks(g2, axis_X, 15, xMax, xMin);

        plot(g2); // plot all data

        setXLabel(g2, xAxisLabel);
        setYLabel(g2, yAxisLabel);
        setTitle(g2, titleLabel);
    }

    /**
     * Plot the data onto the graph using a Graphics2D object
     * @param g2 the relevant Graphics2D object
     */
    private void plot(Graphics2D g2) {
        int w = super.getWidth();
        int h = super.getHeight();

        Color tempColor = g2.getColor(); // store color so we can come back to it just in case

        for(DataSeries node : nodeList)  {
            double xScale = (double) (w - 2 * xPAD) / upperXtic - lowerXtic; // define scale based on GUI parameters
            double yScale = (double) (h - 2 * yPAD) / upperYtic - lowerYtic;

            for(int j = 0; j < node.y.length - 1; j++) {
                double x1, x2;

                if(node.x == null) {
                    x1 = xPAD + j * xScale; // steadily increment x (pixels) if x is not defined
                    x2 = xPAD + (j + 1) * xScale;
                } else {
                    x1 = xPAD + xScale * node.x[j] + lowerXtic * xScale; // scale each x value into pixels
                    x2 = xPAD + xScale * node.x[j + 1] + lowerXtic * xScale;
                }
                double y1 = h - yPAD - yScale * node.y[j] + lowerYtic * yScale; // scale y values into pixels
                double y2 = h - yPAD - yScale * node.y[j + 1] + lowerYtic * yScale;
                g2.setPaint(node.lineColor); // set color to the line painting color
                g2.draw(new Line2D.Double(x1, y1, x2, y2)); // draw a line between the two points

                // if the user wants, fill in the two endpoints of the line
                if(node.lineMarker) {
                    g2.setPaint(node.markerColor);
                    g2.fill(new Ellipse2D.Double(x1 - 2, y1 - 2, 4, 4)); // draw an ellipse
                    g2.fill(new Ellipse2D.Double(x2 - 2, y2 - 2, 4, 4));
                }
            }
        }
        g2.setColor(tempColor); // reset color
    }

    /**
     * Change the label on the Y Axis
     * @param s the new label for the y axis
     */
    public void setYLabel(String s) {
        yAxisLabel = s;
    }

    /**
     * Change the label on the X Axis
     * @param s the new label for the x axis
     */
    public void setXLabel(String s) {
        xAxisLabel = s;
    }

    /**
     * Change the title for the graph
     * @param s the new graph title
     */
    public void setTitle(String s) {
        titleLabel = s;
    }

    /**
     * Draws Y axis label from a string with proper spacing
     * @param g2 the Graphics2D instance to draw under
     * @param s the Y axis label to draw
     */
    private void setYLabel(Graphics2D g2, String s) {
        FontMetrics fm = getFontMetrics(getFont()); // get the relevant font information
        int width = fm.stringWidth(s);  // determine string width in pixels from font

        AffineTransform temp = g2.getTransform(); // store the current transform

        AffineTransform at = new AffineTransform();

        at.setToRotation(-Math.PI / 2, 10, getHeight() / 2 + width / 2); // create rotation transform (90 degrees)
        g2.setTransform(at); // apply rotation transform

        g2.drawString(s, 10, 7 + getHeight() / 2 + width / 2); // draw string under transform

        g2.setTransform(temp); // reset transform
    }

    /**
     * Draws X axis label from a string with proper spacing
     * @param g2 the Graphics2D instance to draw under
     * @param s the X axis label to draw
     */
    private void setXLabel(Graphics2D g2, String s) {
        FontMetrics fm = getFontMetrics(getFont());
        int width = fm.stringWidth(s);

        g2.drawString(s, getWidth() / 2 - width / 2, getHeight() - 10);

        // no transform needed because this text is horizontal
    }

    /**
     * Draws title from a string with proper spacing
     * @param g2 the Graphics2D instance to draw under
     * @param s the title string to draw (split on \n)
     */
    private void setTitle(Graphics2D g2, String s) {
        FontMetrics fm = getFontMetrics(getFont());

        String[] lines = s.split("\n"); // break the title into lines

        int height = xPAD / 2 - ((lines.length - 1) * fm.getHeight()); // calculate total height

        for (String line : lines) {
            int width = fm.stringWidth(line);
            g2.drawString(line, getWidth() / 2 - width / 2, height); // draw each line
            height += fm.getHeight(); // change pixel value to draw next line
        }
    }

    /**
     * Draw the ticks on the Y axis to define the scale
     * @param g2 the Graphics2D instance to draw with
     * @param axis_Y a 2D Line defining the axis itself
     * @param tickCount total number of ticks in the axis
     * @param Max maximum value in y
     * @param Min minimum value in y
     */
    private void drawYTicks(Graphics2D g2, Line2D axis_Y, int tickCount, double Max, double Min) {
        if(!userSetYTic) {
            double range = Max - Min; // define a range

            // perform sketchy rounding
            double unroundedTickSize = range / (tickCount - 1);
            double x = Math.ceil(Math.log10(unroundedTickSize) - 1);
            double pow10x = Math.pow(10, x);
            yTicStepSize = Math.ceil(unroundedTickSize / pow10x) * pow10x;

            // find lowest tick
            if (Min < 0)
                lowerYtic = yTicStepSize * Math.floor(Min / yTicStepSize);
            else
                lowerYtic = yTicStepSize * Math.ceil(Min / yTicStepSize);

            // find highest tick
            if (Max < 0)
                upperYtic = yTicStepSize * Math.floor(1 + Max / yTicStepSize);
            else
                upperYtic = yTicStepSize * Math.ceil(1 + Max / yTicStepSize);
        }

        // store line endpoints
        double x0 = axis_Y.getX1();
        double y0 = axis_Y.getY1();
        double xf = axis_Y.getX2();
        double yf = axis_Y.getY2();

        int roundedTicks = (int) ((upperYtic - lowerYtic) / yTicStepSize); // find total tick number after rounding
        double distance = Math.sqrt(Math.pow(xf - x0, 2) + Math.pow(yf - y0, 2)) / roundedTicks; // calculate pixel distance

        double upper = upperYtic;
        for (int i = 0; i <= roundedTicks; i++) {
            double newY = y0;

            String number = new DecimalFormat("#.#").format(upper); // format each tick into a standard
            //TODO fix this to use sig figs instead
            FontMetrics fm = getFontMetrics(getFont());
            int width = fm.stringWidth(number);

            g2.draw(new Line2D.Double(x0, newY, x0 - 10, newY));
            g2.drawString(number, (float) x0 - 15 - width, (float) newY); // draw each string along the line

            upper = upper - yTicStepSize;
            y0 = newY + distance; // move up the graph to draw each line
        }
    }

    /**
     * Works like {@link #drawXTicks(Graphics2D, Line2D, int, double, double, double)} but without a skip
     */
    private void drawXTicks(Graphics2D g2, Line2D axis_X, int tickCount, double Max, double Min) {
        drawXTicks(g2, axis_X, tickCount, Max, Min, 1);
    }

    /**
     * Draw the ticks on the X axis to define the scale
     * @param g2 the Graphics2D instance to draw with
     * @param axis_X a 2D Line defining the axis itself
     * @param tickCount total number of ticks in the axis
     * @param Max maximum value in x
     * @param Min minimum value in x
     * @param skip the distance between ticks to draw for readability
     */
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

    /**
     * Sets the x ticks manually
     * @param lowerBound the minimum tick value
     * @param upperBound the maximum tick value
     * @param stepSize step size of the ticks
     */
    public void setXTic(double lowerBound, double upperBound, double stepSize) {
        this.userSetXTic = true;

        this.upperXtic = upperBound;
        this.lowerXtic = lowerBound;
        this.xTicStepSize = stepSize;
    }

    /**
     * Sets the y ticks manually
     * @param lowerBound the minimum tick value
     * @param upperBound the maximum tick value
     * @param stepSize step size of the ticks
     */
    public void setYTic(double lowerBound, double upperBound, double stepSize) {
        this.userSetYTic = true;

        this.upperYtic = upperBound;
        this.lowerYtic = lowerBound;
        this.yTicStepSize = stepSize;
    }

    /**
     * Stores the minimum and maximum values of the entire linked list
     * @param list the list to get the max and min values from
     */
    private void getMinMax(LinkedList<DataSeries> list) {
        for(DataSeries node: list) {
            double yNodeMax = getMax(node.y);
            double yNodeMin = getMin(node.y);

            if(yNodeMin < yMin) { // finding lowest possible
                yMin = yNodeMin;
            }

            if(yNodeMax > yMax) {
                yMax = yNodeMax;
            }

            // finding lowest possible x (if we have x)
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

    /**
     * Gets the maximum of an array
     * @param data the array
     * @return the maximum value
     */
    private double getMax(double[] data) {
        double max = -Double.MAX_VALUE;
        for(double d : data) {
            if(d > max)
                max = d;
        }
        return max;
    }

    /**
     * Gets the minimum of an array
     * @param data the array
     * @return the minimum value
     */
    private double getMin(double[] data) {
        double min = Double.MAX_VALUE;
        for(double d : data) {
            if(d < min)
                min = d;
        }
        return min;
    }

    /**
     * Called if we lose clipboard ownership
     * @param c clipboard we lost ownership of
     * @param transferable // object in clipboard
     */
    @Override
    public void lostOwnership(Clipboard c, Transferable transferable) {
        // we don't care if the user adds something else to clipboard
    }

    /**
     * Initializes the right click menu
     * @param frame parent JFrame to load the menu in
     * @param plot the SimplePlot object to right click
     */
    private void menu(JFrame frame, final SimplePlot plot) {
        frame.addMouseListener(new PopupTriggerListener()); // add custom mouse listener

        JMenuItem item = new JMenuItem("Copy graph"); // add item that copies the graph

        // create lambda function as action listener
        // equivalent to new ActionListener() { actionPerformed(ActionEvent ae) {...}};
        item.addActionListener(ae -> {
                BufferedImage i = new BufferedImage(plot.getSize().width, plot.getSize().height, BufferedImage.TRANSLUCENT);  // create blank BufferedImage
                plot.setOpaque(false); // remove the background from the plot
                plot.paint(i.createGraphics()); // paint the plot
                TransferableImage trans = new TransferableImage(i); // create wrapped Image we can put in a clipboard
                Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard(); // get clipboard
                c.setContents(trans, plot); // put image in clipboard
            }
        );

        menu.add(item);

        item = new JMenuItem("Desktop ScreenShot"); // add item that takes a whole-computer screenshot

        // see above
        item.addActionListener(ae -> {
                try {
                    Robot robot = new Robot(); // screenshot tool heh
                    Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()); // get full screen size
                    BufferedImage i = robot.createScreenCapture(screen); // capture screen into BufferedImage
                    TransferableImage trans = new TransferableImage(i); // wrap to store in clip board
                    Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
                    c.setContents(trans, plot);
                } catch (AWTException ex) { // handle exceptions
                    ex.printStackTrace();
                    System.exit(1);
                }
            }
        );

        menu.add(item);
    }

    /**
     * This inner class is a hidden listener to register mouse clicks and show the context menu
     */
    class PopupTriggerListener extends MouseAdapter {
        public void mousePressed(MouseEvent event) {
            if(event.isPopupTrigger()) { // check if this event should show a pop up
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

    /**
     * Private class defining the structure of a data series
     */
    private class DataSeries {
        double[] x;
        double[] y;
        Color lineColor;

        boolean lineMarker;
        Color markerColor;

        public DataSeries() {
            x = y = null;
            lineMarker = false;
        }
    }

    /**
     * Wrapper around a BufferedImage in order to store it in clipboard
     */
    private class TransferableImage implements Transferable {
        Image i;

        public TransferableImage(Image i) {
            this.i = i;
        }

        // Overriding stuff
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