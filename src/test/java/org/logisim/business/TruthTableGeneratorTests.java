package org.logisim.business;

import java.util.List;
import java.util.Map;

import static org.logisim.business.ModelContracts.ComponentId;
import static org.logisim.business.ModelContracts.Signal;

/**
 * Tests for {@link TruthTableGenerator}.
 *
 * We reuse the same simple circuit as in SimulationEngineImplTests:
 *   InputPin --(x)-> AND gate (both inputs) --(output)-> OutputPin
 *
 * For this circuit, x AND x == x so the truth table should be:
 *   input LOW  -> output LOW
 *   input HIGH -> output HIGH
 *
 * Run from project root:
 *   javac -cp "src/main/java;bin" -d bin src/test/java/org/logisim/business/TruthTableGeneratorTests.java
 *   java  -cp bin org.logisim.business.TruthTableGeneratorTests
 */
public class TruthTableGeneratorTests {

    private static int failures = 0;
    private static int total = 0;

    public static void main(String[] args) {
        System.out.println("Running TruthTableGenerator tests...");

        testTruthTableForSimpleAndCircuit();

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

    private static class SimpleCircuit {
        final InMemoryCircuit circuit;
        final InputPin input;
        final OutputPin output;
        final ComponentId inputId;
        final ComponentId outputId;

        SimpleCircuit() {
            this.circuit = new InMemoryCircuit("simple-truth");
            this.input = new InputPin();
            var and = new org.logisim.business.gates.AndGate();
            this.output = new OutputPin();

            circuit.addComponent(input);
            circuit.addComponent(and);
            circuit.addComponent(output);

            // capture ids for ordering
            this.inputId = input.getId();
            this.outputId = output.getId();

            circuit.addConnector(new BasicConnector("c1", input.getId(), 0, and.getId(), 0, "black"));
            circuit.addConnector(new BasicConnector("c2", input.getId(), 0, and.getId(), 1, "black"));
            circuit.addConnector(new BasicConnector("c3", and.getId(), 0, output.getId(), 0, "black"));
        }
    }

    private static void testTruthTableForSimpleAndCircuit() {
        SimpleCircuit sc = new SimpleCircuit();
        SimulationEngineImpl engine = new SimulationEngineImpl();

        // Force deterministic ordering: [input] for inputs and [output] for outputs
        List<ComponentId> inputOrder = List.of(sc.inputId);
        List<ComponentId> outputOrder = List.of(sc.outputId);

        var rows = TruthTableGenerator.generate(sc.circuit, engine, inputOrder, outputOrder);

        // We expect 2^1 = 2 rows
        assertEquals("row count", 2, rows.size());

        // Row 0: input LOW -> output LOW
        TruthTableGenerator.TruthRow row0 = rows.get(0);
        Signal in0 = row0.inputs().get(sc.inputId);
        Signal out0 = row0.outputs().get(sc.outputId);
        assertEquals("row0 input", Signal.LOW, in0);
        assertEquals("row0 output", Signal.LOW, out0);

        // Row 1: input HIGH -> output HIGH
        TruthTableGenerator.TruthRow row1 = rows.get(1);
        Signal in1 = row1.inputs().get(sc.inputId);
        Signal out1 = row1.outputs().get(sc.outputId);
        assertEquals("row1 input", Signal.HIGH, in1);
        assertEquals("row1 output", Signal.HIGH, out1);
    }
}


