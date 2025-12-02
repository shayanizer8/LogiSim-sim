package org.logisim.business;

import java.util.List;
import java.util.Map;

import static org.logisim.business.ModelContracts.ComponentId;
import static org.logisim.business.ModelContracts.Signal;

/**
 * Simple tests for InMemoryCircuit, ProjectImpl, PersistenceUtil and BooleanExpressionGenerator.
 *
 * Run from project root:
 *   javac -cp "src/main/java;bin" -d bin src/test/java/org/logisim/business/ProjectAndCircuitTests.java
 *   java  -cp bin org.logisim.business.ProjectAndCircuitTests
 */
public class ProjectAndCircuitTests {

    private static int failures = 0;
    private static int total = 0;

    public static void main(String[] args) {
        System.out.println("Running ProjectAndCircuitTests...");

        testInMemoryCircuitInputAndOutputSets();
        testInMemoryCircuitSimulateCopiesSignal();
        testProjectImplStoresCircuits();
        testPersistenceRoundTripForSimpleCircuit();
        testBooleanExpressionGeneratorForIdentityCircuit();
        testBooleanExpressionGeneratorForAlwaysZero();
        testBooleanExpressionGeneratorForTwoInputAnd();

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

        SimpleCircuit(String name) {
            this.circuit = new InMemoryCircuit(name);
            this.input = new InputPin();
            var and = new org.logisim.business.gates.AndGate();
            this.output = new OutputPin();

            circuit.addComponent(input);
            circuit.addComponent(and);
            circuit.addComponent(output);

            this.inputId = input.getId();
            this.outputId = output.getId();

            circuit.addConnector(new BasicConnector("c1", input.getId(), 0, and.getId(), 0, "black"));
            circuit.addConnector(new BasicConnector("c2", input.getId(), 0, and.getId(), 1, "black"));
            circuit.addConnector(new BasicConnector("c3", and.getId(), 0, output.getId(), 0, "black"));
        }
    }

    private static void testInMemoryCircuitInputAndOutputSets() {
        SimpleCircuit sc = new SimpleCircuit("io-sets");

        var inputIds = sc.circuit.getInputComponentIds();
        var outputIds = sc.circuit.getOutputComponentIds();

        // InputPin has no inputs, OutputPin has no outputs, so they should be listed.
        assertEquals("input set size", 1, inputIds.size());
        assertEquals("output set size", 1, outputIds.size());
        assertEquals("input set contains input", true, inputIds.contains(sc.inputId));
        assertEquals("output set contains output", true, outputIds.contains(sc.outputId));
    }

    private static void testInMemoryCircuitSimulateCopiesSignal() {
        SimpleCircuit sc = new SimpleCircuit("simulate");

        sc.input.setState(Signal.HIGH);
        sc.circuit.simulate();

        Signal observed = sc.output.getObservedValue();
        assertEquals("simulate() propagates HIGH through simple circuit", Signal.HIGH, observed);
    }

    private static void testProjectImplStoresCircuits() {
        ProjectImpl project = new ProjectImpl("P1");
        SimpleCircuit sc1 = new SimpleCircuit("C1");
        SimpleCircuit sc2 = new SimpleCircuit("C2");

        project.addCircuit(sc1.circuit);
        project.addCircuit(sc2.circuit);

        assertEquals("project name", "P1", project.getName());
        assertEquals("project list size", 2, project.listCircuits().size());
        assertEquals("get C1", "C1", project.getCircuit("C1").getName());

        project.removeCircuit("C1");
        assertEquals("after remove C1 size", 1, project.listCircuits().size());
    }

    private static void testPersistenceRoundTripForSimpleCircuit() {
        SimpleCircuit sc = new SimpleCircuit("persist");

        // set a value and simulate so that state maps have something in them
        sc.input.setState(Signal.LOW);
        sc.circuit.simulate();

        Map<String, Object> map = PersistenceUtil.circuitToMap(sc.circuit);
        java.util.Map<String, ComponentId> savedToRuntime = new java.util.HashMap<>();
        var restored = PersistenceUtil.mapToCircuit(map, savedToRuntime);

        assertEquals("restored name", "persist", restored.getName());
        assertEquals("restored component count", sc.circuit.getComponents().size(), restored.getComponents().size());
        assertEquals("restored connector count", sc.circuit.getConnectors().size(), restored.getConnectors().size());

        // savedToRuntime map should have an entry per original component
        assertEquals("savedToRuntime size", sc.circuit.getComponents().size(), savedToRuntime.size());
    }

    private static void testBooleanExpressionGeneratorForIdentityCircuit() {
        SimpleCircuit sc = new SimpleCircuit("expr");
        SimulationEngineImpl engine = new SimulationEngineImpl();

        var expressions = BooleanExpressionGenerator.generate(sc.circuit, engine);

        // For a single input where output == input, expression should be just "A"
        assertEquals("expression map size", 1, expressions.size());
        String expr = expressions.get(sc.outputId);
        assertEquals("expression for identity", "A", expr);
    }

    /**
     * Circuit where there is one input pin and one output pin, but they are not connected.
     * No matter what the input is, the output pin never becomes HIGH, so the expression
     * should be constant 0.
     */
    private static void testBooleanExpressionGeneratorForAlwaysZero() {
        InMemoryCircuit circuit = new InMemoryCircuit("zero");
        InputPin input = new InputPin();
        OutputPin output = new OutputPin();

        circuit.addComponent(input);
        circuit.addComponent(output);

        SimulationEngineImpl engine = new SimulationEngineImpl();
        var expressions = BooleanExpressionGenerator.generate(circuit, engine);

        // We have one output component -> one expression
        assertEquals("zero expression map size", 1, expressions.size());
        String expr = expressions.get(output.getId());
        assertEquals("expression for always-zero circuit", "0", expr);
    }

    /**
     * Circuit with two input pins driving an AND gate, which drives one output pin.
     * For such a circuit, the (non-minimized) sum-of-products expression should be
     * logically equivalent to "A & B". The generator produces exactly "A & B" or "B & A"
     * depending on input component ordering, so we accept either string.
     */
    private static void testBooleanExpressionGeneratorForTwoInputAnd() {
        InMemoryCircuit circuit = new InMemoryCircuit("and2");
        InputPin in1 = new InputPin();
        InputPin in2 = new InputPin();
        var and = new org.logisim.business.gates.AndGate();
        OutputPin out = new OutputPin();

        circuit.addComponent(in1);
        circuit.addComponent(in2);
        circuit.addComponent(and);
        circuit.addComponent(out);

        // wire: in1 -> and.in0, in2 -> and.in1, and.out0 -> out.in0
        circuit.addConnector(new BasicConnector("c1", in1.getId(), 0, and.getId(), 0, "black"));
        circuit.addConnector(new BasicConnector("c2", in2.getId(), 0, and.getId(), 1, "black"));
        circuit.addConnector(new BasicConnector("c3", and.getId(), 0, out.getId(), 0, "black"));

        SimulationEngineImpl engine = new SimulationEngineImpl();
        var expressions = BooleanExpressionGenerator.generate(circuit, engine);

        assertEquals("two-input AND expression map size", 1, expressions.size());
        String expr = expressions.get(out.getId());

        // Order of variables can be "A & B" or "B & A" depending on internal ordering.
        boolean ok = "A & B".equals(expr) || "B & A".equals(expr);
        assertEquals("expression for two-input AND is A & B (up to order)", true, ok);
    }
}


