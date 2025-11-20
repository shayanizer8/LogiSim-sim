package org.logisim.business;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Simple in-memory Project implementation. */
public class ProjectImpl implements ModelContracts.Project {
    private String name;
    private final Map<String, ModelContracts.Circuit> circuits = new HashMap<>();

    public ProjectImpl(String name) { this.name = name; }

    @Override public String getName() { return name; }
    @Override public void setName(String name) { this.name = name; }

    @Override public void addCircuit(ModelContracts.Circuit circuit) { circuits.put(circuit.getName(), circuit); }
    @Override public void removeCircuit(String circuitName) { circuits.remove(circuitName); }
    @Override public ModelContracts.Circuit getCircuit(String circuitName) { return circuits.get(circuitName); }
    @Override public List<ModelContracts.Circuit> listCircuits() { return new ArrayList<>(circuits.values()); }
}
