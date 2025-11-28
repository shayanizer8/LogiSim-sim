package org.logisim.ui.view;

import org.logisim.business.ModelContracts;
import org.logisim.business.TruthTableGenerator;

import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Modal dialog that displays a generated truth table.
 */
public class TruthTableDialog extends JDialog {

    public TruthTableDialog(Frame owner,
                            List<TruthTableGenerator.TruthRow> rows,
                            List<ModelContracts.ComponentId> inputOrder,
                            List<ModelContracts.ComponentId> outputOrder,
                            Function<ModelContracts.ComponentId, String> labelProvider) {
        super(owner, "Truth Table", true);
        setLayout(new BorderLayout());

        DefaultTableModel model = new DefaultTableModel();
        for (ModelContracts.ComponentId id : inputOrder) {
            model.addColumn("In " + labelProvider.apply(id));
        }
        for (ModelContracts.ComponentId id : outputOrder) {
            model.addColumn("Out " + labelProvider.apply(id));
        }

        for (TruthTableGenerator.TruthRow row : rows) {
            Object[] data = new Object[inputOrder.size() + outputOrder.size()];
            int idx = 0;
            for (ModelContracts.ComponentId id : inputOrder) {
                data[idx++] = signalToBit(row.inputs().get(id));
            }
            for (ModelContracts.ComponentId id : outputOrder) {
                data[idx++] = signalToBit(row.outputs().get(id));
            }
            model.addRow(data);
        }

        JTable table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);
        setSize(600, 400);
        setLocationRelativeTo(owner);
    }

    private Object signalToBit(ModelContracts.Signal signal) {
        if (signal == null) return "U";
        return switch (signal) {
            case HIGH -> 1;
            case LOW -> 0;
            default -> "U";
        };
    }
}

