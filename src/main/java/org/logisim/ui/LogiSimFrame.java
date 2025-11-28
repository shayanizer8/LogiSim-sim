package org.logisim.ui;

import org.logisim.business.BasicConnector;
import org.logisim.business.InMemoryCircuit;
import org.logisim.business.BooleanExpressionGenerator;
import org.logisim.business.CircuitComponent;
import org.logisim.business.InputPin;
import org.logisim.business.OutputPin;
import org.logisim.business.ModelContracts.ComponentId;
import org.logisim.business.ModelContracts.ModelChangeListener;
import org.logisim.business.ModelContracts.Signal;
import org.logisim.business.ModelContracts.SimulationListener;
import org.logisim.business.ModelContracts;
import org.logisim.business.ProjectImpl;
import org.logisim.business.SimulationEngineImpl;
import org.logisim.business.TruthTableGenerator;
import org.logisim.ui.model.CanvasModel;
import org.logisim.ui.persistence.ProjectPersistenceService;
import org.logisim.ui.view.BooleanExpressionDialog;
import org.logisim.ui.view.CircuitCanvas;
import org.logisim.ui.view.CircuitCanvas.PortRef;
import org.logisim.ui.view.CircuitCanvas.ToolMode;
import org.logisim.ui.view.PalettePanel;
import org.logisim.ui.view.SimulationPanel;
import org.logisim.ui.view.TruthTableDialog;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Main Swing frame orchestrating palette, canvas, simulation controls and persistence actions.
 */
public class LogiSimFrame extends JFrame implements
        ModelChangeListener,
        SimulationListener,
        CircuitCanvas.Controller,
        PalettePanel.Listener {

    private static final Logger LOGGER = Logger.getLogger(LogiSimFrame.class.getName());

    private final ProjectImpl project;
    private final SimulationEngineImpl engine;
    private final ProjectPersistenceService persistenceService = new ProjectPersistenceService();

    private final DefaultListModel<String> circuitListModel = new DefaultListModel<>();
    private final JList<String> circuitList = new JList<>(circuitListModel);
    private final Map<String, CanvasModel> canvasesByCircuit = new LinkedHashMap<>();

    private ModelContracts.Circuit activeCircuit;
    private CanvasModel activeCanvas;
    private final CircuitCanvas canvas;
    private final SimulationPanel simulationPanel = new SimulationPanel();
    private final JLabel statusBar = new JLabel("Ready");
    private final PalettePanel palettePanel;

    private Supplier<ModelContracts.Component> pendingPlacementFactory;
    private boolean wireMode;
    private String wireColor = "black";

    private Map<ComponentId, Signal> collapseOutputs(Map<ComponentId, Map<Integer, Signal>> outputs) {
        Map<ComponentId, Signal> collapsed = new LinkedHashMap<>();
        if (outputs == null) return collapsed;
        for (var entry : outputs.entrySet()) {
            Signal signal = entry.getValue().getOrDefault(0, Signal.UNDEFINED);
            collapsed.put(entry.getKey(), signal);
        }
        for (ComponentId id : activeCanvas.getOutputOrder()) {
            var comp = activeCircuit.getComponent(id);
            Signal value = collapsed.get(id);
            if (value == null && comp != null) {
                if (comp instanceof OutputPin op) value = op.getObservedValue();
                else if (!comp.getOutputs().isEmpty()) value = comp.getOutputValue(0);
                else value = Signal.UNDEFINED;
            }
            collapsed.put(id, value == null ? Signal.UNDEFINED : value);
        }
        return collapsed;
    }

    public LogiSimFrame() {
        super("LogiSim — Circuit Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        this.project = new ProjectImpl("My Project");
        this.engine = new SimulationEngineImpl();
        engine.addListener(this);

        ModelContracts.Circuit defaultCircuit = new InMemoryCircuit("Main");
        defaultCircuit.addModelChangeListener(this);
        project.addCircuit(defaultCircuit);
        circuitListModel.addElement(defaultCircuit.getName());
        canvasesByCircuit.put(defaultCircuit.getName(), new CanvasModel());
        setActiveCircuit(defaultCircuit);

        this.canvas = new CircuitCanvas(activeCanvas, this);

        palettePanel = new PalettePanel(this);

        buildLayout();
    }

    private void buildLayout() {
        JPanel leftPanel = new JPanel(new BorderLayout(4, 4));
        leftPanel.setPreferredSize(new Dimension(260, 0));
        leftPanel.add(palettePanel, BorderLayout.NORTH);
        JPanel projectPanel = new JPanel(new BorderLayout());
        projectPanel.setBorder(new TitledBorder("Circuits"));
        circuitList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) switchCircuit(circuitList.getSelectedValue());
        });
        projectPanel.add(new JScrollPane(circuitList), BorderLayout.CENTER);
        JButton addCircuitBtn = new JButton("New Circuit");
        addCircuitBtn.addActionListener(e -> promptNewCircuit());
        projectPanel.add(addCircuitBtn, BorderLayout.SOUTH);
        leftPanel.add(projectPanel, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setPreferredSize(new Dimension(300, 0));
        simulationPanel.setAlignmentX(0f);
        rightPanel.add(simulationPanel);
        rightPanel.add(Box.createVerticalStrut(8));
        JPanel actions = buildActionPanel();
        actions.setAlignmentX(0f);
        rightPanel.add(actions);

        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.getViewport().setBackground(Color.WHITE);
        canvasScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel root = new JPanel(new BorderLayout());
        root.add(leftPanel, BorderLayout.WEST);
        root.add(canvasScroll, BorderLayout.CENTER);
        root.add(rightPanel, BorderLayout.EAST);

        add(root, BorderLayout.CENTER);
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusBar, BorderLayout.SOUTH);
    }

    private JPanel buildActionPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        panel.add(actionButton("Run Simulation", e -> runSimulation()));
        panel.add(Box.createVerticalStrut(6));
        panel.add(actionButton("Generate Truth Table", e -> showTruthTable()));
        panel.add(Box.createVerticalStrut(6));
        panel.add(actionButton("Generate Boolean Expression", e -> showExpressions()));
        panel.add(Box.createVerticalStrut(6));
        panel.add(actionButton("Delete Selected Component", e -> deleteSelectedComponent()));
        panel.add(Box.createVerticalStrut(6));
        panel.add(actionButton("Export Canvas (PNG)", e -> exportCanvasAsPng()));
        panel.add(Box.createVerticalStrut(6));
        panel.add(actionButton("Save Project", e -> saveProject()));
        panel.add(Box.createVerticalStrut(6));
        panel.add(actionButton("Load Project", e -> loadProject()));

        return panel;
    }

    private JButton actionButton(String text, ActionListener listener) {
        JButton button = new JButton(text);
        button.setAlignmentX(0f);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        button.addActionListener(listener);
        return button;
    }

    private void setActiveCircuit(ModelContracts.Circuit circuit) {
        this.activeCircuit = circuit;
        this.activeCanvas = canvasesByCircuit.computeIfAbsent(circuit.getName(), name -> new CanvasModel());
        activeCanvas.refreshPortOrdering(circuit.getInputComponentIds(), circuit.getOutputComponentIds());
        simulationPanel.setOutputOrder(activeCanvas.getOutputOrder(), this::componentLabel);
        if (canvas != null) {
            canvas.setModel(activeCanvas);
        }
    }

    private void switchCircuit(String name) {
        if (name == null) return;
        ModelContracts.Circuit circuit = project.getCircuit(name);
        if (circuit == null) return;
        setActiveCircuit(circuit);
        statusBar.setText("Switched to circuit " + name);
    }

    private void promptNewCircuit() {
        String name = JOptionPane.showInputDialog(this, "Circuit name:", "New Circuit", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;
        if (project.getCircuit(name) != null) {
            JOptionPane.showMessageDialog(this, "Circuit already exists", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        ModelContracts.Circuit circuit = new InMemoryCircuit(name);
        circuit.addModelChangeListener(this);
        project.addCircuit(circuit);
        circuitListModel.addElement(name);
        canvasesByCircuit.put(name, new CanvasModel());
        circuitList.setSelectedValue(name, true);
    }

    private String componentLabel(ComponentId id) {
        var figure = activeCanvas.getFigure(id);
        if (figure != null && figure.label() != null) return figure.label();
        var comp = activeCircuit.getComponent(id);
        if (comp == null) return id.id().substring(0, Math.min(6, id.id().length()));
        return comp.getType();
    }

    // PalettePanel.Listener
    @Override
    public void onWireTool(boolean enabled) {
        wireMode = enabled;
        if (enabled) {
            pendingPlacementFactory = null;
            statusBar.setText("Wire tool active. Click source output then destination input.");
        } else {
            canvas.cancelWirePreview();
            statusBar.setText("Wire tool off");
        }
    }

    @Override
    public void onComponentRequested(String label, Supplier<ModelContracts.Component> factory) {
        pendingPlacementFactory = factory;
        wireMode = false;
        palettePanel.clearWireSelection();
        statusBar.setText("Placing " + label + ", click on canvas to drop it");
    }

    @Override
    public void onModuleRequested() {
        List<ModelContracts.Circuit> circuits = project.listCircuits();
        if (circuits.size() <= 1) {
            JOptionPane.showMessageDialog(this, "Create another circuit first.", "Modules", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<String> names = circuits.stream().map(ModelContracts.Circuit::getName).toList();
        String choice = (String) JOptionPane.showInputDialog(this, "Select circuit to embed:", "Module",
                JOptionPane.PLAIN_MESSAGE, null, names.toArray(), null);
        if (choice == null) return;
        ModelContracts.Circuit target = project.getCircuit(choice);
        if (target == null) return;
        List<ComponentId> inOrder = new ArrayList<>(target.getInputComponentIds());
        List<ComponentId> outOrder = new ArrayList<>(target.getOutputComponentIds());
        pendingPlacementFactory = () -> new CircuitComponent("Module-" + choice, target, inOrder, outOrder);
        wireMode = false;
        palettePanel.clearWireSelection();
        statusBar.setText("Placing module " + choice + ". Click on canvas.");
    }

    @Override
    public void onWireColorChanged(String color) {
        wireColor = color;
    }

    // CircuitCanvas.Controller
    @Override
    public void requestComponentPlacement(Point canvasPoint) {
        if (pendingPlacementFactory == null) return;
        ModelContracts.Component component = pendingPlacementFactory.get();
        activeCircuit.addComponent(component);
        activeCanvas.upsertComponent(component, canvasPoint);
        activeCanvas.refreshPortOrdering(activeCircuit.getInputComponentIds(), activeCircuit.getOutputComponentIds());
        refreshSimulationPanel();
        pendingPlacementFactory = null;
        statusBar.setText("Component added at " + canvasPoint.x + "," + canvasPoint.y);
    }

    private void deleteSelectedComponent() {
        if (activeCircuit == null) return;
        String selWire = canvas.getSelectedConnectorId();
        if (selWire != null) {
            activeCircuit.removeConnector(selWire);
            canvas.setSelectedConnectorId(null);
            statusBar.setText("Deleted selected wire");
            return;
        }
        ComponentId selectedId = activeCanvas.getSelected();
        if (selectedId == null) {
            JOptionPane.showMessageDialog(this, "Select a component or wire on the canvas first.", "Delete", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        var comp = activeCircuit.getComponent(selectedId);
        if (comp == null) return;
        List<ModelContracts.Connector> toRemove = activeCircuit.getConnectors().stream()
                .filter(conn -> conn.getSourceComponentId().equals(selectedId) || conn.getSinkComponentId().equals(selectedId))
                .collect(Collectors.toList());
        for (var conn : toRemove) activeCircuit.removeConnector(conn.getId());
        activeCircuit.removeComponent(selectedId);
        statusBar.setText("Deleted " + comp.getType());
    }

    @Override
    public void requestConnectorCreation(PortRef source, PortRef sink, String color) {
        if (source == null || sink == null) return;
        BasicConnector connector = new BasicConnector(UUID.randomUUID().toString(),
                source.componentId(), source.portIndex(),
                sink.componentId(), sink.portIndex(),
                color);
        activeCircuit.addConnector(connector);
        statusBar.setText("Connector added");
    }

    @Override
    public void onComponentMoved(ComponentId id, Point newLocation) {
        activeCanvas.moveComponent(id, newLocation);
    }

    @Override
    public ToolMode currentTool() {
        if (pendingPlacementFactory != null) return ToolMode.PLACE_COMPONENT;
        if (wireMode) return ToolMode.WIRE;
        return ToolMode.IDLE;
    }

    @Override
    public String currentWireColor() {
        return wireColor;
    }

    @Override
    public boolean handlePortClick(PortRef port) {
        if (activeCircuit == null) return false;
        var comp = activeCircuit.getComponent(port.componentId());
        if (comp == null) return false;

        if (comp instanceof InputPin ip && port.output()) {
            Signal current = ip.getOutputValue(port.portIndex());
            Signal next = current == Signal.HIGH ? Signal.LOW : Signal.HIGH;
            ip.setState(next);
            activeCanvas.updateOutputs(comp, Map.of(port.portIndex(), next));
            statusBar.setText("Input switch toggled to " + next);
            return true;
        }

        if (!port.output()) {
            boolean wired = activeCircuit.getConnectors().stream()
                    .anyMatch(conn -> conn.getSinkComponentId().equals(port.componentId())
                            && conn.getSinkPortIndex() == port.portIndex());
            if (wired) return false;
            Signal current = activeCanvas.getInputState(port.componentId(), port.portIndex());
            Signal next = current == Signal.HIGH ? Signal.LOW : Signal.HIGH;
            comp.setInputValue(port.portIndex(), next);
            activeCanvas.setInputState(port.componentId(), port.portIndex(), next);
            statusBar.setText("Input set to " + next);
            return true;
        }
        return false;
    }

    private void refreshSimulationPanel() {
        simulationPanel.setOutputOrder(activeCanvas.getOutputOrder(), this::componentLabel);
    }

    private void runSimulation() {
        if (activeCircuit == null) return;
        new SimulationWorker().execute();
    }

    private class SimulationWorker extends SwingWorker<Map<ComponentId, Map<Integer, Signal>>, Void> {
        @Override
        protected Map<ComponentId, Map<Integer, Signal>> doInBackground() {
            engine.simulateOnce(activeCircuit);
            return engine.collectOutputs(activeCircuit);
        }

        @Override
        protected void done() {
            try {
                Map<ComponentId, Map<Integer, Signal>> outputs = get();
                simulationPanel.showOutputs(collapseOutputs(outputs), LogiSimFrame.this::componentLabel);
                statusBar.setText("Simulation complete");
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Simulation failed", ex);
                JOptionPane.showMessageDialog(LogiSimFrame.this, "Simulation error: " + ex.getMessage(),
                        "Simulation", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showTruthTable() {
        if (activeCircuit == null) return;
        if (!hasDeclaredInputs()) {
            JOptionPane.showMessageDialog(this,
                    "Truth tables require Input Switch components representing each external input.\n" +
                            "Add input switches and wire them into your circuit, then try again.",
                    "Add Inputs", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Map<ComponentId, Signal> snapshot = snapshotInputPins();
        List<TruthTableGenerator.TruthRow> rows;
        try {
            rows = TruthTableGenerator.generate(
                    activeCircuit,
                    engine,
                    activeCanvas.getInputOrder(),
                    activeCanvas.getOutputOrder());
        } finally {
            restoreInputPins(snapshot);
            recomputeOutputsSilently();
        }
        TruthTableDialog dialog = new TruthTableDialog(this, rows, activeCanvas.getInputOrder(),
                activeCanvas.getOutputOrder(), this::componentLabel);
        dialog.setVisible(true);
    }

    private void showExpressions() {
        if (activeCircuit == null) return;
        if (!hasDeclaredInputs()) {
            JOptionPane.showMessageDialog(this,
                    "Boolean expressions require Input Switch components. Add them to your circuit first.",
                    "Add Inputs", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Map<ComponentId, Signal> snapshot = snapshotInputPins();
        Map<ComponentId, String> expressions;
        try {
            expressions = BooleanExpressionGenerator.generate(activeCircuit, engine);
        } finally {
            restoreInputPins(snapshot);
            recomputeOutputsSilently();
        }
        BooleanExpressionDialog dialog = new BooleanExpressionDialog(this, expressions, this::componentLabel);
        dialog.setVisible(true);
    }

    private boolean hasDeclaredInputs() {
        return activeCircuit != null && !activeCircuit.getInputComponentIds().isEmpty();
    }

    private Map<ComponentId, Signal> snapshotInputPins() {
        Map<ComponentId, Signal> snapshot = new LinkedHashMap<>();
        if (activeCircuit == null) return snapshot;
        for (ComponentId id : activeCircuit.getInputComponentIds()) {
            var comp = activeCircuit.getComponent(id);
            if (comp instanceof InputPin ip) {
                snapshot.put(id, ip.getOutputValue(0));
            }
        }
        return snapshot;
    }

    private void restoreInputPins(Map<ComponentId, Signal> snapshot) {
        if (activeCircuit == null || snapshot == null) return;
        for (var entry : snapshot.entrySet()) {
            var comp = activeCircuit.getComponent(entry.getKey());
            if (comp instanceof InputPin ip) {
                ip.setState(entry.getValue());
            }
        }
    }

    private void recomputeOutputsSilently() {
        if (activeCircuit == null) return;
        try {
            engine.simulateOnce(activeCircuit);
            Map<ComponentId, Map<Integer, Signal>> outputs = engine.collectOutputs(activeCircuit);
            simulationPanel.showOutputs(collapseOutputs(outputs), this::componentLabel);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Unable to recompute outputs after analysis", ex);
        }
    }

    private void exportCanvasAsPng() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = ensurePngPath(chooser.getSelectedFile().toPath());
        writeCanvasSnapshot(path, true);
    }

    private void saveProject() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();
        try {
            persistenceService.save(project, canvasesByCircuit, path);
            statusBar.setText("Project saved to " + path);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Save failed", ex);
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(), "Save", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadProject() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();
        String lowerName = path.getFileName().toString().toLowerCase();
        if (lowerName.endsWith(".png")) {
            showPngDialog(path);
            return;
        }
        try {
            var result = persistenceService.load(path);
            project.setName(result.project().getName());
            List<ModelContracts.Circuit> existing = new ArrayList<>(project.listCircuits());
            for (ModelContracts.Circuit c : existing) {
                c.removeModelChangeListener(this);
                project.removeCircuit(c.getName());
            }
            circuitListModel.clear();
            canvasesByCircuit.clear();
            for (ModelContracts.Circuit circuit : result.project().listCircuits()) {
                circuit.addModelChangeListener(this);
                circuitListModel.addElement(circuit.getName());
                canvasesByCircuit.put(circuit.getName(), result.layouts().getOrDefault(circuit.getName(), new CanvasModel()));
                project.addCircuit(circuit);
            }
            if (circuitListModel.getSize() > 0) {
                circuitList.setSelectedIndex(0);
                switchCircuit(circuitListModel.getElementAt(0));
            }
            statusBar.setText("Loaded project from " + path);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Load failed", ex);
            JOptionPane.showMessageDialog(this, "Load failed: " + ex.getMessage(), "Load", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Path ensurePngPath(Path chosen) {
        String fileName = chosen.getFileName().toString();
        if (fileName.toLowerCase().endsWith(".png")) return chosen;
        return chosen.resolveSibling(fileName + ".png");
    }

    private void writeCanvasSnapshot(Path path, boolean updateStatus) {
        try {
            BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            canvas.paint(g2);
            g2.dispose();
            ImageIO.write(image, "png", path.toFile());
            if (updateStatus) statusBar.setText("Exported canvas to " + path);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Unable to export canvas", ex);
            if (updateStatus) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Export", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showPngDialog(Path pngPath) {
        try {
            BufferedImage image = ImageIO.read(pngPath.toFile());
            if (image == null) {
                JOptionPane.showMessageDialog(this, "Unable to read image", "PNG Viewer", JOptionPane.ERROR_MESSAGE);
                return;
            }
            JLabel imageLabel = new JLabel(new ImageIcon(image));
            JScrollPane scroller = new JScrollPane(imageLabel);
            JDialog dialog = new JDialog(this, "PNG: " + pngPath.getFileName(), false);
            dialog.add(scroller);
            dialog.setSize(Math.min(image.getWidth() + 40, 800), Math.min(image.getHeight() + 80, 600));
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
            statusBar.setText("Displayed PNG " + pngPath);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Unable to open PNG", ex);
            JOptionPane.showMessageDialog(this, "Unable to open PNG: " + ex.getMessage(), "PNG Viewer", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ModelChangeListener callbacks
    @Override
    public void onComponentAdded(ModelContracts.Circuit circuit, ModelContracts.Component component) {
        SwingUtilities.invokeLater(() -> {
            CanvasModel canvasModel = canvasesByCircuit.computeIfAbsent(circuit.getName(), name -> new CanvasModel());
            if (canvasModel.getFigure(component.getId()) == null) {
                canvasModel.upsertComponent(component, new Point(40, 40));
            }
            canvasModel.refreshPortOrdering(circuit.getInputComponentIds(), circuit.getOutputComponentIds());
            if (circuit == activeCircuit) refreshSimulationPanel();
        });
    }

    @Override
    public void onComponentRemoved(ModelContracts.Circuit circuit, ComponentId id) {
        SwingUtilities.invokeLater(() -> {
            CanvasModel canvasModel = canvasesByCircuit.get(circuit.getName());
            if (canvasModel != null) canvasModel.removeComponent(id);
            if (circuit == activeCircuit) refreshSimulationPanel();
        });
    }

    @Override
    public void onConnectorAdded(ModelContracts.Circuit circuit, ModelContracts.Connector connector) {
        SwingUtilities.invokeLater(() -> {
            CanvasModel canvasModel = canvasesByCircuit.get(circuit.getName());
            if (canvasModel != null) {
                canvasModel.setConnector(connector);
                canvasModel.clearInputState(connector.getSinkComponentId(), connector.getSinkPortIndex());
            }
            if (circuit == activeCircuit) runSimulation();
        });
    }

    @Override
    public void onConnectorRemoved(ModelContracts.Circuit circuit, String connectorId) {
        SwingUtilities.invokeLater(() -> {
            CanvasModel canvasModel = canvasesByCircuit.get(circuit.getName());
            if (canvasModel != null) canvasModel.removeConnector(connectorId);
            if (circuit == activeCircuit) runSimulation();
        });
    }

    @Override
    public void onComponentStateChanged(ModelContracts.Circuit circuit, ModelContracts.Component component, Map<Integer, Signal> outputs) {
        SwingUtilities.invokeLater(() -> {
            if (circuit == activeCircuit) {
                activeCanvas.updateOutputs(component, outputs);
            }
        });
    }

    // SimulationListener
    @Override
    public void onSimulationStep(ModelContracts.Circuit circuit) {
        SwingUtilities.invokeLater(() -> statusBar.setText("Simulating " + circuit.getName() + "..."));
    }

    @Override
    public void onSimulationComplete(ModelContracts.Circuit circuit) {
        SwingUtilities.invokeLater(() -> statusBar.setText("Simulation complete for " + circuit.getName()));
    }

    @Override
    public void onSimulationError(ModelContracts.Circuit circuit, ModelContracts.SimulationException error) {
        SwingUtilities.invokeLater(() -> statusBar.setText("Simulation error: " + error.getMessage()));
    }
}

