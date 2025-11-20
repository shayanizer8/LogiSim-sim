package org.logisim.business;

import org.logisim.business.gates.AndGate;

import java.util.List;

public class DemoMain {
    public static void main(String[] args) {
        // Build circuit: two InputPins -> AND -> OutputPin
        InMemoryCircuit circuit = new InMemoryCircuit("and-demo");

        InputPin inA = new InputPin();
        InputPin inB = new InputPin();
        AndGate and = new AndGate();
        OutputPin out = new OutputPin();

        circuit.addComponent(inA);
        circuit.addComponent(inB);
        circuit.addComponent(and);
        circuit.addComponent(out);

        // connectors: inA.out0 -> and.in0 ; inB.out0 -> and.in1 ; and.out0 -> out.in0
        circuit.addConnector(new BasicConnector("c-a-and", inA.getId(), 0, and.getId(), 0, "black"));
        circuit.addConnector(new BasicConnector("c-b-and", inB.getId(), 0, and.getId(), 1, "black"));
        circuit.addConnector(new BasicConnector("c-and-out", and.getId(), 0, out.getId(), 0, "black"));

        SimulationEngineImpl engine = new SimulationEngineImpl();

        // Generate truth table using the generator
        List<TruthTableGenerator.TruthRow> table = TruthTableGenerator.generate(circuit, engine);

        // Print header (use short component id suffixes)
        List<ModelContracts.ComponentId> inIds = List.copyOf(circuit.getInputComponentIds());
        List<ModelContracts.ComponentId> outIds = List.copyOf(circuit.getOutputComponentIds());

        System.out.print("Inputs ");
        for (var id : inIds) System.out.print(shortId(id) + " ");
        System.out.print("| Outputs ");
        for (var id : outIds) System.out.print(shortId(id) + " ");
        System.out.println();

        for (var row : table) {
            for (var id : inIds) {
                var v = row.inputs().get(id);
                System.out.print((v == ModelContracts.Signal.HIGH ? 1 : 0) + " ");
            }
            System.out.print("| ");
            for (var id : outIds) {
                var v = row.outputs().get(id);
                System.out.print((v == ModelContracts.Signal.HIGH ? 1 : 0) + " ");
            }
            System.out.println();
        }

        // Demonstrate composite circuit usage: wrap the same circuit as a component
        CircuitComponent comp = new CircuitComponent("module1", circuit, inIds, outIds);
        System.out.println("Composite component created with type=" + comp.getType());

        // Generate simple boolean expressions for outputs
        var exprs = BooleanExpressionGenerator.generate(circuit, engine);
        System.out.println("Boolean expressions (sum-of-products):");
        for (var e : exprs.entrySet()) System.out.println(shortId(e.getKey()) + " -> " + e.getValue());
    }

    private static String shortId(ModelContracts.ComponentId id) {
        String s = id.id();
        return s.length() <= 6 ? s : s.substring(s.length() - 6);
    }
}
