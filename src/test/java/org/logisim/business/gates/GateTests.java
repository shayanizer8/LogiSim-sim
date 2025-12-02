package org.logisim.business.gates;

import org.logisim.business.ModelContracts.Signal;

/**
 * Lightweight unit tests for basic gates.
 *
 * This avoids any external test framework so there are no extra
 * dependencies. Compile and run via (from project root):
 *
 *   javac -cp "src/main/java;bin" -d bin src/test/java/org/logisim/business/gates/GateTests.java
 *   java  -cp bin org.logisim.business.gates.GateTests
 *
 * You can also run only one group of gate tests:
 *
 *   java -cp bin org.logisim.business.gates.GateTests AND
 *   java -cp bin org.logisim.business.gates.GateTests OR
 *   java -cp bin org.logisim.business.gates.GateTests NOT
 *   java -cp bin org.logisim.business.gates.GateTests NAND
 *   java -cp bin org.logisim.business.gates.GateTests NOR
 *   java -cp bin org.logisim.business.gates.GateTests XOR
 *
 * (On Linux/macOS, replace ';' with ':'.)
 */
public class GateTests {

    private static int failures = 0;
    private static int total = 0;

    public static void main(String[] args) {
        System.out.println("Running gate tests...");

        boolean runAll = (args == null || args.length == 0);
        boolean runAND  = runAll || contains(args, "AND");
        boolean runOR   = runAll || contains(args, "OR");
        boolean runNOT  = runAll || contains(args, "NOT");
        boolean runNAND = runAll || contains(args, "NAND");
        boolean runNOR  = runAll || contains(args, "NOR");
        boolean runXOR  = runAll || contains(args, "XOR");

        if (runAND)  testAndGateTruthTable();
        if (runOR)   testOrGateTruthTable();
        if (runNOT)  testNotGateTruthTable();
        if (runNAND) testNandGateTruthTable();
        if (runNOR)  testNorGateTruthTable();
        if (runXOR)  testXorGateTruthTable();

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

    private static boolean contains(String[] args, String name) {
        for (String a : args) {
            if (a != null && a.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static void assertEquals(String testName, Signal expected, Signal actual) {
        total++;
        if (expected != actual) {
            failures++;
            System.err.println("FAIL [" + testName + "] expected=" + expected + " actual=" + actual);
        }
    }

    // --- AND ---
    private static void testAndGateTruthTable() {
        AndGate gate = new AndGate();

        gate.setInputValue(0, Signal.LOW);
        gate.setInputValue(1, Signal.LOW);
        gate.evaluate();
        assertEquals("AND 0&0", Signal.LOW, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.LOW);
        gate.setInputValue(1, Signal.HIGH);
        gate.evaluate();
        assertEquals("AND 0&1", Signal.LOW, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.HIGH);
        gate.setInputValue(1, Signal.LOW);
        gate.evaluate();
        assertEquals("AND 1&0", Signal.LOW, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.HIGH);
        gate.setInputValue(1, Signal.HIGH);
        gate.evaluate();
        assertEquals("AND 1&1", Signal.HIGH, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.UNDEFINED);
        gate.setInputValue(1, Signal.HIGH);
        gate.evaluate();
        assertEquals("AND UNDEF&1", Signal.UNDEFINED, gate.getOutputValue(0));
    }

    // --- OR ---
    private static void testOrGateTruthTable() {
        OrGate gate = new OrGate();

        gate.setInputValue(0, Signal.LOW);
        gate.setInputValue(1, Signal.LOW);
        gate.evaluate();
        assertEquals("OR 0|0", Signal.LOW, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.LOW);
        gate.setInputValue(1, Signal.HIGH);
        gate.evaluate();
        assertEquals("OR 0|1", Signal.HIGH, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.HIGH);
        gate.setInputValue(1, Signal.LOW);
        gate.evaluate();
        assertEquals("OR 1|0", Signal.HIGH, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.HIGH);
        gate.setInputValue(1, Signal.HIGH);
        gate.evaluate();
        assertEquals("OR 1|1", Signal.HIGH, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.UNDEFINED);
        gate.setInputValue(1, Signal.LOW);
        gate.evaluate();
        assertEquals("OR UNDEF|0", Signal.UNDEFINED, gate.getOutputValue(0));
    }

    // --- NOT ---
    private static void testNotGateTruthTable() {
        NotGate gate = new NotGate();

        gate.setInputValue(0, Signal.LOW);
        gate.evaluate();
        assertEquals("NOT 0", Signal.HIGH, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.HIGH);
        gate.evaluate();
        assertEquals("NOT 1", Signal.LOW, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.UNDEFINED);
        gate.evaluate();
        assertEquals("NOT UNDEF", Signal.UNDEFINED, gate.getOutputValue(0));
    }

    // --- NAND ---
    private static void testNandGateTruthTable() {
        NandGate gate = new NandGate();

        gate.setInputValue(0, Signal.LOW);
        gate.setInputValue(1, Signal.LOW);
        gate.evaluate();
        assertEquals("NAND 0&0", Signal.HIGH, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.LOW);
        gate.setInputValue(1, Signal.HIGH);
        gate.evaluate();
        assertEquals("NAND 0&1", Signal.HIGH, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.HIGH);
        gate.setInputValue(1, Signal.LOW);
        gate.evaluate();
        assertEquals("NAND 1&0", Signal.HIGH, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.HIGH);
        gate.setInputValue(1, Signal.HIGH);
        gate.evaluate();
        assertEquals("NAND 1&1", Signal.LOW, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.UNDEFINED);
        gate.setInputValue(1, Signal.HIGH);
        gate.evaluate();
        assertEquals("NAND UNDEF&1", Signal.UNDEFINED, gate.getOutputValue(0));
    }

    // --- NOR ---
    private static void testNorGateTruthTable() {
        NorGate gate = new NorGate();

        gate.setInputValue(0, Signal.LOW);
        gate.setInputValue(1, Signal.LOW);
        gate.evaluate();
        assertEquals("NOR 0|0", Signal.HIGH, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.LOW);
        gate.setInputValue(1, Signal.HIGH);
        gate.evaluate();
        assertEquals("NOR 0|1", Signal.LOW, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.HIGH);
        gate.setInputValue(1, Signal.LOW);
        gate.evaluate();
        assertEquals("NOR 1|0", Signal.LOW, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.HIGH);
        gate.setInputValue(1, Signal.HIGH);
        gate.evaluate();
        assertEquals("NOR 1|1", Signal.LOW, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.UNDEFINED);
        gate.setInputValue(1, Signal.HIGH);
        gate.evaluate();
        assertEquals("NOR UNDEF|1", Signal.UNDEFINED, gate.getOutputValue(0));
    }

    // --- XOR ---
    private static void testXorGateTruthTable() {
        XorGate gate = new XorGate();

        gate.setInputValue(0, Signal.LOW);
        gate.setInputValue(1, Signal.LOW);
        gate.evaluate();
        assertEquals("XOR 0^0", Signal.LOW, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.LOW);
        gate.setInputValue(1, Signal.HIGH);
        gate.evaluate();
        assertEquals("XOR 0^1", Signal.HIGH, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.HIGH);
        gate.setInputValue(1, Signal.LOW);
        gate.evaluate();
        assertEquals("XOR 1^0", Signal.HIGH, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.HIGH);
        gate.setInputValue(1, Signal.HIGH);
        gate.evaluate();
        assertEquals("XOR 1^1", Signal.LOW, gate.getOutputValue(0));

        gate.setInputValue(0, Signal.UNDEFINED);
        gate.setInputValue(1, Signal.HIGH);
        gate.evaluate();
        assertEquals("XOR UNDEF^1", Signal.UNDEFINED, gate.getOutputValue(0));
    }
}



