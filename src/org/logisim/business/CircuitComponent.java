package org.logisim.business;

import java.util.ArrayList;
import java.util.List;

import static org.logisim.business.ModelContracts.ComponentId;
import static org.logisim.business.ModelContracts.Signal;

/**
 * Composite component that wraps a `Circuit` so it can be used as a Component
 * inside other circuits. The constructor accepts ordered lists of input and
 * output component ids from the inner circuit to map ports deterministically.
 */
public class CircuitComponent extends AbstractComponent {
    private final ModelContracts.Circuit inner;
    private final List<ComponentId> inputIds;
    private final List<ComponentId> outputIds;

    public CircuitComponent(String name, ModelContracts.Circuit inner, List<ComponentId> inputIds, List<ComponentId> outputIds) {
        super("CIRCUIT", inputIds == null ? 0 : inputIds.size(), outputIds == null ? 0 : outputIds.size());
        this.inner = inner;
        this.inputIds = inputIds == null ? new ArrayList<>() : List.copyOf(inputIds);
        this.outputIds = outputIds == null ? new ArrayList<>() : List.copyOf(outputIds);
    }

    public ModelContracts.Circuit getInnerCircuit() { return inner; }

    @Override
    public void evaluate() {
        // Map outer inputs into inner circuit's input components
        for (int i = 0; i < inputIds.size(); i++) {
            ComponentId cid = inputIds.get(i);
            var comp = inner.getComponent(cid);
            Signal v = getInputValue(i);
            if (comp == null) continue;
            if (comp instanceof InputPin ip) {
                ip.setState(v);
            } else {
                // try set input port 0
                if (!comp.getInputs().isEmpty()) comp.setInputValue(0, v);
            }
        }

        // Run inner simulation
        inner.simulate();

        // Read outputs from inner circuit into this component's outputs
        for (int j = 0; j < outputIds.size(); j++) {
            ComponentId cid = outputIds.get(j);
            var comp = inner.getComponent(cid);
            Signal out = Signal.UNDEFINED;
            if (comp == null) {
                out = Signal.UNDEFINED;
            } else if (comp instanceof OutputPin op) {
                out = op.getObservedValue();
            } else if (!comp.getOutputs().isEmpty()) {
                out = comp.getOutputValue(0);
            }
            setOutputValue(j, out);
        }
    }
}
