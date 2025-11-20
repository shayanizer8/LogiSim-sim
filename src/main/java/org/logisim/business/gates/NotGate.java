package org.logisim.business.gates;

import org.logisim.business.AbstractComponent;
import static org.logisim.business.ModelContracts.Signal;

/** 1-input NOT gate implementation */
public class NotGate extends AbstractComponent {
    public NotGate() { super("NOT", 1, 1); }

    @Override
    public void evaluate() {
        Signal a = getInputValue(0);
        if (a == Signal.UNDEFINED) {
            setOutputValue(0, Signal.UNDEFINED);
            return;
        }
        setOutputValue(0, (a == Signal.HIGH) ? Signal.LOW : Signal.HIGH);
    }
}
