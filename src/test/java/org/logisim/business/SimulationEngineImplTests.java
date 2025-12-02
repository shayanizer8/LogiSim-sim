package org.logisim.business;

import java.util.HashMap;
import java.util.Map;

import static org.logisim.business.ModelContracts.ComponentId;
import static org.logisim.business.ModelContracts.Signal;

/**
 * Very small, focused tests for {@link SimulationEngineImpl}.
 *
 * We build a tiny circuit in code:
 *   InputPin --(x)-> AND gate (both inputs) --(output)-> OutputPin
 *
 * Because x AND x == x, the output should always match the input.
 *
 * Run from project root:
 *   javac -cp "src/main/java;bin" -d bin src/test/java/org/logisim/business/SimulationEngineImplTests.java
 *   java  -cp bin org.logisim.business.SimulationEngineImplTests
 */
public class SimulationEngineImplTests {

    private static int failures = 0;
    private static int total = 0;

    public static void main(String[] args) {
        System.out.println("Running SimulationEngineImpl tests...");

        testSingleInputAndGateCircuit_lowGivesLow();
        testSingleInputAndGateCircuit_highGivesHigh();
        testSetInputValuesHelper();

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

    /**
     * Helper that builds and returns a simple circuit containing:
     * input -> andGate (both inputs) -> output
     */
    private static class SimpleCircuit {
        final InMemoryCircuit circuit;
        final InputPin input;
        final OutputPin output;

        SimpleCircuit() {
            this.circuit = new InMemoryCircuit("simple");
            this.input = new InputPin();
            var and = new org.logisim.business.gates.AndGate();
            this.output = new OutputPin();

            circuit.addComponent(input);
            circuit.addComponent(and);
            circuit.addComponent(output);

            // wire: input out0 -> and in0 and in1
            circuit.addConnector(new BasicConnector("c1", input.getId(), 0, and.getId(), 0, "black"));
            circuit.addConnector(new BasicConnector("c2", input.getId(), 0, and.getId(), 1, "black"));
            // wire: and out0 -> output in0
            circuit.addConnector(new BasicConnector("c3", and.getId(), 0, output.getId(), 0, "black"));
        }
    }

    private static void testSingleInputAndGateCircuit_lowGivesLow() {
        SimpleCircuit sc = new SimpleCircuit();
        SimulationEngineImpl engine = new SimulationEngineImpl();

        sc.input.setState(Signal.LOW);
        engine.simulateOnce(sc.circuit);

        Signal observed = sc.output.getObservedValue();
        assertEquals("LOW through simple AND circuit", Signal.LOW, observed);
    }

    private static void testSingleInputAndGateCircuit_highGivesHigh() {
        SimpleCircuit sc = new SimpleCircuit();
        SimulationEngineImpl engine = new SimulationEngineImpl();

        sc.input.setState(Signal.HIGH);
        engine.simulateOnce(sc.circuit);

        Signal observed = sc.output.getObservedValue();
        assertEquals("HIGH through simple AND circuit", Signal.HIGH, observed);
    }

    /**
     * Test the setInputValues helper: it should set the first input of a component.
     */
    private static void testSetInputValuesHelper() {
        InMemoryCircuit circuit = new InMemoryCircuit("helper");
        var and = new org.logisim.business.gates.AndGate();
        circuit.addComponent(and);

        SimulationEngineImpl engine = new SimulationEngineImpl();

        Map<ComponentId, Map<Integer, Signal>> values = new HashMap<>();
        Map<Integer, Signal> andInputs = new HashMap<>();
        andInputs.put(0, Signal.HIGH);
        andInputs.put(1, Signal.HIGH);
        values.put(and.getId(), andInputs);

        engine.setInputValues(circuit, values);
        and.evaluate();

        Signal out = and.getOutputValue(0);
        assertEquals("setInputValues sets both inputs to HIGH", Signal.HIGH, out);
    }
}


