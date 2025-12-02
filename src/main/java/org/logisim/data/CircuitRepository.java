package org.logisim.data;

import org.logisim.business.ModelContracts;
import org.logisim.business.PersistenceUtil;
import org.logisim.business.TruthTableGenerator;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * SQLite-based repository for storing and retrieving circuit diagrams,
 * simulation results, truth tables, and boolean expressions.
 */
public class CircuitRepository {
    private static final String DB_FILE = "logisim_circuits.db";
    private final String dbPath;

    public CircuitRepository() {
        this(DB_FILE);
    }

    public CircuitRepository(String dbPath) {
        this.dbPath = dbPath;
        initializeDatabase();
    }

    /**
     * Initialize the database schema with all required tables.
     */
    private void initializeDatabase() {
        String createCircuitsTable = """
            CREATE TABLE IF NOT EXISTS circuits (
                id INTEGER PRIMARY KEY,
                circuit_name TEXT NOT NULL UNIQUE,
                project_name TEXT,
                circuit_data TEXT NOT NULL,
                layout_data TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """;

        String createSimulationResultsTable = """
            CREATE TABLE IF NOT EXISTS simulation_results (
                id INTEGER PRIMARY KEY,
                circuit_id INTEGER NOT NULL,
                output_component_id TEXT NOT NULL,
                output_port_index INTEGER NOT NULL,
                signal_value TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                FOREIGN KEY (circuit_id) REFERENCES circuits(id) ON DELETE CASCADE,
                UNIQUE(circuit_id, output_component_id, output_port_index, timestamp)
            )
            """;

        String createTruthTableRowsTable = """
            CREATE TABLE IF NOT EXISTS truth_table_rows (
                id INTEGER PRIMARY KEY,
                circuit_id INTEGER NOT NULL,
                row_index INTEGER NOT NULL,
                input_data TEXT NOT NULL,
                output_data TEXT NOT NULL,
                FOREIGN KEY (circuit_id) REFERENCES circuits(id) ON DELETE CASCADE,
                UNIQUE(circuit_id, row_index)
            )
            """;

        String createBooleanExpressionsTable = """
            CREATE TABLE IF NOT EXISTS boolean_expressions (
                id INTEGER PRIMARY KEY,
                circuit_id INTEGER NOT NULL,
                output_component_id TEXT NOT NULL,
                expression TEXT NOT NULL,
                FOREIGN KEY (circuit_id) REFERENCES circuits(id) ON DELETE CASCADE,
                UNIQUE(circuit_id, output_component_id)
            )
            """;

        try (Connection conn = getConnection()) {
            conn.createStatement().execute(createCircuitsTable);
            conn.createStatement().execute(createSimulationResultsTable);
            conn.createStatement().execute(createTruthTableRowsTable);
            conn.createStatement().execute(createBooleanExpressionsTable);
            
            // Add layout_data column if it doesn't exist (migration for existing databases)
            try {
                conn.createStatement().execute("ALTER TABLE circuits ADD COLUMN layout_data TEXT");
            } catch (SQLException e) {
                // Column already exists, ignore
            }
            
            // Drop sqlite_sequence table if it exists (it's auto-created by SQLite for AUTOINCREMENT)
            // We removed AUTOINCREMENT to prevent this table, but drop it if it exists from old databases
            try {
                conn.createStatement().execute("DROP TABLE IF EXISTS sqlite_sequence");
            } catch (SQLException e) {
                // Ignore if it doesn't exist or can't be dropped
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        // Drop sqlite_sequence table if it exists (from old databases that used AUTOINCREMENT)
        // We now use INTEGER PRIMARY KEY instead of AUTOINCREMENT to prevent this table
        try {
            conn.createStatement().execute("DROP TABLE IF EXISTS sqlite_sequence");
        } catch (SQLException e) {
            // Ignore if it doesn't exist or can't be dropped
        }
        return conn;
    }

    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Save a circuit diagram to the database.
     * If a circuit with the same name exists, it will be updated.
     *
     * @param circuit The circuit to save
     * @param projectName Optional project name
     * @param layoutData Optional layout data (component positions) as JSON string, or null
     * @return The database ID of the saved circuit
     */
    public int saveCircuit(ModelContracts.Circuit circuit, String projectName, String layoutData) {
        Objects.requireNonNull(circuit, "circuit");
        String circuitData = SimpleJson.stringify(PersistenceUtil.circuitToMap(circuit));
        String timestamp = getCurrentTimestamp();

        String selectSql = "SELECT id FROM circuits WHERE circuit_name = ?";
        String insertSql = """
            INSERT INTO circuits (circuit_name, project_name, circuit_data, layout_data, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        String updateSql = """
            UPDATE circuits SET circuit_data = ?, layout_data = ?, updated_at = ?, project_name = ?
            WHERE circuit_name = ?
            """;

        try (Connection conn = getConnection()) {
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setString(1, circuit.getName());
                ResultSet rs = selectStmt.executeQuery();

                if (rs.next()) {
                    // Update existing circuit
                    int circuitId = rs.getInt("id");
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setString(1, circuitData);
                        updateStmt.setString(2, layoutData);
                        updateStmt.setString(3, timestamp);
                        updateStmt.setString(4, projectName);
                        updateStmt.setString(5, circuit.getName());
                        updateStmt.executeUpdate();
                    }
                    return circuitId;
                } else {
                    // Insert new circuit
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                        insertStmt.setString(1, circuit.getName());
                        insertStmt.setString(2, projectName);
                        insertStmt.setString(3, circuitData);
                        insertStmt.setString(4, layoutData);
                        insertStmt.setString(5, timestamp);
                        insertStmt.setString(6, timestamp);
                        insertStmt.executeUpdate();

                        ResultSet generatedKeys = insertStmt.getGeneratedKeys();
                        if (generatedKeys.next()) {
                            return generatedKeys.getInt(1);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save circuit", e);
        }
        throw new RuntimeException("Failed to save circuit: no ID generated");
    }
    
    /**
     * Save a circuit without layout data (backward compatibility).
     */
    public int saveCircuit(ModelContracts.Circuit circuit, String projectName) {
        return saveCircuit(circuit, projectName, null);
    }

    /**
     * Get circuit ID by name, or -1 if not found.
     */
    private int getCircuitId(String circuitName) {
        String sql = "SELECT id FROM circuits WHERE circuit_name = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, circuitName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get circuit ID", e);
        }
        return -1;
    }

    /**
     * Save simulation results for a circuit (only output component results).
     * This replaces any existing results for the same circuit.
     *
     * @param circuitName The name of the circuit
     * @param outputs Map of ComponentId -> Map of port index -> Signal value (should only contain output components)
     */
    public void saveSimulationResults(String circuitName, Map<ModelContracts.ComponentId, Map<Integer, ModelContracts.Signal>> outputs) {
        int circuitId = getCircuitId(circuitName);
        if (circuitId == -1) {
            throw new IllegalArgumentException("Circuit not found: " + circuitName);
        }

        String deleteSql = "DELETE FROM simulation_results WHERE circuit_id = ?";
        String insertSql = """
            INSERT INTO simulation_results (circuit_id, output_component_id, output_port_index, signal_value, timestamp)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection conn = getConnection()) {
            // Delete existing results
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setInt(1, circuitId);
                deleteStmt.executeUpdate();
            }

            // Insert new results
            String timestamp = getCurrentTimestamp();
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                for (var entry : outputs.entrySet()) {
                    String componentId = entry.getKey().id();
                    for (var portEntry : entry.getValue().entrySet()) {
                        insertStmt.setInt(1, circuitId);
                        insertStmt.setString(2, componentId);
                        insertStmt.setInt(3, portEntry.getKey());
                        insertStmt.setString(4, portEntry.getValue().name());
                        insertStmt.setString(5, timestamp);
                        insertStmt.addBatch();
                    }
                }
                insertStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save simulation results", e);
        }
    }

    /**
     * Save truth table for a circuit.
     * This replaces any existing truth table for the same circuit.
     *
     * @param circuitName The name of the circuit
     * @param truthTable List of truth table rows
     */
    public void saveTruthTable(String circuitName, List<TruthTableGenerator.TruthRow> truthTable) {
        int circuitId = getCircuitId(circuitName);
        if (circuitId == -1) {
            throw new IllegalArgumentException("Circuit not found: " + circuitName);
        }

        String deleteSql = "DELETE FROM truth_table_rows WHERE circuit_id = ?";
        String insertSql = """
            INSERT INTO truth_table_rows (circuit_id, row_index, input_data, output_data)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection conn = getConnection()) {
            // Delete existing truth table
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setInt(1, circuitId);
                deleteStmt.executeUpdate();
            }

            // Insert new truth table rows
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                for (int i = 0; i < truthTable.size(); i++) {
                    TruthTableGenerator.TruthRow row = truthTable.get(i);
                    Map<String, String> inputMap = new LinkedHashMap<>();
                    for (var entry : row.inputs().entrySet()) {
                        inputMap.put(entry.getKey().id(), entry.getValue().name());
                    }
                    Map<String, String> outputMap = new LinkedHashMap<>();
                    for (var entry : row.outputs().entrySet()) {
                        outputMap.put(entry.getKey().id(), entry.getValue().name());
                    }

                    insertStmt.setInt(1, circuitId);
                    insertStmt.setInt(2, i);
                    insertStmt.setString(3, SimpleJson.stringify(inputMap));
                    insertStmt.setString(4, SimpleJson.stringify(outputMap));
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save truth table", e);
        }
    }

    /**
     * Save boolean expressions for a circuit.
     * This replaces any existing expressions for the same circuit.
     *
     * @param circuitName The name of the circuit
     * @param expressions Map of output ComponentId -> expression string
     */
    public void saveBooleanExpressions(String circuitName, Map<ModelContracts.ComponentId, String> expressions) {
        int circuitId = getCircuitId(circuitName);
        if (circuitId == -1) {
            throw new IllegalArgumentException("Circuit not found: " + circuitName);
        }

        String deleteSql = "DELETE FROM boolean_expressions WHERE circuit_id = ?";
        String insertSql = """
            INSERT INTO boolean_expressions (circuit_id, output_component_id, expression)
            VALUES (?, ?, ?)
            """;

        try (Connection conn = getConnection()) {
            // Delete existing expressions
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setInt(1, circuitId);
                deleteStmt.executeUpdate();
            }

            // Insert new expressions
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                for (var entry : expressions.entrySet()) {
                    insertStmt.setInt(1, circuitId);
                    insertStmt.setString(2, entry.getKey().id());
                    insertStmt.setString(3, entry.getValue());
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save boolean expressions", e);
        }
    }

    /**
     * Load a circuit from the database by name.
     *
     * @param circuitName The name of the circuit
     * @return The loaded circuit, or null if not found
     */
    public ModelContracts.Circuit loadCircuit(String circuitName) {
        String sql = "SELECT circuit_data FROM circuits WHERE circuit_name = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, circuitName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String circuitDataJson = rs.getString("circuit_data");
                Map<String, Object> circuitMap = SimpleJson.readObjectFromString(circuitDataJson);
                Map<String, ModelContracts.ComponentId> savedToRuntime = new LinkedHashMap<>();
                return PersistenceUtil.mapToCircuit(circuitMap, savedToRuntime);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load circuit", e);
        }
        return null;
    }
    
    /**
     * Load a circuit with component ID mapping for truth table/expression resolution.
     *
     * @param circuitName The name of the circuit
     * @return A record containing the circuit, layout data, and the saved-to-runtime ID mapping
     */
    public CircuitLoadResult loadCircuitWithMapping(String circuitName) {
        String sql = "SELECT circuit_data, layout_data FROM circuits WHERE circuit_name = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, circuitName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String circuitDataJson = rs.getString("circuit_data");
                String layoutDataJson = rs.getString("layout_data");
                Map<String, Object> circuitMap = SimpleJson.readObjectFromString(circuitDataJson);
                Map<String, ModelContracts.ComponentId> savedToRuntime = new LinkedHashMap<>();
                ModelContracts.Circuit circuit = PersistenceUtil.mapToCircuit(circuitMap, savedToRuntime);
                return new CircuitLoadResult(circuit, layoutDataJson, savedToRuntime);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load circuit", e);
        }
        return null;
    }
    
    /**
     * Result of loading a circuit with its layout data and ID mapping.
     */
    public record CircuitLoadResult(ModelContracts.Circuit circuit,
                                    String layoutData,
                                    Map<String, ModelContracts.ComponentId> savedToRuntimeIds) { }

    /**
     * Load simulation results for a circuit.
     *
     * @param circuitName The name of the circuit
     * @return Map of ComponentId -> Map of port index -> Signal value, or empty map if not found
     */
    public Map<ModelContracts.ComponentId, Map<Integer, ModelContracts.Signal>> loadSimulationResults(String circuitName) {
        int circuitId = getCircuitId(circuitName);
        if (circuitId == -1) {
            return Map.of();
        }

        String sql = """
            SELECT output_component_id, output_port_index, signal_value
            FROM simulation_results
            WHERE circuit_id = ?
            ORDER BY timestamp DESC
            """;

        Map<ModelContracts.ComponentId, Map<Integer, ModelContracts.Signal>> results = new LinkedHashMap<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, circuitId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String componentIdStr = rs.getString("output_component_id");
                int portIndex = rs.getInt("output_port_index");
                String signalStr = rs.getString("signal_value");

                ModelContracts.ComponentId componentId = new ModelContracts.ComponentId(componentIdStr);
                ModelContracts.Signal signal = ModelContracts.Signal.valueOf(signalStr);

                results.computeIfAbsent(componentId, k -> new LinkedHashMap<>())
                       .put(portIndex, signal);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load simulation results", e);
        }
        return results;
    }

    /**
     * Load truth table for a circuit.
     * Uses the provided saved-to-runtime ID mapping to resolve component IDs correctly.
     *
     * @param circuitName The name of the circuit
     * @param savedToRuntimeIds Map from saved component ID string -> runtime ComponentId
     * @return List of truth table rows, or empty list if not found
     */
    public List<TruthTableGenerator.TruthRow> loadTruthTable(String circuitName, 
                                                              Map<String, ModelContracts.ComponentId> savedToRuntimeIds) {
        int circuitId = getCircuitId(circuitName);
        if (circuitId == -1) {
            return List.of();
        }

        String sql = """
            SELECT input_data, output_data
            FROM truth_table_rows
            WHERE circuit_id = ?
            ORDER BY row_index
            """;

        List<TruthTableGenerator.TruthRow> rows = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, circuitId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String inputJson = rs.getString("input_data");
                String outputJson = rs.getString("output_data");

                @SuppressWarnings("unchecked")
                Map<String, String> inputMap = (Map<String, String>) SimpleJson.parseFromString(inputJson);
                @SuppressWarnings("unchecked")
                Map<String, String> outputMap = (Map<String, String>) SimpleJson.parseFromString(outputJson);

                Map<ModelContracts.ComponentId, ModelContracts.Signal> inputs = new LinkedHashMap<>();
                for (var entry : inputMap.entrySet()) {
                    // Map saved ID to runtime ComponentId
                    ModelContracts.ComponentId runtimeId = savedToRuntimeIds.get(entry.getKey());
                    if (runtimeId != null) {
                        inputs.put(runtimeId, ModelContracts.Signal.valueOf(entry.getValue()));
                    }
                }

                Map<ModelContracts.ComponentId, ModelContracts.Signal> outputs = new LinkedHashMap<>();
                for (var entry : outputMap.entrySet()) {
                    // Map saved ID to runtime ComponentId
                    ModelContracts.ComponentId runtimeId = savedToRuntimeIds.get(entry.getKey());
                    if (runtimeId != null) {
                        outputs.put(runtimeId, ModelContracts.Signal.valueOf(entry.getValue()));
                    }
                }

                rows.add(new TruthTableGenerator.TruthRow(inputs, outputs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load truth table", e);
        }
        return rows;
    }
    
    /**
     * Load truth table for a circuit (backward compatibility - uses new ComponentIds which may not match).
     *
     * @param circuitName The name of the circuit
     * @return List of truth table rows, or empty list if not found
     */
    public List<TruthTableGenerator.TruthRow> loadTruthTable(String circuitName) {
        // Load circuit to get ID mapping
        CircuitLoadResult loadResult = loadCircuitWithMapping(circuitName);
        if (loadResult == null) {
            return List.of();
        }
        return loadTruthTable(circuitName, loadResult.savedToRuntimeIds());
    }

    /**
     * Load boolean expressions for a circuit.
     * Uses the provided saved-to-runtime ID mapping to resolve component IDs correctly.
     *
     * @param circuitName The name of the circuit
     * @param savedToRuntimeIds Map from saved component ID string -> runtime ComponentId
     * @return Map of output ComponentId -> expression string, or empty map if not found
     */
    public Map<ModelContracts.ComponentId, String> loadBooleanExpressions(String circuitName,
                                                                              Map<String, ModelContracts.ComponentId> savedToRuntimeIds) {
        int circuitId = getCircuitId(circuitName);
        if (circuitId == -1) {
            return Map.of();
        }

        String sql = """
            SELECT output_component_id, expression
            FROM boolean_expressions
            WHERE circuit_id = ?
            """;

        Map<ModelContracts.ComponentId, String> expressions = new LinkedHashMap<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, circuitId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String savedComponentIdStr = rs.getString("output_component_id");
                String expression = rs.getString("expression");
                // Map saved ID to runtime ComponentId
                ModelContracts.ComponentId runtimeId = savedToRuntimeIds.get(savedComponentIdStr);
                if (runtimeId != null) {
                    expressions.put(runtimeId, expression);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load boolean expressions", e);
        }
        return expressions;
    }
    
    /**
     * Load boolean expressions for a circuit (backward compatibility - uses new ComponentIds which may not match).
     *
     * @param circuitName The name of the circuit
     * @return Map of output ComponentId -> expression string, or empty map if not found
     */
    public Map<ModelContracts.ComponentId, String> loadBooleanExpressions(String circuitName) {
        // Load circuit to get ID mapping
        CircuitLoadResult loadResult = loadCircuitWithMapping(circuitName);
        if (loadResult == null) {
            return Map.of();
        }
        return loadBooleanExpressions(circuitName, loadResult.savedToRuntimeIds());
    }

    /**
     * Check if simulation results exist for a circuit.
     *
     * @param circuitName The name of the circuit
     * @return true if simulation results exist, false otherwise
     */
    public boolean hasSimulationResults(String circuitName) {
        int circuitId = getCircuitId(circuitName);
        if (circuitId == -1) return false;
        
        String sql = "SELECT COUNT(*) as count FROM simulation_results WHERE circuit_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, circuitId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count") > 0;
            }
        } catch (SQLException e) {
            return false;
        }
        return false;
    }

    /**
     * Check if truth table exists for a circuit.
     *
     * @param circuitName The name of the circuit
     * @return true if truth table exists, false otherwise
     */
    public boolean hasTruthTable(String circuitName) {
        int circuitId = getCircuitId(circuitName);
        if (circuitId == -1) return false;
        
        String sql = "SELECT COUNT(*) as count FROM truth_table_rows WHERE circuit_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, circuitId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count") > 0;
            }
        } catch (SQLException e) {
            return false;
        }
        return false;
    }

    /**
     * Check if boolean expressions exist for a circuit.
     *
     * @param circuitName The name of the circuit
     * @return true if boolean expressions exist, false otherwise
     */
    public boolean hasBooleanExpressions(String circuitName) {
        int circuitId = getCircuitId(circuitName);
        if (circuitId == -1) return false;
        
        String sql = "SELECT COUNT(*) as count FROM boolean_expressions WHERE circuit_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, circuitId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count") > 0;
            }
        } catch (SQLException e) {
            return false;
        }
        return false;
    }

    /**
     * List all circuit names in the database.
     *
     * @return List of circuit names
     */
    public List<String> listCircuits() {
        String sql = "SELECT circuit_name FROM circuits ORDER BY circuit_name";
        List<String> names = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString("circuit_name"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list circuits", e);
        }
        return names;
    }

    /**
     * Delete a circuit and all associated data (simulation results, truth table, expressions).
     *
     * @param circuitName The name of the circuit to delete
     */
    public void deleteCircuit(String circuitName) {
        int circuitId = getCircuitId(circuitName);
        if (circuitId == -1) {
            return; // Circuit doesn't exist
        }

        String sql = "DELETE FROM circuits WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, circuitId);
            stmt.executeUpdate();
            // Cascade delete will handle related records
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete circuit", e);
        }
    }
}

