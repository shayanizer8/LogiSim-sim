package org.logisim.business;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.logisim.business.ModelContracts.ComponentId;
import static org.logisim.business.ModelContracts.Signal;

/**
 * Simple boolean expression generator that produces a (non-minimized)
 * sum-of-products expression for each output component based on the
 * truth table. Variable names are assigned as A, B, C... in the order of
 * the circuit's input component ids list.
 */
public final class BooleanExpressionGenerator {
    private BooleanExpressionGenerator() { }

    /**
     * Generate expressions for each output component. Returns a map from
     * output ComponentId -> expression string.
     */
    public static Map<ComponentId, String> generate(ModelContracts.Circuit circuit, ModelContracts.SimulationEngine engine) {
        Map<ComponentId, String> map = new HashMap<>();
        List<ComponentId> inputs = new ArrayList<>(circuit.getInputComponentIds());
        List<ComponentId> outputs = new ArrayList<>(circuit.getOutputComponentIds());

        if (inputs.isEmpty() || outputs.isEmpty()) return map;

        // get truth table
        List<TruthTableGenerator.TruthRow> table = TruthTableGenerator.generate(circuit, engine);

        // variable names
        List<String> varNames = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) varNames.add(String.valueOf((char)('A' + i)));

        for (ComponentId outId : outputs) {
            List<String> minterms = new ArrayList<>();
            for (var row : table) {
                Signal v = row.outputs().get(outId);
                if (v == Signal.HIGH) {
                    // build product for this row
                    List<String> lits = new ArrayList<>();
                    for (int i = 0; i < inputs.size(); i++) {
                        ComponentId inId = inputs.get(i);
                        Signal inV = row.inputs().get(inId);
                        String var = varNames.get(i);
                        if (inV == Signal.HIGH) lits.add(var);
                        else lits.add("!" + var);
                    }
                    minterms.add(String.join(" & ", lits));
                }
            }
            String expr;
            if (minterms.isEmpty()) expr = "0";
            else expr = String.join(" | ", minterms);
            map.put(outId, expr);
        }
        return map;
    }
}
