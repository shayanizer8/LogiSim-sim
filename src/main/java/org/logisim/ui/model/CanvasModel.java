package org.logisim.ui.model;

import org.logisim.business.ModelContracts;
import org.logisim.business.ModelContracts.ComponentId;
import org.logisim.business.ModelContracts.Signal;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Presentation model for the circuit canvas. Keeps track of component figures,
 * connector figures and ordering metadata used by the UI (truth tables, boolean
 * expressions, deterministic rendering).
 */
public class CanvasModel {
    public static final Dimension DEFAULT_COMPONENT_SIZE = new Dimension(140, 80);

    private final Map<ComponentId, ComponentFigure> figures = new LinkedHashMap<>();
    private final Map<String, ConnectorFigure> connectors = new LinkedHashMap<>();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final List<ComponentId> inputOrder = new ArrayList<>();
    private final List<ComponentId> outputOrder = new ArrayList<>();

    private ComponentId selectedId;

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    private void fireChange() {
        pcs.firePropertyChange("canvas", null, null);
    }

    public void upsertComponent(ModelContracts.Component component, Point location) {
        ComponentFigure current = figures.get(component.getId());
        Rectangle bounds;
        if (current == null) {
            bounds = new Rectangle(location, sizeFor(component));
        } else {
            bounds = new Rectangle(current.bounds());
            bounds.setLocation(location);
        }
        Map<Integer, Signal> inputs = current == null ? Map.of() : current.inputs();
        Map<Integer, Signal> outputs = current == null ? Map.of() : current.outputs();
        figures.put(component.getId(), new ComponentFigure(component, bounds,
                current == null ? component.getType() : current.label(),
                inputs,
                outputs));
        fireChange();
    }

    public void removeComponent(ComponentId id) {
        figures.remove(id);
        connectors.values().removeIf(conn -> conn.connector().getSourceComponentId().equals(id) || conn.connector().getSinkComponentId().equals(id));
        if (Objects.equals(selectedId, id)) selectedId = null;
        inputOrder.remove(id);
        outputOrder.remove(id);
        fireChange();
    }

    public List<ComponentFigure> getFigures() {
        return List.copyOf(figures.values());
    }

    public ComponentFigure getFigure(ComponentId id) {
        return figures.get(id);
    }

    public void setConnector(ModelContracts.Connector connector) {
        connectors.put(connector.getId(), new ConnectorFigure(connector));
        fireChange();
    }

    public void removeConnector(String connectorId) {
        connectors.remove(connectorId);
        fireChange();
    }

    public List<ConnectorFigure> getConnectorFigures() {
        return List.copyOf(connectors.values());
    }

    public void setSelected(ComponentId id) {
        var old = this.selectedId;
        this.selectedId = id;
        pcs.firePropertyChange("selection", old, id);
    }

    public ComponentId getSelected() {
        return selectedId;
    }

    public ComponentFigure findFigureAt(Point p) {
        for (ComponentFigure fig : figures.values()) {
            if (fig.bounds().contains(p)) return fig;
        }
        return null;
    }

    public void moveComponent(ComponentId id, Point location) {
        ComponentFigure fig = figures.get(id);
        if (fig == null) return;
        Rectangle b = new Rectangle(location, fig.bounds().getSize());
        figures.put(id, new ComponentFigure(fig.component(), b, fig.label(), fig.inputs(), fig.outputs()));
        fireChange();
    }

    public void renameComponent(ComponentId id, String label) {
        ComponentFigure fig = figures.get(id);
        if (fig == null) return;
        figures.put(id, new ComponentFigure(fig.component(), fig.bounds(), label, fig.inputs(), fig.outputs()));
        fireChange();
    }

    public void updateOutputs(ModelContracts.Component component, Map<Integer, Signal> outputs) {
        ComponentFigure fig = figures.get(component.getId());
        if (fig == null) return;
        figures.put(component.getId(), new ComponentFigure(component, fig.bounds(), fig.label(), fig.inputs(), outputs == null ? Map.of() : Map.copyOf(outputs)));
        fireChange();
    }

    public void setInputState(ComponentId id, int portIndex, Signal signal) {
        ComponentFigure fig = figures.get(id);
        if (fig == null) return;
        Map<Integer, Signal> inputs = new java.util.HashMap<>(fig.inputs());
        if (signal == null) inputs.remove(portIndex);
        else inputs.put(portIndex, signal);
        figures.put(id, new ComponentFigure(fig.component(), fig.bounds(), fig.label(), Map.copyOf(inputs), fig.outputs()));
        fireChange();
    }

    public Signal getInputState(ComponentId id, int portIndex) {
        ComponentFigure fig = figures.get(id);
        if (fig == null) return Signal.UNDEFINED;
        return fig.inputs().getOrDefault(portIndex, Signal.UNDEFINED);
    }

    public void clearInputState(ComponentId id, int portIndex) {
        setInputState(id, portIndex, null);
    }

    public List<ComponentId> getInputOrder() { return List.copyOf(inputOrder); }
    public List<ComponentId> getOutputOrder() { return List.copyOf(outputOrder); }

    public void refreshPortOrdering(Set<ComponentId> newInputs, Set<ComponentId> newOutputs) {
        mergeOrder(inputOrder, newInputs);
        inputOrder.removeIf(id -> !newInputs.contains(id));
        mergeOrder(outputOrder, newOutputs);
        outputOrder.removeIf(id -> !newOutputs.contains(id));
    }

    public void setInputOrderDirect(List<ComponentId> order) {
        inputOrder.clear();
        inputOrder.addAll(order);
    }

    public void setOutputOrderDirect(List<ComponentId> order) {
        outputOrder.clear();
        outputOrder.addAll(order);
    }

    private void mergeOrder(List<ComponentId> order, Set<ComponentId> additions) {
        Set<ComponentId> seen = new LinkedHashSet<>(order);
        for (ComponentId id : additions) {
            if (!seen.contains(id)) {
                order.add(id);
                seen.add(id);
            }
        }
    }

    public record ComponentFigure(ModelContracts.Component component,
                                  Rectangle bounds,
                                  String label,
                                  Map<Integer, Signal> inputs,
                                  Map<Integer, Signal> outputs) { }
    public record ConnectorFigure(ModelContracts.Connector connector) { }

    private Dimension sizeFor(ModelContracts.Component component) {
        if (component == null) return DEFAULT_COMPONENT_SIZE;
        String type = component.getType() == null ? "" : component.getType().toUpperCase();
        return switch (type) {
            case "INPUT" -> new Dimension(36, 32);
            case "OUTPUT" -> new Dimension(42, 42);
            case "NOT" -> new Dimension(80, 44);
            case "AND", "OR", "NAND", "NOR", "XOR" -> new Dimension(100, 64);
            default -> DEFAULT_COMPONENT_SIZE;
        };
    }
}

