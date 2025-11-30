package org.logisim.business;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.logisim.business.ModelContracts.ComponentId;
import static org.logisim.business.ModelContracts.Signal;

/**
 * Utility that generates a truth table for a circuit by enumerating all input
 * combinations, running the provided SimulationEngine, and collecting outputs.
 *
 * Note: This generator expects the circuit's input components to be InputPin
 * (or otherwise controllable by setting their state). Output components should
 * be OutputPin (or readable after simulation).
 */
public final class TruthTableGenerator {
    private TruthTableGenerator() { }

    public static record TruthRow(Map<ComponentId, Signal> inputs, Map<ComponentId, Signal> outputs) { }

    public static List<TruthRow> generate(ModelContracts.Circuit circuit,
                                          ModelContracts.SimulationEngine engine) {
        return generate(circuit, engine, null, null);
    }

    /**
     * Generate the truth table for the provided circuit using the given engine.
     * If {@code inputOrder} or {@code outputOrder} are provided, they control the
     * ordering of inputs/outputs; otherwise the circuit's component sets are used.
     */
    public static List<TruthRow> generate(ModelContracts.Circuit circuit,
                                          ModelContracts.SimulationEngine engine,
                                          List<ComponentId> inputOrder,
                                          List<ComponentId> outputOrder) {
        if (circuit == null || engine == null) return List.of();

        List<ComponentId> inputs = (inputOrder != null && !inputOrder.isEmpty())
                ? new ArrayList<>(inputOrder)
                : new ArrayList<>(circuit.getInputComponentIds());
        List<ComponentId> outputs = (outputOrder != null && !outputOrder.isEmpty())
                ? new ArrayList<>(outputOrder)
                : new ArrayList<>(circuit.getOutputComponentIds());

        int n = inputs.size();
        int rows = 1 << Math.max(0, n);
        List<TruthRow> table = new ArrayList<>();

        for (int mask = 0; mask < rows; mask++) {
            Map<ComponentId, Signal> inMap = new HashMap<>();

            // set inputs
            for (int i = 0; i < n; i++) {
                ComponentId cid = inputs.get(i);
                int repeats = 1 << (n - i - 1);
                int position = (mask / repeats) % 2;
                Signal s = position == 1 ? Signal.HIGH : Signal.LOW;
                var comp = circuit.getComponent(cid);
                if (comp instanceof InputPin ip) {
                    ip.setState(s);
                } else {
                    // fallback: try setting first input port if present
                    if (!comp.getInputs().isEmpty()) comp.setInputValue(0, s);
                }
                inMap.put(cid, s);
            }

            // run simulation
            engine.simulateOnce(circuit);

            // collect outputs
            Map<ComponentId, Signal> outMap = new HashMap<>();

            // First try to read OutputPin observed value
            for (ComponentId cid : outputs) {
                var comp = circuit.getComponent(cid);
                if (comp instanceof OutputPin op) {
                    outMap.put(cid, op.getObservedValue());
                } else {
                    // fallback: if component has outputs, read them via getOutputValue(0)
                    if (!comp.getOutputs().isEmpty()) outMap.put(cid, comp.getOutputValue(0));
                    else outMap.put(cid, Signal.UNDEFINED);
                }
            }

            table.add(new TruthRow(inMap, outMap));
        }

        return table;
    }
}
