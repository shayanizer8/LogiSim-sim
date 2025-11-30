package org.logisim.ui.view;

import org.logisim.business.ModelContracts;

import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.util.Map;
import java.util.function.Function;

/**
 * Simple dialog that lists generated boolean expressions per output component.
 */
public class BooleanExpressionDialog extends JDialog {
    public BooleanExpressionDialog(Frame owner,
                                   Map<ModelContracts.ComponentId, String> expressions,
                                   Function<ModelContracts.ComponentId, String> labelProvider) {
        super(owner, "Boolean Expressions", true);
        setLayout(new BorderLayout());
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Output", "Expression"}, 0);
        for (var entry : expressions.entrySet()) {
            model.addRow(new Object[]{labelProvider.apply(entry.getKey()), entry.getValue()});
        }
        JTable table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);
        setSize(500, 300);
        setLocationRelativeTo(owner);
    }
}

