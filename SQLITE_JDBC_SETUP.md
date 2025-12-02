# SQLite JDBC Driver Integration Guide

## ✅ CRUD Operations Support

Yes! The `CircuitRepository` class supports full **CRUD operations**:

### **CREATE** Operations:
- `saveCircuit()` - Save/update circuit diagrams
- `saveSimulationResults()` - Save simulation output values
- `saveTruthTable()` - Save truth table data
- `saveBooleanExpressions()` - Save boolean expressions

### **READ** Operations:
- `loadCircuit()` - Load circuit by name
- `loadSimulationResults()` - Load simulation results
- `loadTruthTable()` - Load truth table
- `loadBooleanExpressions()` - Load boolean expressions
- `listCircuits()` - List all circuit names

### **UPDATE** Operations:
- `saveCircuit()` - Updates existing circuit if it already exists
- `saveSimulationResults()` - Replaces existing results
- `saveTruthTable()` - Replaces existing truth table
- `saveBooleanExpressions()` - Replaces existing expressions

### **DELETE** Operations:
- `deleteCircuit()` - Deletes circuit and all associated data (cascade delete)

---

## 📦 How to Add SQLite JDBC Driver (You've Downloaded It)

### **Option 1: Using IntelliJ IDEA / Cursor (Recommended)**

1. **Locate your downloaded JAR file**
   - You should have a file like `sqlite-jdbc-3.44.1.0.jar` (or similar version)
   - Note the full path to this file

2. **Add to Project Libraries in IntelliJ/Cursor:**
   
   **For IntelliJ IDEA:**
   - Right-click on your project root (`F:\LogiSim-sim`)
   - Select **"Open Module Settings"** (or press `F4`)
   - Go to **"Libraries"** in the left sidebar
   - Click the **"+"** button → **"Java"**
   - Navigate to and select your downloaded `sqlite-jdbc-*.jar` file
   - Click **"OK"** → **"Apply"** → **"OK"**

   **For Cursor (VS Code-based):**
   - Create a `lib` folder in your project root: `F:\LogiSim-sim\lib`
   - Copy your `sqlite-jdbc-*.jar` file into the `lib` folder
   - Open `.vscode/settings.json` (or create it)
   - Add this configuration:
     ```json
     {
       "java.project.referencedLibraries": [
         "lib/**/*.jar"
       ]
     }
     ```
   - Reload the window: `Ctrl+Shift+P` → "Developer: Reload Window"

3. **Verify Installation:**
   - Try to compile your project
   - If you see no errors about `java.sql.*` or `DriverManager`, you're good!

### **Option 2: Manual Classpath (Command Line)**

If you're compiling from command line:

```bash
# Windows
javac -cp ".;lib/sqlite-jdbc-3.44.1.0.jar" src/main/java/org/logisim/**/*.java

# Linux/Mac
javac -cp ".:lib/sqlite-jdbc-3.44.1.0.jar" src/main/java/org/logisim/**/*.java
```

### **Option 3: Create lib Folder Structure**

1. Create folder: `F:\LogiSim-sim\lib`
2. Copy your `sqlite-jdbc-*.jar` into `lib/`
3. Update your IDE settings to include `lib/**/*.jar` in classpath

---

## 🔍 Verification Steps

After adding the JDBC driver:

1. **Check for compilation errors:**
   - Open `CircuitRepository.java`
   - Look for any red underlines on `import java.sql.*;`
   - If no errors, the driver is properly loaded

2. **Test the database:**
   - Run your application
   - Create a circuit
   - Check if `logisim_circuits.db` file appears in your project root
   - If yes, SQLite is working!

3. **Check database file:**
   - You can open `logisim_circuits.db` with any SQLite browser (like DB Browser for SQLite)
   - You should see 4 tables: `circuits`, `simulation_results`, `truth_table_rows`, `boolean_expressions`

---

## 📝 Integration Summary

The integration is **already complete** in `LogiSimFrame.java`! Here's what was added:

### ✅ **CircuitRepository Instance:**
```java
private final CircuitRepository circuitRepo = new CircuitRepository();
```

### ✅ **Auto-Save on Circuit Creation:**
- When default circuit is created (constructor)
- When new circuit is created (`promptNewCircuit()`)

### ✅ **Auto-Save on Circuit Modification:**
- When component is added (`onComponentAdded()`)
- When component is removed (`onComponentRemoved()`)
- When connector is added (`onConnectorAdded()`)
- When connector is removed (`onConnectorRemoved()`)
- When component is placed (`requestComponentPlacement()`)
- When connector is created (`requestConnectorCreation()`)
- When component is deleted (`deleteSelectedComponent()`)

### ✅ **Auto-Save on Simulation:**
- After simulation completes (`SimulationWorker.done()`)

### ✅ **Auto-Save on Analysis:**
- After truth table generation (`showTruthTable()`)
- After boolean expression generation (`showExpressions()`)

---

## 🚀 You're All Set!

Once you add the SQLite JDBC driver JAR to your project's classpath, everything will work automatically. The database file `logisim_circuits.db` will be created in your project root directory, and all circuit data will be saved automatically as you work.

---

## ❓ Troubleshooting

**Problem:** `ClassNotFoundException: org.sqlite.JDBC`
- **Solution:** The JDBC driver JAR is not in your classpath. Follow Option 1 above.

**Problem:** `No suitable driver found for jdbc:sqlite`
- **Solution:** Make sure the JAR file is actually added to your project libraries.

**Problem:** Database file not created
- **Solution:** Check that you have write permissions in the project directory. The file is created on first use.

**Problem:** Compilation errors in `CircuitRepository.java`
- **Solution:** Make sure `SimpleJson.java` has the new methods `readObjectFromString()` and `parseFromString()` (they should be there already).

