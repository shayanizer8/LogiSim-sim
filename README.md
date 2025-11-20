# LogiSim — Business Layer

This folder contains the core business-layer classes for the LogiSim project.
They implement the data model and simulation engine used by the Java Swing UI
and the persistence layer.

Key packages and classes
- `org.logisim.business.ModelContracts` — interfaces and listener contracts (`Component`, `Circuit`, `Connector`, `SimulationEngine`, `ModelChangeListener`).
- `org.logisim.business.AbstractComponent` — base class for components (ports, state handling).
- `org.logisim.business.gates.*` — gate implementations: `AndGate`, `OrGate`, `NotGate`.
- `org.logisim.business.InputPin` / `OutputPin` — simple input/output components for the UI.
- `org.logisim.business.InMemoryCircuit` — in-memory circuit container and simulation logic.
- `org.logisim.business.SimulationEngineImpl` — API the UI can call (`setInputValues`, `simulateOnce`, `collectOutputs`).
- `org.logisim.business.TruthTableGenerator` — utility to enumerate inputs and generate truth tables.
- `org.logisim.business.PersistenceUtil` — helpers to convert a circuit to a Map for JSON/XML persistence.

Quick compile & run (no build tool)
```powershell
mkdir -Force out
$files = Get-ChildItem -Path 'src\main\java' -Recurse -Filter *.java | ForEach-Object FullName
javac -d out $files
java -cp out org.logisim.business.DemoMain
```

Notes for UI integration
- Register a `ModelChangeListener` on `Circuit` to receive component/connector add/remove and component state-change events.
- When receiving `onComponentStateChanged`, marshal UI updates to the Swing EDT using `SwingUtilities.invokeLater(...)`.
- Use `SimulationEngineImpl` to run simulations from the UI thread or a background worker — prefer background execution for long running tasks.

Persistence
- `PersistenceUtil.circuitToMap(circuit)` returns a `Map<String,Object>` suitable for JSON or XML conversion. Restoring requires a component factory and is intentionally left for the data layer.

# Guidance for Teammates

## Anas

- **Purpose:** UI developer — implement the Java Swing interface and visual editing for circuits.
- **What to use from the business layer:**
	- `SimulationEngineImpl` — call `setInputValues(...)`, then `simulateOnce(...)`, and `collectOutputs(...)` to run simulations from the UI. Run simulation on a background thread and update Swing components on the EDT.
	- `ModelContracts.ModelChangeListener` — register via `Circuit.addModelChangeListener(...)` to receive `onComponentAdded`, `onConnectorAdded`, `onComponentRemoved`, `onConnectorRemoved`, and `onComponentStateChanged` events. Use these to keep the canvas in sync with the model.
	- Component `getState()` — use `Component.getState()` to read friendly information (inputs/outputs) for rendering tooltips or property panels. Consider exposing a user-editable `label` in state if you need named inputs.
	- `CircuitComponent` — modules are represented as components; treat them like any other `Component` when embedding in a larger circuit.

- **UI tips:**
	- Keep a deterministic ordering for input/output columns (store the ordering in component state or in your UI model) so truth tables and boolean expressions remain stable.
	- When applying model-driven updates, always marshal UI changes to the Swing EDT (`SwingUtilities.invokeLater(...)`).
	- For wiring visuals, use `ModelContracts.Connector` fields (`getSourceComponentId()`, `getSourcePortIndex()`, `getSinkComponentId()`, `getSinkPortIndex()`) to locate anchors on component figures.

## Haider

- **Purpose:** Data/persistence developer — implement saving/loading circuits to disk (JSON, XML or other formats) and provide unit tests.
- **What to use from the business layer:**
	- `PersistenceUtil.circuitToMap(...)` — serializes a `Circuit` to a `Map<String,Object>` ready for JSON/XML conversion. Use this for saving.
	- `ComponentFactory` (or provide your own) — reconstruct `Component` instances from saved `type` and `state` maps when loading. `PersistenceUtil.mapToCircuit(...)` shows the expected shape and uses `ComponentFactory.create(...)` to rebuild components.
	- `ModelContracts.Component.getState()` — persisted state includes `id`, `type`, `inputs`, and `outputs`; extend this state if you add labels or UI properties so they persist.

- **Persistence tips:**
	- The current restore creates new runtime `ComponentId` values and maps saved ids to newly created components when wiring connectors. If you need to preserve original saved IDs exactly, modify `ComponentId` handling or store the saved id as an explicit field in component state.
	- Add a `schemaVersion` field to your serialized map to support future changes in the persistence format.
	- Provide clear error handling and logging when encountering unknown `type` values during load — the current factory skips unknown types.

---

If you want, I can also add short examples in this README showing the minimal code snippets for UI registration (`ModelChangeListener`) and for save/load using `PersistenceUtil` + `ComponentFactory`.
# LogiSim-sim