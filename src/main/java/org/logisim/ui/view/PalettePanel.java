package org.logisim.ui.view;

import org.logisim.business.ModelContracts;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import java.awt.GridLayout;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Component palette shown on the left side of the UI. Houses tool selection,
 * component buttons and wire color picker.
 */
public class PalettePanel extends JPanel {
    public interface Listener {
        void onWireTool(boolean enabled);
        void onComponentRequested(String label, Supplier<ModelContracts.Component> factory);
        void onModuleRequested();
        void onWireColorChanged(String color);
    }

    private final Listener listener;
    private final JComboBox<String> colorCombo;
    private final JToggleButton wireToggle;

    public PalettePanel(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
        setBorder(new TitledBorder("Palette"));
        setLayout(new GridLayout(0, 1, 4, 4));

        wireToggle = new JToggleButton("Wire");
        wireToggle.addActionListener(e -> {
            listener.onWireTool(wireToggle.isSelected());
        });

        add(wireToggle);
        add(componentButton("Input Switch", () -> new org.logisim.business.InputPin()));
        add(componentButton("Output Lamp", () -> new org.logisim.business.OutputPin()));
        add(componentButton("AND Gate", () -> new org.logisim.business.gates.AndGate()));
        add(componentButton("OR Gate", () -> new org.logisim.business.gates.OrGate()));
         add(componentButton("NOT Gate", () -> new org.logisim.business.gates.NotGate()));
        add(componentButton("NAND Gate", () -> new org.logisim.business.gates.NandGate()));
        add(componentButton("NOR Gate", () -> new org.logisim.business.gates.NorGate()));
        add(componentButton("XOR Gate", () -> new org.logisim.business.gates.XorGate()));



        JButton moduleBtn = new JButton("Module...");
        moduleBtn.addActionListener(e -> listener.onModuleRequested());
        add(moduleBtn);

        add(new JLabel("Wire color", SwingConstants.CENTER));
        colorCombo = new JComboBox<>(new String[]{"black", "red", "green", "blue", "orange", "gray"});
        colorCombo.addActionListener(e -> listener.onWireColorChanged((String) colorCombo.getSelectedItem()));
        colorCombo.setSelectedItem("black");
        add(colorCombo);
    }

    private JButton componentButton(String text, Supplier<ModelContracts.Component> factory) {
        JButton button = new JButton(text);
        button.addActionListener(e -> {
            wireToggle.setSelected(false);
            listener.onWireTool(false);
            listener.onComponentRequested(text, factory);
        });
        return button;
    }

    public void clearWireSelection() {
        wireToggle.setSelected(false);
    }
}

