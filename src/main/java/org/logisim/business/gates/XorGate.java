package org.logisim.business.gates;

import org.logisim.business.AbstractComponent;
import static org.logisim.business.ModelContracts.Signal;

/** 2-input XOR gate implementation */
public class XorGate extends AbstractComponent {
    public XorGate() { super("XOR", 2, 1); }

    @Override
    public void evaluate() {
        Signal a = getInputValue(0);
        Signal b = getInputValue(1);
        if (a == Signal.UNDEFINED || b == Signal.UNDEFINED) {
            setOutputValue(0, Signal.UNDEFINED);
            return;
        }
        setOutputValue(0, (a != b) ? Signal.HIGH : Signal.LOW);
    }
}