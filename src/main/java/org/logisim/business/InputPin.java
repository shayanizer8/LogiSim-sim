package org.logisim.business;

import static org.logisim.business.ModelContracts.Signal;

/**
 * InputPin acts like a switch / input source. It has no inputs and one output.
 * The UI or tests can call {@link #setState(Signal)} to drive the circuit.
 */
public class InputPin extends AbstractComponent {
    public InputPin() { super("INPUT", 0, 1); }

    /** Drive the output of this input pin. */
    public void setState(Signal value) { setOutputValue(0, value == null ? Signal.UNDEFINED : value); }

    @Override
    public void evaluate() {
        // InputPin's output is externally driven; nothing to compute here.
    }
}
