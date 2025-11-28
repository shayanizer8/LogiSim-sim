package org.logisim.business;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.logisim.business.ModelContracts.ComponentId;
import static org.logisim.business.ModelContracts.Port;
import static org.logisim.business.ModelContracts.Signal;

/**
 * Minimal abstract base for components. Stores ports and current signal values.
 */
public abstract class AbstractComponent implements ModelContracts.Component {
    private final ComponentId id;
    private final String type;
    private final List<Port> inputs = new ArrayList<>();
    private final List<Port> outputs = new ArrayList<>();
    private final List<Signal> inputValues = new ArrayList<>();
    private final List<Signal> outputValues = new ArrayList<>();

    protected AbstractComponent(String type, int inputCount, int outputCount) {
        this.id = ComponentId.newId();
        this.type = type;
        for (int i = 0; i < inputCount; i++) {
            inputs.add(new Port("in" + i, i));
            inputValues.add(Signal.UNDEFINED);
        }
        for (int i = 0; i < outputCount; i++) {
            outputs.add(new Port("out" + i, i));
            outputValues.add(Signal.UNDEFINED);
        }
    }

    @Override
    public ComponentId getId() { return id; }

    @Override
    public String getType() { return type; }

    @Override
    public List<Port> getInputs() { return List.copyOf(inputs); }

    @Override
    public List<Port> getOutputs() { return List.copyOf(outputs); }

    @Override
    public synchronized void setInputValue(int inputIndex, Signal value) {
        if (inputIndex < 0 || inputIndex >= inputValues.size()) return;
        inputValues.set(inputIndex, value == null ? Signal.UNDEFINED : value);
    }

    protected synchronized Signal getInputValue(int inputIndex) {
        if (inputIndex < 0 || inputIndex >= inputValues.size()) return Signal.UNDEFINED;
        return inputValues.get(inputIndex);
    }

    @Override
    public synchronized Signal getOutputValue(int outputIndex) {
        if (outputIndex < 0 || outputIndex >= outputValues.size()) return Signal.UNDEFINED;
        return outputValues.get(outputIndex);
    }

    protected synchronized void setOutputValue(int outputIndex, Signal value) {
        if (outputIndex < 0 || outputIndex >= outputValues.size()) return;
        outputValues.set(outputIndex, value == null ? Signal.UNDEFINED : value);
    }

    /** Concrete subclasses must implement evaluation using provided inputs and
     * set output values via {@link #setOutputValue(int, Signal)}. */
    @Override
    public abstract void evaluate();

    @Override
    public synchronized Map<String, Object> getState() {
        Map<String, Object> m = new HashMap<>();
        List<String> in = new ArrayList<>();
        for (Signal s : inputValues) in.add(s.name());
        List<String> out = new ArrayList<>();
        for (Signal s : outputValues) out.add(s.name());
        m.put("id", id.id());
        m.put("type", type);
        m.put("inputs", in);
        m.put("outputs", out);
        return m;
    }
}
