package org.logisim.ui.persistence;

import org.logisim.business.ModelContracts;
import org.logisim.business.PersistenceUtil;
import org.logisim.business.ProjectImpl;
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
 * Coordinates saving/loading of a project (business layer model + UI layout).
 */
public class ProjectPersistenceService {

    public void save(ModelContracts.Project project, Map<String, CanvasModel> layouts, Path path) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(path, "path");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("projectName", project.getName());
        List<Map<String, Object>> circuits = new ArrayList<>();
        for (ModelContracts.Circuit circuit : project.listCircuits()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("model", PersistenceUtil.circuitToMap(circuit));
            CanvasModel canvas = layouts.get(circuit.getName());
            entry.put("layout", serializeLayout(canvas));
            circuits.add(entry);
        }
        root.put("circuits", circuits);
        SimpleJson.writeToFile(path, root);
    }

    public LoadResult load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Map<String, Object> root = SimpleJson.readObject(path);
        String projectName = root.getOrDefault("projectName", "Loaded Project").toString();
        ProjectImpl project = new ProjectImpl(projectName);
        Map<String, CanvasModel> layouts = new LinkedHashMap<>();
        Object circuitsObj = root.get("circuits");
        if (circuitsObj instanceof List<?> list) {
            for (Object entryObj : list) {
                if (!(entryObj instanceof Map<?, ?> entryMap)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> entry = (Map<String, Object>) entryMap;
                @SuppressWarnings("unchecked")
                Map<String, Object> modelMap = entry.get("model") instanceof Map ? (Map<String, Object>) entry.get("model") : null;
                if (modelMap == null) continue;
                Map<String, ModelContracts.ComponentId> savedToRuntime = new LinkedHashMap<>();
                ModelContracts.Circuit circuit = PersistenceUtil.mapToCircuit(modelMap, savedToRuntime);
                if (circuit == null) continue;
                project.addCircuit(circuit);
                CanvasModel canvas = new CanvasModel();
                Object layoutObj = entry.get("layout");
                applyLayout(layoutObj, circuit, savedToRuntime, canvas);
                layouts.put(circuit.getName(), canvas);
            }
        }
        return new LoadResult(project, layouts);
    }

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

    private void applyLayout(Object layoutObj,
                             ModelContracts.Circuit circuit,
                             Map<String, ModelContracts.ComponentId> savedToRuntime,
                             CanvasModel canvas) {
        if (!(layoutObj instanceof Map<?, ?> layoutMap)) {
            autoDistribute(circuit, canvas);
            return;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comps = layoutMap.get("components") instanceof List ? (List<Map<String, Object>>) layoutMap.get("components") : List.of();
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
        List<String> inputOrder = layoutMap.get("inputOrder") instanceof List ? (List<String>) layoutMap.get("inputOrder") : List.of();
        canvas.setInputOrderDirect(resolveOrder(inputOrder, savedToRuntime));
        @SuppressWarnings("unchecked")
        List<String> outputOrder = layoutMap.get("outputOrder") instanceof List ? (List<String>) layoutMap.get("outputOrder") : List.of();
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

    private List<ModelContracts.ComponentId> resolveOrder(List<String> savedOrder, Map<String, ModelContracts.ComponentId> savedToRuntime) {
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

    public record LoadResult(ModelContracts.Project project, Map<String, CanvasModel> layouts) { }
}

