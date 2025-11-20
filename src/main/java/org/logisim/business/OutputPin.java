package org.logisim.business;

import static org.logisim.business.ModelContracts.Signal;

/**
 * OutputPin represents an observable output (e.g. LED). It has one input and no outputs.
 * Use {@link #getObservedValue()} after simulation to read the current value.
 */
public class OutputPin extends AbstractComponent {
    private Signal observed = Signal.UNDEFINED;

    public OutputPin() { super("OUTPUT", 1, 0); }

    @Override
    public synchronized void evaluate() {
        observed = getInputValue(0);
    }

    public synchronized Signal getObservedValue() { return observed; }
}
