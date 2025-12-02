package org.logisim.ui.persistence;

import org.logisim.business.ModelContracts;
import org.logisim.data.ProjectDataStore;
import org.logisim.ui.model.CanvasModel;

import java.awt.Point;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * UI-layer coordinator that saves/loads a project (business model)
 * plus canvas/layout information. The actual JSON/data access is
 * handled by the data-layer {@link ProjectDataStore}.
 */
public class ProjectPersistenceService {

    /**
     * Save the given project and its per-circuit canvas layouts.
     *
     * @param project the business-layer project
     * @param layouts map from circuit name -> {@link CanvasModel}
     * @param path    target file path
     */
    public void save(ModelContracts.Project project,
                     Map<String, CanvasModel> layouts,
                     Path path) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(path, "path");

        // Convert CanvasModel layouts to plain Map structures for the data layer
        Map<String, Map<String, Object>> layoutMaps = new LinkedHashMap<>();
        if (layouts != null) {
            for (ModelContracts.Circuit circuit : project.listCircuits()) {
                CanvasModel canvas = layouts.get(circuit.getName());
                Map<String, Object> layoutMap = serializeLayout(canvas);
                layoutMaps.put(circuit.getName(), layoutMap);
            }
        }

        ProjectDataStore.save(project, layoutMaps, path);
    }

    /**
     * Load a project and its canvas layouts from the given file.
     * Uses {@link ProjectDataStore} for the business model + raw
     * layout maps, then adapts those maps into {@link CanvasModel}
     * instances for the UI.
     */
    public LoadResult load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");

        ProjectDataStore.LoadResult dataResult = ProjectDataStore.load(path);
        ModelContracts.Project project = dataResult.project();

        Map<String, CanvasModel> canvasLayouts = new LinkedHashMap<>();
        Map<String, Map<String, Object>> layoutMaps = dataResult.layouts();
        Map<String, Map<String, ModelContracts.ComponentId>> idMappings = dataResult.savedToRuntimeIds();

        // For each circuit, adapt its layout map into a CanvasModel
        for (ModelContracts.Circuit circuit : project.listCircuits()) {
            String name = circuit.getName();
            Map<String, Object> layoutMap = layoutMaps.get(name);
            Map<String, ModelContracts.ComponentId> savedToRuntime =
                    idMappings.getOrDefault(name, Map.of());

            CanvasModel canvas = new CanvasModel();
            applyLayout(layoutMap, circuit, savedToRuntime, canvas);
            canvasLayouts.put(name, canvas);
        }

        return new LoadResult(project, canvasLayouts);
    }

    /**
     * Convert a {@link CanvasModel} into a plain Map layout representation
     * suitable for persistence. This keeps all UI-specific types out of
     * the data layer.
     */
    private Map<String, Object> serializeLayout(CanvasModel canvas) {
        Map<String, Object> layout = new LinkedHashMap<>();
        List<Map<String, Object>> comps = new ArrayList<>();

        if (canvas != null) {
            for (var fig : canvas.getFigures()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", fig.component().getId().id());
                map.put("x", fig.bounds().x);
                map.put("y", fig.bounds().y);
                map.put("label", fig.label());
                comps.add(map);
            }
            List<String> inputOrder = canvas.getInputOrder().stream().map(id -> id.id()).toList();
            List<String> outputOrder = canvas.getOutputOrder().stream().map(id -> id.id()).toList();
            layout.put("inputOrder", inputOrder);
            layout.put("outputOrder", outputOrder);
        }

        layout.put("components", comps);
        return layout;
    }

    /**
     * Apply a persisted layout map to a {@link CanvasModel}, resolving saved
     * component ids to runtime ids using the provided {@code savedToRuntime}
     * mapping (produced during circuit reconstruction).
     */
    private void applyLayout(Object layoutObj,
                             ModelContracts.Circuit circuit,
                             Map<String, ModelContracts.ComponentId> savedToRuntime,
                             CanvasModel canvas) {
        if (!(layoutObj instanceof Map<?, ?> layoutMap)) {
            autoDistribute(circuit, canvas);
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comps = layoutMap.get("components") instanceof List
                ? (List<Map<String, Object>>) layoutMap.get("components")
                : List.of();

        int autoX = 30;
        int autoY = 40;
        for (Map<String, Object> comp : comps) {
            String savedId = comp.get("id") == null ? null : comp.get("id").toString();
            ModelContracts.ComponentId runtimeId = savedToRuntime.get(savedId);
            ModelContracts.Component component = runtimeId == null ? null : circuit.getComponent(runtimeId);
            if (component == null) continue;

            int x = parseInt(comp.get("x"), autoX);
            int y = parseInt(comp.get("y"), autoY);
            canvas.upsertComponent(component, new Point(x, y));

            Object label = comp.get("label");
            if (label != null) canvas.renameComponent(component.getId(), label.toString());

            autoX += 40;
            autoY += 20;
        }

        canvas.refreshPortOrdering(circuit.getInputComponentIds(), circuit.getOutputComponentIds());

        @SuppressWarnings("unchecked")
        List<String> inputOrder = layoutMap.get("inputOrder") instanceof List
                ? (List<String>) layoutMap.get("inputOrder")
                : List.of();
        canvas.setInputOrderDirect(resolveOrder(inputOrder, savedToRuntime));

        @SuppressWarnings("unchecked")
        List<String> outputOrder = layoutMap.get("outputOrder") instanceof List
                ? (List<String>) layoutMap.get("outputOrder")
                : List.of();
        canvas.setOutputOrderDirect(resolveOrder(outputOrder, savedToRuntime));

        // Ensure every component is present even if not saved previously
        for (var comp : circuit.getComponents()) {
            if (canvas.getFigure(comp.getId()) == null) {
                canvas.upsertComponent(comp, new Point(autoX, autoY));
                autoX += 60;
            }
        }

        // Populate connector figures so wires show immediately after loading
        for (var connector : circuit.getConnectors()) {
            canvas.setConnector(connector);
            canvas.clearInputState(connector.getSinkComponentId(), connector.getSinkPortIndex());
        }

        // Initialize output visuals (e.g., input switches showing red/green state)
        for (var component : circuit.getComponents()) {
            if (component.getOutputs().isEmpty()) continue;
            Map<Integer, ModelContracts.Signal> outputState = new LinkedHashMap<>();
            for (var port : component.getOutputs()) {
                outputState.put(port.index(), component.getOutputValue(port.index()));
            }
            canvas.updateOutputs(component, outputState);
        }
    }

    private List<ModelContracts.ComponentId> resolveOrder(List<String> savedOrder,
                                                          Map<String, ModelContracts.ComponentId> savedToRuntime) {
        List<ModelContracts.ComponentId> list = new ArrayList<>();
        for (String saved : savedOrder) {
            ModelContracts.ComponentId id = savedToRuntime.get(saved);
            if (id != null) list.add(id);
        }
        return list;
    }

    private void autoDistribute(ModelContracts.Circuit circuit, CanvasModel canvas) {
        int x = 40;
        int y = 40;
        for (var comp : circuit.getComponents()) {
            canvas.upsertComponent(comp, new Point(x, y));
            x += 120;
            if (x > 600) {
                x = 40;
                y += 100;
            }
        }
        canvas.refreshPortOrdering(circuit.getInputComponentIds(), circuit.getOutputComponentIds());
    }

    private int parseInt(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    /**
     * UI-layer load result bundling the business project with concrete
     * {@link CanvasModel} layouts.
     */
    public record LoadResult(ModelContracts.Project project,
                             Map<String, CanvasModel> layouts) { }
}