package org.logisim.business;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CopyOnWriteArrayList;

import org.logisim.business.ModelContracts.ModelChangeListener;

import static org.logisim.business.ModelContracts.ComponentId;
import static org.logisim.business.ModelContracts.Signal;

/**
 * Simple in-memory Circuit implementation. Simulation uses an iterative
 * evaluate-and-propagate loop until values stabilize or max iterations reached.
 */
public class InMemoryCircuit implements ModelContracts.Circuit {
    private final String name;
    private final Map<ComponentId, ModelContracts.Component> components = new HashMap<>();
    private final Map<String, ModelContracts.Connector> connectors = new HashMap<>();
    private final List<ModelChangeListener> listeners = new CopyOnWriteArrayList<>();
    private static final Logger LOGGER = Logger.getLogger(InMemoryCircuit.class.getName());

    public InMemoryCircuit(String name) { this.name = name; }

    @Override public String getName() { return name; }

    @Override
    public void addComponent(ModelContracts.Component component) {
        components.put(component.getId(), component);
        // Notify listeners
        for (var l : listeners) {
            try { l.onComponentAdded(this, component); } catch (Exception ex) { /* swallow listener errors */ }
        }
    }

    @Override
    public void removeComponent(ComponentId id) {
        components.remove(id);
        notifyComponentRemoved(id);
    }

    // notify removal through listener
    private void notifyComponentRemoved(ComponentId id) {
        for (var l : listeners) {
            try { l.onComponentRemoved(this, id); } catch (Exception ex) { }
        }
    }

    @Override
    public ModelContracts.Component getComponent(ComponentId id) { return components.get(id); }

    @Override
    public void addConnector(ModelContracts.Connector connector) {
        connectors.put(connector.getId(), connector);
        notifyConnectorAdded(connector);
    }

    @Override
    public void removeConnector(String connectorId) {
        connectors.remove(connectorId);
        notifyConnectorRemoved(connectorId);
    }

    private void notifyConnectorAdded(ModelContracts.Connector connector) {
        for (var l : listeners) {
            try { l.onConnectorAdded(this, connector); } catch (Exception ex) { }
        }
    }

    private void notifyConnectorRemoved(String connectorId) {
        for (var l : listeners) {
            try { l.onConnectorRemoved(this, connectorId); } catch (Exception ex) { }
        }
    }

    @Override
    public void addModelChangeListener(ModelChangeListener listener) {
        if (listener == null) return;
        listeners.add(listener);
    }

    @Override
    public void removeModelChangeListener(ModelChangeListener listener) {
        listeners.remove(listener);
    }

    @Override
    public List<ModelContracts.Component> getComponents() { return new ArrayList<>(components.values()); }

    @Override
    public List<ModelContracts.Connector> getConnectors() { return new ArrayList<>(connectors.values()); }

    @Override
    public Set<ComponentId> getInputComponentIds() {
        // Simple heuristic: components with zero inputs are considered external inputs
        Set<ComponentId> ids = new HashSet<>();
        for (var c : components.values()) {
            if (c.getInputs().isEmpty()) ids.add(c.getId());
        }
        return ids;
    }

    @Override
    public Set<ComponentId> getOutputComponentIds() {
        // Heuristic: components with zero outputs are external outputs
        Set<ComponentId> ids = new HashSet<>();
        for (var c : components.values()) {
            if (c.getOutputs().isEmpty()) ids.add(c.getId());
        }
        return ids;
    }

    @Override
    public void simulate() {
        // Before evaluating, clear all component inputs to UNDEFINED unless they are currently wired
        // First, build a map from (component, input port) to whether it is driven
        Map<ComponentId, Set<Integer>> drivenInputs = new HashMap<>();
        for (var conn : connectors.values()) {
            drivenInputs.computeIfAbsent(conn.getSinkComponentId(), k -> new HashSet<>()).add(conn.getSinkPortIndex());
        }
        for (var comp : components.values()) {
            int numInputs = comp.getInputs().size();
            for (int i = 0; i < numInputs; i++) {
                if (drivenInputs.getOrDefault(comp.getId(), Set.of()).contains(i)) continue;
                comp.setInputValue(i, Signal.UNDEFINED);
            }
        }

        // First, attempt a topological evaluation for acyclic graphs. If cycles are
        // present, fallback to an iterative stabilization loop.
        try {
            List<ComponentId> topo = topologicalSort();
            if (topo != null) {
                // Acyclic: evaluate in topological order and propagate outputs once
                Map<ComponentId, List<Signal>> prevOutputs = snapshotOutputs();
                for (ComponentId id : topo) {
                    var comp = components.get(id);
                    if (comp == null) continue;
                    comp.evaluate();
                    // propagate outputs of this component immediately
                    for (var conn : connectors.values()) {
                        if (conn.getSourceComponentId().equals(id)) {
                            var dst = components.get(conn.getSinkComponentId());
                            if (dst == null) continue;
                            dst.setInputValue(conn.getSinkPortIndex(), comp.getOutputValue(conn.getSourcePortIndex()));
                        }
                    }
                }
                notifyStateChanges(prevOutputs);
                return;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Topological simulation attempt failed, falling back to iterative", ex);
        }

        // Fallback: iterative evaluate-and-propagate until stable
        int maxIter = Math.max(10, components.size() + 5);
        boolean changed = true;
        for (int iter = 0; iter < maxIter && changed; iter++) {
            changed = false;

            Map<ComponentId, List<Signal>> prevOutputs = snapshotOutputs();

            // evaluate all components
            for (var comp : components.values()) comp.evaluate();

            // propagate outputs via connectors
            for (var conn : connectors.values()) {
                var src = components.get(conn.getSourceComponentId());
                var dst = components.get(conn.getSinkComponentId());
                if (src == null || dst == null) continue;
                Signal out = src.getOutputValue(conn.getSourcePortIndex());
                dst.setInputValue(conn.getSinkPortIndex(), out);
                if (out != Signal.UNDEFINED) changed = true;
            }

            notifyStateChanges(prevOutputs);
        }
    }

    private Map<ComponentId, List<Signal>> snapshotOutputs() {
        Map<ComponentId, List<Signal>> prevOutputs = new HashMap<>();
        for (var comp : components.values()) {
            List<Signal> outs = new ArrayList<>();
            for (var p : comp.getOutputs()) outs.add(comp.getOutputValue(p.index()));
            prevOutputs.put(comp.getId(), outs);
        }
        return prevOutputs;
    }

    private void notifyStateChanges(Map<ComponentId, List<Signal>> prevOutputs) {
        for (var comp : components.values()) {
            List<Signal> now = new ArrayList<>();
            for (var p : comp.getOutputs()) now.add(comp.getOutputValue(p.index()));
            List<Signal> before = prevOutputs.get(comp.getId());
            boolean differs = false;
            if (before == null) differs = true;
            else if (before.size() != now.size()) differs = true;
            else {
                for (int i = 0; i < now.size(); i++) {
                    if (now.get(i) != before.get(i)) { differs = true; break; }
                }
            }
            if (differs) {
                Map<Integer, Signal> outMap = new HashMap<>();
                for (var p : comp.getOutputs()) outMap.put(p.index(), comp.getOutputValue(p.index()));
                for (var l : listeners) {
                    try { l.onComponentStateChanged(this, comp, outMap); } catch (Exception ex) { LOGGER.log(Level.FINER, "Listener threw", ex); }
                }
            }
        }
    }

    /**
     * Attempt a topological sort of components based on connectors. Returns
     * a list of ComponentId in topological order, or null if the graph contains cycles.
     */
    private List<ComponentId> topologicalSort() {
        // Build adjacency and in-degree
        Map<ComponentId, Integer> indeg = new HashMap<>();
        Map<ComponentId, List<ComponentId>> adj = new HashMap<>();
        for (var id : components.keySet()) { indeg.put(id, 0); adj.put(id, new ArrayList<>()); }
        for (var conn : connectors.values()) {
            var src = conn.getSourceComponentId();
            var dst = conn.getSinkComponentId();
            if (!adj.containsKey(src) || !adj.containsKey(dst)) continue;
            adj.get(src).add(dst);
            indeg.put(dst, indeg.getOrDefault(dst, 0) + 1);
        }

        Queue<ComponentId> q = new LinkedList<>();
        for (var e : indeg.entrySet()) if (e.getValue() == 0) q.add(e.getKey());

        List<ComponentId> order = new ArrayList<>();
        while (!q.isEmpty()) {
            ComponentId cur = q.poll();
            order.add(cur);
            for (var nb : adj.getOrDefault(cur, List.of())) {
                indeg.put(nb, indeg.get(nb) - 1);
                if (indeg.get(nb) == 0) q.add(nb);
            }
        }

        if (order.size() != components.size()) return null; // cycle detected
        return order;
    }
}
