package org.logisim.business;

import java.util.List;
import java.util.Map;

import static org.logisim.business.ModelContracts.Signal;

/**
 * Small factory to create component instances from a type string and optional
 * saved state map. Used by the persistence layer to reconstruct circuits.
 */
public final class ComponentFactory {
    private ComponentFactory() { }

    public static ModelContracts.Component create(String type, Map<String, Object> state) {
        if (type == null) return null;
        String t = type.toUpperCase();
        switch (t) {
            case "AND": {
                var g = new org.logisim.business.gates.AndGate();
                restoreInputs(g, state);
                return g;
            }
            case "OR": {
                var g = new org.logisim.business.gates.OrGate();
                restoreInputs(g, state);
                return g;
            }
            case "NOT": {
                var g = new org.logisim.business.gates.NotGate();
                restoreInputs(g, state);
                return g;
            }
            case "INPUT": {
                var ip = new InputPin();
                restoreOutputsForInput(ip, state);
                return ip;
            }
            case "OUTPUT": {
                var op = new OutputPin();
                // restore input value if present
                restoreInputs(op, state);
                return op;
            }
            default:
                // unknown type: return null for now
                return null;
        }
    }

    private static void restoreInputs(ModelContracts.Component comp, Map<String, Object> state) {
        if (state == null) return;
        Object inObj = state.get("inputs");
        if (!(inObj instanceof List)) return;
        List<?> inputs = (List<?>) inObj;
        for (int i = 0; i < inputs.size(); i++) {
            Object o = inputs.get(i);
            if (o == null) continue;
            Signal s = parseSignal(o.toString());
            comp.setInputValue(i, s);
        }
    }

    private static void restoreOutputsForInput(InputPin ip, Map<String, Object> state) {
        if (state == null) return;
        Object outObj = state.get("outputs");
        if (!(outObj instanceof List)) return;
        List<?> outputs = (List<?>) outObj;
        if (!outputs.isEmpty()) {
            Signal s = parseSignal(outputs.get(0).toString());
            ip.setState(s);
        }
    }

    private static Signal parseSignal(String s) {
        if (s == null) return Signal.UNDEFINED;
        try {
            return Signal.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return Signal.UNDEFINED;
        }
    }
}
