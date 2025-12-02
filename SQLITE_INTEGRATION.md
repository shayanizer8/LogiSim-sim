# SQLite Integration Guide

## Overview
The `CircuitRepository` class in `org.logisim.data` provides SQLite-based persistence for:
- Circuit diagrams (components and connectors)
- Simulation results (output values)
- Truth tables
- Boolean expressions

## Step 1: Add SQLite JDBC Driver

### Option A: Using Maven (if you add pom.xml)
Add to `pom.xml`:
```xml
<dependencies>
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
        <version>3.44.1.0</version>
    </dependency>
</dependencies>
```

### Option B: Manual Download (for IntelliJ/Cursor projects)
1. Download SQLite JDBC driver from: https://github.com/xerial/sqlite-jdbc/releases
2. Download `sqlite-jdbc-3.44.1.0.jar` (or latest version)
3. In IntelliJ/Cursor:
   - Right-click your project → "Open Module Settings" (or F4)
   - Go to "Libraries"
   - Click "+" → "Java"
   - Select the downloaded JAR file
   - Click "OK"

### Option C: Using Gradle (if you add build.gradle)
Add to `build.gradle`:
```gradle
dependencies {
    implementation 'org.xerial:sqlite-jdbc:3.44.1.0'
}
```

## Step 2: Integration Points in LogiSimFrame

The following methods in `LogiSimFrame.java` should be updated to save data to SQLite:

### 2.1 After Circuit Creation/Modification
Save the circuit whenever it's created or modified:
```java
private CircuitRepository circuitRepo = new CircuitRepository();

// After creating/updating a circuit:
circuitRepo.saveCircuit(activeCircuit, project.getName());
```

### 2.2 After Simulation
Save simulation results after running:
```java
// In runSimulation() method, after simulation completes:
Map<ComponentId, Map<Integer, Signal>> outputs = engine.collectOutputs(activeCircuit);
circuitRepo.saveSimulationResults(activeCircuit.getName(), outputs);
```

### 2.3 After Truth Table Generation
Save truth table when generated:
```java
// In showTruthTable() method, after generating:
circuitRepo.saveTruthTable(activeCircuit.getName(), rows);
```

### 2.4 After Boolean Expression Generation
Save expressions when generated:
```java
// In showExpressions() method, after generating:
circuitRepo.saveBooleanExpressions(activeCircuit.getName(), expressions);
```

## Step 3: Loading Data from SQLite

You can add menu items or buttons to:
- Load circuits from database
- View historical simulation results
- View saved truth tables
- View saved boolean expressions

Example:
```java
// Load a circuit from database
ModelContracts.Circuit loaded = circuitRepo.loadCircuit("MyCircuit");

// Load simulation results
Map<ComponentId, Map<Integer, Signal>> results = 
    circuitRepo.loadSimulationResults("MyCircuit");

// Load truth table
List<TruthTableGenerator.TruthRow> table = 
    circuitRepo.loadTruthTable("MyCircuit");

// Load boolean expressions
Map<ComponentId, String> expressions = 
    circuitRepo.loadBooleanExpressions("MyCircuit");
```

## Database Schema

The database file `logisim_circuits.db` will be created in your project root with these tables:

1. **circuits**: Stores circuit diagrams (JSON format)
2. **simulation_results**: Stores output values after simulation
3. **truth_table_rows**: Stores truth table data
4. **boolean_expressions**: Stores boolean expressions per output

All tables are linked via `circuit_id` with CASCADE DELETE.

## Usage Example

```java
// Initialize repository
CircuitRepository repo = new CircuitRepository();

// Save a circuit
int circuitId = repo.saveCircuit(myCircuit, "MyProject");

// Save simulation results
repo.saveSimulationResults("MyCircuit", outputMap);

// Save truth table
repo.saveTruthTable("MyCircuit", truthTableRows);

// Save boolean expressions
repo.saveBooleanExpressions("MyCircuit", expressionMap);

// Load everything back
ModelContracts.Circuit loaded = repo.loadCircuit("MyCircuit");
Map<ComponentId, Map<Integer, Signal>> results = repo.loadSimulationResults("MyCircuit");
List<TruthTableGenerator.TruthRow> table = repo.loadTruthTable("MyCircuit");
Map<ComponentId, String> expressions = repo.loadBooleanExpressions("MyCircuit");
```

## Notes

- The database file is created automatically on first use
- Circuits are stored as JSON (using `PersistenceUtil.circuitToMap`)
- All data is linked to circuit names (unique per circuit)
- Old data is replaced when saving (no versioning by default)

