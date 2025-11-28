package org.logisim.ui.view;

import org.logisim.business.ModelContracts.ComponentId;
import org.logisim.business.ModelContracts.Signal;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Compact panel that lists output values after simulation.
 */
public class SimulationPanel extends JPanel {
    private final DefaultTableModel outputModel;
    private List<ComponentId> outputOrder = List.of();

    public SimulationPanel() {
        setBorder(new TitledBorder("Outputs"));
        setLayout(new BorderLayout(4, 4));
        setPreferredSize(new Dimension(260, 220));
        outputModel = new DefaultTableModel(new Object[]{"Output", "Value"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable outputTable = new JTable(outputModel);
        add(new JLabel("Outputs"), BorderLayout.NORTH);
        add(new JScrollPane(outputTable), BorderLayout.CENTER);
    }

    public void setOutputOrder(List<ComponentId> order, Function<ComponentId, String> labelProvider) {
        this.outputOrder = List.copyOf(Objects.requireNonNull(order));
        outputModel.setRowCount(0);
        for (ComponentId id : outputOrder) {
            outputModel.addRow(new Object[]{labelProvider.apply(id), Signal.UNDEFINED});
        }
    }

    public void showOutputs(Map<ComponentId, Signal> outputs, Function<ComponentId, String> labelProvider) {
        outputModel.setRowCount(0);
        for (ComponentId id : outputOrder) {
            Signal signal = outputs.getOrDefault(id, Signal.UNDEFINED);
            outputModel.addRow(new Object[]{labelProvider.apply(id), signal});
        }
    }
}

