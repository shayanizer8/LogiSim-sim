package org.logisim.business;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core interfaces and DTOs for the LogiSim business layer.
 *
 * This file contains lightweight contracts that the rest of the business
 * implementation (and the UI / data layers) should follow. It is intentionally
 * minimal — method signatures only, with documentation — so we can iterate on
 * the concrete implementations.
 */
public final class ModelContracts {
    private ModelContracts() { /* utility holder */ }

    /** A simple component identifier. */
    public static record ComponentId(String id) {
        public static ComponentId newId() { return new ComponentId(UUID.randomUUID().toString()); }
    }

    /** Signal value used in simulation. */
    public enum Signal {
        LOW,
        HIGH,
        UNDEFINED
    }

    /** Lightweight description of a port (input or output). */
    public static record Port(String name, int index) { }

    /**
     * Contract for a circuit component (gate, switch, LED, module, etc.).
     * Implementations should be small, deterministic and thread-confined —
     * simulation orchestration is the job of the SimulationEngine/Circuit.
     */
    public interface Component {
        ComponentId getId();
        String getType(); // e.g. "AND", "OR", "NOT", "SWITCH"

        List<Port> getInputs();
        List<Port> getOutputs();

        /** Set input value for given input port index. */
        void setInputValue(int inputIndex, Signal value);

        /** Get last computed output value for given output port index. */
        Signal getOutputValue(int outputIndex);

        /** Evaluate outputs from current inputs — pure function style preferred. */
        void evaluate();

        /** Snapshot of internal state useful for UI rendering or serialization. */
        Map<String, Object> getState();
    }

    /**
     * Connector transfers a signal from a source component's output port to one or
     * more sink component input ports. Implementations should be lightweight and
     * only carry routing/visual information and propagation helpers.
     */
    public interface Connector {
        String getId();
        ComponentId getSourceComponentId();
        int getSourcePortIndex();

        ComponentId getSinkComponentId();
        int getSinkPortIndex();

        /** Optional color or label used by the UI. */
        String getColor();
    }

    /** Container for components and connectors that form a runnable circuit. */
    public interface Circuit {
        String getName();

        void addComponent(Component component);
        void removeComponent(ComponentId id);
        Component getComponent(ComponentId id);

        void addConnector(Connector connector);
        void removeConnector(String connectorId);

        List<Component> getComponents();
        List<Connector> getConnectors();

        /**
         * Returns the set of components considered external inputs for truth-table
         * generation and simulation initialization (e.g. switches / input pins).
         */
        Set<ComponentId> getInputComponentIds();

        /** Returns components that are observed as outputs (LEDs / output pins). */
        Set<ComponentId> getOutputComponentIds();

        /**
         * Perform a full simulation of this circuit using the provided engine.
         * Implementations may delegate to a SimulationEngine or provide an internal
         * execution method.
         */
        void simulate();

        /** Register a listener to receive model change events (component/connector add/remove). */
        void addModelChangeListener(ModelChangeListener listener);

        /** Remove a previously registered model change listener. */
        void removeModelChangeListener(ModelChangeListener listener);
    }

    /** Top-level project that may contain multiple circuits (modules). */
    public interface Project {
        String getName();
        void setName(String name);

        void addCircuit(Circuit circuit);
        void removeCircuit(String circuitName);
        Circuit getCircuit(String circuitName);
        List<Circuit> listCircuits();
    }

    /**
     * Simulation engine responsible for running circuit evaluations and propagating
     * signals. The UI should interact with the engine via this contract.
     */
    public interface SimulationEngine {
        /** Register a listener to receive simulation events. */
        void addListener(SimulationListener listener);

        void removeListener(SimulationListener listener);

        /** Set input values for input components before running a simulation. */
        void setInputValues(Circuit circuit, Map<ComponentId, Map<Integer, Signal>> values);

        /** Run a single synchronous simulation pass for the provided circuit. */
        void simulateOnce(Circuit circuit) throws SimulationException;

        /** Collect output values after simulation. */
        Map<ComponentId, Map<Integer, Signal>> collectOutputs(Circuit circuit);
    }

    /** Listener for simulation lifecycle events (suitable for UI updates). */
    public interface SimulationListener {
        void onSimulationStep(Circuit circuit);
        void onSimulationComplete(Circuit circuit);
        void onSimulationError(Circuit circuit, SimulationException error);
    }

    /** Listener for model changes (component / connector add/remove) — used by UI. */
    public interface ModelChangeListener {
        void onComponentAdded(Circuit circuit, Component component);
        void onComponentRemoved(Circuit circuit, ComponentId id);
        void onConnectorAdded(Circuit circuit, Connector connector);
        void onConnectorRemoved(Circuit circuit, String connectorId);
        /**
         * Called when a component's output values change during simulation.
         * The map contains output port index -> Signal value.
         */
        void onComponentStateChanged(Circuit circuit, Component component, Map<Integer, Signal> outputs);
    }

    /** Runtime exception used to signal simulation-related errors. */
    public static final class SimulationException extends RuntimeException {
        public SimulationException(String message) { super(message); }
        public SimulationException(String message, Throwable cause) { super(message, cause); }
    }
}
