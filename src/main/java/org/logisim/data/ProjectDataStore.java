package org.logisim.data;

import org.logisim.business.ModelContracts;
import org.logisim.business.PersistenceUtil;
import org.logisim.business.ProjectImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data-layer service responsible for saving and loading projects
 * (business model + generic layout metadata) to/from JSON files.
 *
 * The layout is represented as a simple Map structure so that UI
 * layers can interpret it however they like (e.g. CanvasModel).
 */
public final class ProjectDataStore {

    private ProjectDataStore() { }

    /**
     * Save a project and its per-circuit layout maps to the given path.
     *
     * @param project  business-layer project
     * @param layouts  map from circuit name -> layout map (pure data, no UI types)
     * @param path     target file
     */
    public static void save(ModelContracts.Project project,
                            Map<String, Map<String, Object>> layouts,
                            Path path) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(path, "path");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("projectName", project.getName());

        List<Map<String, Object>> circuits = new ArrayList<>();
        for (ModelContracts.Circuit circuit : project.listCircuits()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("model", PersistenceUtil.circuitToMap(circuit));
            Map<String, Object> layout = layouts == null ? null : layouts.get(circuit.getName());
            entry.put("layout", layout == null ? Map.of() : layout);
            circuits.add(entry);
        }
        root.put("circuits", circuits);
        SimpleJson.writeToFile(path, root);
    }

    /**
     * Load a project and its per-circuit layout maps from the given path.
     * The returned layouts are simple maps which the UI layer can translate
     * into concrete view models.
     */
    public static LoadResult load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Map<String, Object> root = SimpleJson.readObject(path);
        String projectName = root.getOrDefault("projectName", "Loaded Project").toString();
        ProjectImpl project = new ProjectImpl(projectName);
        Map<String, Map<String, Object>> layouts = new LinkedHashMap<>();
        // For each circuit we also keep a mapping from saved component id -> runtime ComponentId
        Map<String, Map<String, ModelContracts.ComponentId>> savedToRuntimeByCircuit = new LinkedHashMap<>();

        Object circuitsObj = root.get("circuits");
        if (circuitsObj instanceof List<?> list) {
            for (Object entryObj : list) {
                if (!(entryObj instanceof Map<?, ?> entryMap)) continue;

                @SuppressWarnings("unchecked")
                Map<String, Object> entry = (Map<String, Object>) entryMap;

                @SuppressWarnings("unchecked")
                Map<String, Object> modelMap = entry.get("model") instanceof Map
                        ? (Map<String, Object>) entry.get("model")
                        : null;
                if (modelMap == null) continue;

                Map<String, ModelContracts.ComponentId> savedToRuntime = new LinkedHashMap<>();
                ModelContracts.Circuit circuit = PersistenceUtil.mapToCircuit(modelMap, savedToRuntime);
                if (circuit == null) continue;

                project.addCircuit(circuit);
                // Remember id mapping for this circuit name
                savedToRuntimeByCircuit.put(circuit.getName(), savedToRuntime);

                Object layoutObj = entry.get("layout");
                @SuppressWarnings("unchecked")
                Map<String, Object> layoutMap = layoutObj instanceof Map
                        ? (Map<String, Object>) layoutObj
                        : Map.of();
                layouts.put(circuit.getName(), layoutMap);
            }
        }

        return new LoadResult(project, layouts, savedToRuntimeByCircuit);
    }

    /**
     * Simple DTO bundling the loaded project with per-circuit layout maps
     * and saved-id -> runtime-id mappings used by the UI layer.
     */
    public record LoadResult(ModelContracts.Project project,
                             Map<String, Map<String, Object>> layouts,
                             Map<String, Map<String, ModelContracts.ComponentId>> savedToRuntimeIds) { }
}