package org.logisim.business;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple persistence helpers that convert circuits/components/connectors to
 * serializable Maps. This is intentionally simple — Haider can use these maps
 * to produce JSON or XML in the data layer. Restoring from a Map requires
 * knowledge of concrete component types; a simple stub is provided.
 */
public final class PersistenceUtil {
    private PersistenceUtil() { }

    public static Map<String, Object> circuitToMap(ModelContracts.Circuit circuit) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", circuit.getName());
        List<Map<String, Object>> comps = new ArrayList<>();
        for (var comp : circuit.getComponents()) {
            Map<String, Object> sc = new HashMap<>();
            sc.put("id", comp.getId().id());
            sc.put("type", comp.getType());
            sc.put("state", comp.getState());
            comps.add(sc);
        }
        m.put("components", comps);

        List<Map<String, Object>> conns = new ArrayList<>();
        for (var conn : circuit.getConnectors()) {
            Map<String, Object> sc = new HashMap<>();
            sc.put("id", conn.getId());
            sc.put("sourceId", conn.getSourceComponentId().id());
            sc.put("sourcePort", conn.getSourcePortIndex());
            sc.put("sinkId", conn.getSinkComponentId().id());
            sc.put("sinkPort", conn.getSinkPortIndex());
            sc.put("color", conn.getColor());
            conns.add(sc);
        }
        m.put("connectors", conns);
        return m;
    }

    /**
     * Simple stub for reconstructing a circuit from a map. Full reconstruction
     * requires a component factory mapping types to constructors. We leave that
     * to the data layer (Haider) — this method shows the expected shape.
     */
    public static ModelContracts.Circuit mapToCircuit(Map<String, Object> map) {
        if (map == null) return null;
        Object nameObj = map.get("name");
        String name = nameObj == null ? "restored" : nameObj.toString();
        InMemoryCircuit circuit = new InMemoryCircuit(name);

        // First, reconstruct components and remember mapping from saved id -> created component id
        Object compsObj = map.get("components");
        Map<String, ModelContracts.Component> createdBySavedId = new HashMap<>();
        if (compsObj instanceof List) {
            List<?> comps = (List<?>) compsObj;
            for (Object o : comps) {
                if (!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> sc = (Map<String, Object>) o;
                Object sid = sc.get("id");
                String savedId = sid == null ? null : sid.toString();
                String type = sc.get("type") == null ? null : sc.get("type").toString();
                @SuppressWarnings("unchecked")
                Map<String, Object> state = sc.get("state") instanceof Map ? (Map<String, Object>) sc.get("state") : null;

                ModelContracts.Component comp = ComponentFactory.create(type, state);
                if (comp == null) continue;
                circuit.addComponent(comp);
                if (savedId != null) createdBySavedId.put(savedId, comp);
            }
        }

        // Then, reconstruct connectors using the saved component id mapping
        Object connsObj = map.get("connectors");
        if (connsObj instanceof List) {
            List<?> conns = (List<?>) connsObj;
            for (Object o : conns) {
                if (!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> sc = (Map<String, Object>) o;
                String cid = sc.get("id") == null ? java.util.UUID.randomUUID().toString() : sc.get("id").toString();
                String srcSaved = sc.get("sourceId") == null ? null : sc.get("sourceId").toString();
                String sinkSaved = sc.get("sinkId") == null ? null : sc.get("sinkId").toString();
                int srcPort = sc.get("sourcePort") instanceof Number ? ((Number) sc.get("sourcePort")).intValue() : 0;
                int sinkPort = sc.get("sinkPort") instanceof Number ? ((Number) sc.get("sinkPort")).intValue() : 0;
                String color = sc.get("color") == null ? "black" : sc.get("color").toString();

                ModelContracts.Component srcComp = srcSaved == null ? null : createdBySavedId.get(srcSaved);
                ModelContracts.Component sinkComp = sinkSaved == null ? null : createdBySavedId.get(sinkSaved);
                if (srcComp == null || sinkComp == null) continue; // skip connectors referencing unknown components

                BasicConnector conn = new BasicConnector(cid, srcComp.getId(), srcPort, sinkComp.getId(), sinkPort, color);
                circuit.addConnector(conn);
            }
        }

        return circuit;
    }
}
