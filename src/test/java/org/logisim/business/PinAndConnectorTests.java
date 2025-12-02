package org.logisim.business;

import static org.logisim.business.ModelContracts.Signal;

/**
 * Small, easy-to-read tests for InputPin, OutputPin and BasicConnector.
 *
 * Run from project root:
 *   javac -cp "src/main/java;bin" -d bin src/test/java/org/logisim/business/PinAndConnectorTests.java
 *   java  -cp bin org.logisim.business.PinAndConnectorTests
 */
public class PinAndConnectorTests {

    private static int failures = 0;
    private static int total = 0;

    public static void main(String[] args) {
        System.out.println("Running PinAndConnectorTests...");

        testInputPinStateAndOutputs();
        testOutputPinObservedValue();
        testBasicConnectorStoresData();

        System.out.println();
        System.out.println("Total assertions: " + total);
        System.out.println("Failures: " + failures);

        if (failures == 0) {
            System.out.println("ALL TESTS PASSED");
        } else {
            System.err.println("SOME TESTS FAILED");
        }

        System.exit(failures == 0 ? 0 : 1);
    }

    private static void assertEquals(String name, Object expected, Object actual) {
        total++;
        if ((expected == null && actual != null) || (expected != null && !expected.equals(actual))) {
            failures++;
            System.err.println("FAIL [" + name + "] expected=" + expected + " actual=" + actual);
        }
    }

    private static void testInputPinStateAndOutputs() {
        InputPin pin = new InputPin();

        // InputPin has 0 inputs and 1 output
        assertEquals("InputPin inputs size", 0, pin.getInputs().size());
        assertEquals("InputPin outputs size", 1, pin.getOutputs().size());

        // Default state should be UNDEFINED
        assertEquals("InputPin default output", Signal.UNDEFINED, pin.getOutputValue(0));

        // Setting LOW and HIGH should be reflected on output
        pin.setState(Signal.LOW);
        assertEquals("InputPin LOW", Signal.LOW, pin.getOutputValue(0));

        pin.setState(Signal.HIGH);
        assertEquals("InputPin HIGH", Signal.HIGH, pin.getOutputValue(0));

        // evaluate() should not change the externally driven output
        pin.evaluate();
        assertEquals("InputPin evaluate keeps value", Signal.HIGH, pin.getOutputValue(0));
    }

    private static void testOutputPinObservedValue() {
        OutputPin pin = new OutputPin();

        // OutputPin has 1 input and 0 outputs
        assertEquals("OutputPin inputs size", 1, pin.getInputs().size());
        assertEquals("OutputPin outputs size", 0, pin.getOutputs().size());

        // Before any evaluation, observed value should be UNDEFINED
        assertEquals("OutputPin default observed", Signal.UNDEFINED, pin.getObservedValue());

        // When we set its input and call evaluate(), observed should update
        pin.setInputValue(0, Signal.LOW);
        pin.evaluate();
        assertEquals("OutputPin observed LOW", Signal.LOW, pin.getObservedValue());

        pin.setInputValue(0, Signal.HIGH);
        pin.evaluate();
        assertEquals("OutputPin observed HIGH", Signal.HIGH, pin.getObservedValue());
    }

    private static void testBasicConnectorStoresData() {
        // We just want to be sure the connector keeps what we pass in.
        var src = new InputPin();
        var dst = new OutputPin();

        BasicConnector c = new BasicConnector(
                "c1",
                src.getId(),
                0,
                dst.getId(),
                1,
                null // let it default the color
        );

        assertEquals("connector id", "c1", c.getId());
        assertEquals("connector src id", src.getId(), c.getSourceComponentId());
        assertEquals("connector src port", 0, c.getSourcePortIndex());
        assertEquals("connector dst id", dst.getId(), c.getSinkComponentId());
        assertEquals("connector dst port", 1, c.getSinkPortIndex());
        assertEquals("connector default color", "black", c.getColor());
    }
}


