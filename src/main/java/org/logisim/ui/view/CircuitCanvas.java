package org.logisim.ui.view;

import org.logisim.business.ModelContracts;
import org.logisim.business.ModelContracts.ComponentId;
import org.logisim.business.ModelContracts.Signal;
import org.logisim.ui.model.CanvasModel;

import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;
import java.util.Objects;

/**
 * Visual canvas that renders components/connectors and forwards user gestures
 * to the hosting controller.
 */
public class CircuitCanvas extends JPanel {

    public enum ToolMode {
        IDLE,
        PLACE_COMPONENT,
        WIRE
    }

    public interface Controller {
        void requestComponentPlacement(Point canvasPoint);
        void requestConnectorCreation(PortRef source, PortRef sink, String color);
        void onComponentMoved(ComponentId id, Point newLocation);
        ToolMode currentTool();
        String currentWireColor();
        boolean handlePortClick(PortRef port);
    }

    private CanvasModel model;
    private java.beans.PropertyChangeListener modelListener;
    private final Controller controller;
    private PortRef pendingWire;
    private String selectedConnectorId;

    public CircuitCanvas(CanvasModel model, Controller controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
        setPreferredSize(new Dimension(2000, 1400));
        setModel(model);

        setBackground(Color.WHITE);
        setOpaque(true);
        ToolTipManager.sharedInstance().registerComponent(this);

        MouseHandler handler = new MouseHandler();
        addMouseListener(handler);
        addMouseMotionListener(handler);
    }

    public void setModel(CanvasModel model) {
        Objects.requireNonNull(model, "model");
        if (this.model != null && modelListener != null) {
            this.model.removePropertyChangeListener(modelListener);
        }
        this.model = model;
        modelListener = evt -> repaint();
        this.model.addPropertyChangeListener(modelListener);
        repaint();
    }

    public void cancelWirePreview() {
        pendingWire = null;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    public String getSelectedConnectorId() { return selectedConnectorId; }
    public void setSelectedConnectorId(String id) { this.selectedConnectorId = id; repaint(); }

    @Override
    public String getToolTipText(MouseEvent event) {
        var fig = model.findFigureAt(event.getPoint());
        if (fig == null) return null;
        ModelContracts.Component comp = fig.component();
        var state = comp.getState();
        return comp.getType() + " " + state;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawGrid(g2);
        drawConnectors(g2);
        drawComponents(g2);

        g2.dispose();
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(new Color(235, 235, 235));
        int step = 20;
        for (int x = 0; x < getWidth(); x += step) g2.drawLine(x, 0, x, getHeight());
        for (int y = 0; y < getHeight(); y += step) g2.drawLine(0, y, getWidth(), y);
    }

    private void drawComponents(Graphics2D g2) {
        for (var fig : model.getFigures()) {
            drawComponentShape(g2, fig);
            drawLabels(g2, fig);
            drawInputPlugs(g2, fig);
            drawOutputPlugs(g2, fig);
        }
    }

    private void drawComponentShape(Graphics2D g2, CanvasModel.ComponentFigure fig) {
        var bounds = fig.bounds();
        String type = fig.component().getType().toUpperCase();
        Color fill = new Color(253, 253, 253);
        Color border = Color.DARK_GRAY;
        g2.setStroke(new BasicStroke(2f));

        switch (type) {
            case "AND" -> drawAndGate(g2, bounds, fill, border);
            case "NAND" -> drawNandGate(g2, bounds, fill, border);
            case "OR" -> drawOrGate(g2, bounds, fill, border);
            case "NOR" -> drawNorGate(g2, bounds, fill, border);
            case "XOR" -> drawXorGate(g2, bounds, fill, border);
            case "NOT" -> drawNotGate(g2, bounds, fill, border);
            case "INPUT" -> drawInputSwitch(g2, bounds, fill, border);
            case "OUTPUT" -> drawOutputLamp(g2, bounds, fill, border, fig);
            default -> {
                g2.setColor(fill);
                g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 16, 16);
                g2.setColor(border);
                g2.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 16, 16);
            }
        }

        if (fig.component().getId().equals(model.getSelected())) {
            g2.setColor(new Color(92, 167, 232, 120));
            g2.fillRoundRect(bounds.x-3, bounds.y-3, bounds.width+6, bounds.height+6, 10, 10);
        }
    }

    private void drawAndGate(Graphics2D g2, java.awt.Rectangle b, Color fill, Color border) {
        int rectWidth = (int) (b.width * 0.5);
        g2.setColor(fill);
        g2.fillRect(b.x, b.y, rectWidth, b.height);
        g2.fillArc(b.x + rectWidth - b.height / 2, b.y, b.height, b.height, 270, 180);
        g2.setColor(border);
        g2.drawRect(b.x, b.y, rectWidth, b.height);
        g2.drawArc(b.x + rectWidth - b.height / 2, b.y, b.height, b.height, 270, 180);
    }

    private void drawOrGate(Graphics2D g2, java.awt.Rectangle b, Color fill, Color border) {
        Path2D path = new Path2D.Double();
        double leftCurve = b.width * 0.25;
        path.moveTo(b.x + leftCurve, b.y);
        path.quadTo(b.x, b.y + b.height / 2.0, b.x + leftCurve, b.y + b.height);
        path.quadTo(b.x + b.width, b.y + b.height, b.x + b.width, b.y + b.height / 2.0);
        path.quadTo(b.x + b.width, b.y, b.x + leftCurve, b.y);
        g2.setColor(fill);
        g2.fill(path);
        g2.setColor(border);
        g2.draw(path);
    }

    private void drawNotGate(Graphics2D g2, java.awt.Rectangle b, Color fill, Color border) {
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(b.x, b.y);
        triangle.lineTo(b.x, b.y + b.height);
        triangle.lineTo(b.x + b.width - 12, b.y + b.height / 2.0);
        triangle.closePath();
        g2.setColor(fill);
        g2.fill(triangle);
        g2.setColor(border);
        g2.draw(triangle);
        g2.draw(new Arc2D.Double(b.x + b.width - 18, b.y + b.height / 2.0 - 6, 12, 12, 0, 360, Arc2D.OPEN));
    }

    private void drawInputSwitch(Graphics2D g2, java.awt.Rectangle b, Color fill, Color border) {
        g2.setColor(fill);
        g2.fillOval(b.x, b.y, b.height, b.height);
        g2.setColor(border);
        g2.drawOval(b.x, b.y, b.height, b.height);
        g2.drawLine(b.x + b.height / 2, b.y, b.x + b.height, b.y - 12);
    }

    private void drawOutputLamp(Graphics2D g2, java.awt.Rectangle b, Color fill, Color border, CanvasModel.ComponentFigure fig) {
        g2.setColor(fill);
        g2.fillOval(b.x + b.width / 4, b.y, b.height, b.height);
        g2.setColor(border);
        g2.drawOval(b.x + b.width / 4, b.y, b.height, b.height);
        Signal state = fig.outputs().getOrDefault(0, Signal.UNDEFINED);
        g2.setColor(stateColor(state));
        g2.fillOval(b.x + b.width / 4 + 8, b.y + 8, b.height - 16, b.height - 16);
    }

    private void drawLabels(Graphics2D g2, CanvasModel.ComponentFigure fig) {
        String label = fig.label() == null ? fig.component().getType() : fig.label();
        g2.setColor(Color.DARK_GRAY);
        g2.drawString(label, fig.bounds().x + 6, fig.bounds().y - 6);
    }

    private void drawInputPlugs(Graphics2D g2, CanvasModel.ComponentFigure fig) {
        int inCount = fig.component().getInputs().size();
        for (int i = 0; i < inCount; i++) {
            Point anchor = getPortLocation(fig.bounds(), i, inCount, false);
            Signal state = fig.inputs().getOrDefault(i, Signal.UNDEFINED);
            g2.setColor(Color.DARK_GRAY);
            g2.drawLine(anchor.x - 16, anchor.y, anchor.x, anchor.y);
            g2.setColor(stateColor(state));
            g2.fillOval(anchor.x - 6, anchor.y - 6, 12, 12);
            g2.setColor(Color.DARK_GRAY);
            g2.drawOval(anchor.x - 6, anchor.y - 6, 12, 12);
        }
    }

    private void drawOutputPlugs(Graphics2D g2, CanvasModel.ComponentFigure fig) {
        int outCount = fig.component().getOutputs().size();
        for (int i = 0; i < outCount; i++) {
            Point anchor = getPortLocation(fig.bounds(), i, outCount, true);
            Signal state = fig.outputs().getOrDefault(i, Signal.UNDEFINED);
            g2.setColor(Color.DARK_GRAY);
            g2.drawLine(anchor.x, anchor.y, anchor.x + 16, anchor.y);
            g2.setColor(stateColor(state));
            g2.fillOval(anchor.x - 6, anchor.y - 6, 12, 12);
            g2.setColor(Color.DARK_GRAY);
            g2.drawOval(anchor.x - 6, anchor.y - 6, 12, 12);
        }
    }

    private Color stateColor(Signal signal) {
        return switch (signal) {
            case HIGH -> new Color(7, 161, 57);
            case LOW -> new Color(201, 75, 75);
            default -> Color.GRAY;
        };
    }

    private void drawConnectors(Graphics2D g2) {
        g2.setStroke(new BasicStroke(2f));
        for (var cf : model.getConnectorFigures()) {
            var conn = cf.connector();
            var srcFig = model.getFigure(conn.getSourceComponentId());
            var dstFig = model.getFigure(conn.getSinkComponentId());
            if (srcFig == null || dstFig == null) continue;
            Point src = getPortLocation(srcFig.bounds(), conn.getSourcePortIndex(), srcFig.component().getOutputs().size(), true);
            Point dst = getPortLocation(dstFig.bounds(), conn.getSinkPortIndex(), dstFig.component().getInputs().size(), false);
            if (conn.getId().equals(selectedConnectorId)) {
                g2.setColor(new Color(4, 130, 222));
                g2.setStroke(new BasicStroke(4f));
            } else {
                g2.setColor(parseColor(conn.getColor()));
                g2.setStroke(new BasicStroke(2f));
            }
            g2.draw(new CubicCurve2D.Double(src.x, src.y, src.x + 60, src.y, dst.x - 60, dst.y, dst.x, dst.y));
        }
        if (pendingWire != null) {
            Point src = getPortPoint(pendingWire);
            Point mouse = getMousePosition();
            if (src != null && mouse != null) {
                g2.setColor(parseColor(controller.currentWireColor()));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{4, 4}, 0));
                g2.drawLine(src.x, src.y, mouse.x, mouse.y);
            }
        }
    }

    private void drawNandGate(Graphics2D g2, java.awt.Rectangle b, Color fill, Color border) {
        drawAndGate(g2, b, fill, border);
        // draw bubble
        int cy = b.y + b.height / 2;
        int cx = b.x + b.width - 6;
        g2.setColor(Color.WHITE);
        g2.fillOval(cx-6, cy-6, 12, 12);
        g2.setColor(border);
        g2.drawOval(cx-6, cy-6, 12, 12);
    }
    private void drawNorGate(Graphics2D g2, java.awt.Rectangle b, Color fill, Color border) {
        drawOrGate(g2, b, fill, border);
        // draw bubble
        int cy = b.y + b.height / 2;
        int cx = b.x + b.width - 6;
        g2.setColor(Color.WHITE);
        g2.fillOval(cx-6, cy-6, 12, 12);
        g2.setColor(border);
        g2.drawOval(cx-6, cy-6, 12, 12);
    }
    private void drawXorGate(Graphics2D g2, java.awt.Rectangle b, Color fill, Color border) {
        // Draw second left curve (offset from OR)
        Path2D offset = new Path2D.Double();
        double offsetDist = b.width * 0.12;
        offset.moveTo(b.x + b.width * 0.18, b.y);
        offset.quadTo(b.x + offsetDist, b.y + b.height / 2.0, b.x + b.width * 0.18, b.y + b.height);
        g2.setColor(border);
        g2.draw(offset);
        drawOrGate(g2, b, fill, border);
    }

    private static Point getPortLocation(java.awt.Rectangle bounds, int idx, int total, boolean output) {
        int spacing = total + 1;
        int y = bounds.y + ((idx + 1) * bounds.height / spacing);
        int x = output ? bounds.x + bounds.width : bounds.x;
        return new Point(x, y);
    }

    private Point getPortPoint(PortRef ref) {
        var fig = model.getFigure(ref.componentId());
        if (fig == null) return null;
        int count = ref.output() ? fig.component().getOutputs().size() : fig.component().getInputs().size();
        return getPortLocation(fig.bounds(), ref.portIndex(), count, ref.output());
    }

    private Color parseColor(String value) {
        if (value == null) return Color.BLACK;
        return switch (value.toLowerCase()) {
            case "red" -> Color.RED;
            case "green" -> Color.GREEN.darker();
            case "blue" -> Color.BLUE;
            case "orange" -> Color.ORANGE;
            case "gray", "grey" -> Color.GRAY;
            default -> Color.BLACK;
        };
    }

    private class MouseHandler extends MouseAdapter {
        private ComponentId draggingId;
        private Point dragOffset;

        @Override
        public void mousePressed(MouseEvent e) {
            if (controller.currentTool() == ToolMode.WIRE) return;
            var fig = model.findFigureAt(e.getPoint());
            if (fig != null) {
                draggingId = fig.component().getId();
                dragOffset = new Point(e.getX() - fig.bounds().x, e.getY() - fig.bounds().y);
                model.setSelected(draggingId);
            } else {
                model.setSelected(null);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            draggingId = null;
            dragOffset = null;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (draggingId == null || dragOffset == null) return;
            Point newLoc = new Point(e.getX() - dragOffset.x, e.getY() - dragOffset.y);
            controller.onComponentMoved(draggingId, snap(newLoc));
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            ToolMode mode = controller.currentTool();
            if (mode == ToolMode.PLACE_COMPONENT) {
                controller.requestComponentPlacement(snap(e.getPoint()));
                return;
            }
            if (mode != ToolMode.WIRE) {
                PortRef port = findPort(e.getPoint());
                if (port != null && controller.handlePortClick(port)) {
                    setSelectedConnectorId(null);
                    return;
                }
                // If clicked on a wire, select it
                String clickedWire = findConnectorAt(e.getPoint());
                setSelectedConnectorId(clickedWire);
            } else {
                PortRef port = findPort(e.getPoint());
                if (port == null) return;
                if (pendingWire == null) {
                    pendingWire = port;
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                } else {
                    if (pendingWire.output() == port.output()) {
                        pendingWire = null;
                        setCursor(Cursor.getDefaultCursor());
                        return;
                    }
                    PortRef src = pendingWire.output() ? pendingWire : port;
                    PortRef dst = pendingWire.output() ? port : pendingWire;
                    controller.requestConnectorCreation(src, dst, controller.currentWireColor());
                    pendingWire = null;
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        }
    }

    private Point snap(Point p) {
        int grid = 10;
        return new Point((p.x / grid) * grid, (p.y / grid) * grid);
    }

    private PortRef findPort(Point location) {
        for (var fig : model.getFigures()) {
            int inCount = fig.component().getInputs().size();
            for (int i = 0; i < inCount; i++) {
                Point p = getPortLocation(fig.bounds(), i, inCount, false);
                if (p.distance(location) <= 10) return new PortRef(fig.component().getId(), i, false);
            }
            int outCount = fig.component().getOutputs().size();
            for (int i = 0; i < outCount; i++) {
                Point p = getPortLocation(fig.bounds(), i, outCount, true);
                if (p.distance(location) <= 10) return new PortRef(fig.component().getId(), i, true);
            }
        }
        return null;
    }

    // Utility: find connector at screen point (within 10 px) for selection
    private String findConnectorAt(Point p) {
        for (var cf : model.getConnectorFigures()) {
            var conn = cf.connector();
            var srcFig = model.getFigure(conn.getSourceComponentId());
            var dstFig = model.getFigure(conn.getSinkComponentId());
            if (srcFig == null || dstFig == null) continue;
            Point src = getPortLocation(srcFig.bounds(), conn.getSourcePortIndex(), srcFig.component().getOutputs().size(), true);
            Point dst = getPortLocation(dstFig.bounds(), conn.getSinkPortIndex(), dstFig.component().getInputs().size(), false);
            // Check if click is within 10px of curve midpoint (approx)
            int mx = (src.x + dst.x) / 2;
            int my = (src.y + dst.y) / 2;
            if (p.distance(mx, my) <= 12) return conn.getId();
        }
        return null;
    }

    /**
     * Lightweight description of a selected port, used for wiring gestures.
     */
    public record PortRef(ComponentId componentId, int portIndex, boolean output) { }
}

