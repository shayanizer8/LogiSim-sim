package org.logisim.business.gates;

import org.logisim.business.AbstractComponent;
import static org.logisim.business.ModelContracts.Signal;

/** 2-input AND gate implementation */
public class AndGate extends AbstractComponent {
    public AndGate() { super("AND", 2, 1); }

    @Override
    public void evaluate() {
        Signal a = getInputValue(0);
        Signal b = getInputValue(1);
        if (a == Signal.UNDEFINED || b == Signal.UNDEFINED) {
            setOutputValue(0, Signal.UNDEFINED);
            return;
        }
        setOutputValue(0, (a == Signal.HIGH && b == Signal.HIGH) ? Signal.HIGH : Signal.LOW);
    }
}
