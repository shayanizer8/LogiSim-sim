package org.logisim.business;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.logisim.business.ModelContracts.ComponentId;
import static org.logisim.business.ModelContracts.Signal;

/**
 * Simple implementation of the SimulationEngine contract. Delegates simulation
 * execution to the provided Circuit implementation (currently {@code InMemoryCircuit}).
 */
public class SimulationEngineImpl implements ModelContracts.SimulationEngine {
    private final List<ModelContracts.SimulationListener> listeners = new ArrayList<>();

    @Override
    public void addListener(ModelContracts.SimulationListener listener) {
        if (listener == null) return;
        listeners.add(listener);
    }

    @Override
    public void removeListener(ModelContracts.SimulationListener listener) {
        listeners.remove(listener);
    }

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(SimulationEngineImpl.class.getName());

    @Override
    public void setInputValues(ModelContracts.Circuit circuit, Map<ComponentId, Map<Integer, Signal>> values) {
        if (circuit == null || values == null) return;
        for (var entry : values.entrySet()) {
            var comp = circuit.getComponent(entry.getKey());
            if (comp == null) continue;
            Map<Integer, Signal> map = entry.getValue();
            if (map == null) continue;
            for (var e2 : map.entrySet()) {
                comp.setInputValue(e2.getKey(), e2.getValue());
            }
        }
    }

    @Override
    public void simulateOnce(ModelContracts.Circuit circuit) throws ModelContracts.SimulationException {
        try {
            // notify step start
            for (var l : listeners) l.onSimulationStep(circuit);
            circuit.simulate();
            for (var l : listeners) l.onSimulationComplete(circuit);
        } catch (RuntimeException ex) {
            ModelContracts.SimulationException se = new ModelContracts.SimulationException("Simulation failed", ex);
            for (var l : listeners) l.onSimulationError(circuit, se);
            LOGGER.log(java.util.logging.Level.SEVERE, "Simulation failure", ex);
            throw se;
        }
    }

    @Override
    public Map<ComponentId, Map<Integer, Signal>> collectOutputs(ModelContracts.Circuit circuit) {
        Map<ComponentId, Map<Integer, Signal>> result = new HashMap<>();
        for (var comp : circuit.getComponents()) {
            List<ModelContracts.Port> outs = comp.getOutputs();
            if (outs.isEmpty()) continue;
            Map<Integer, Signal> map = new HashMap<>();
            for (var p : outs) {
                map.put(p.index(), comp.getOutputValue(p.index()));
            }
            result.put(comp.getId(), map);
        }
        return result;
    }
}
