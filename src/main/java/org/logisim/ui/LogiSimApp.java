package org.logisim.ui;

import javax.swing.SwingUtilities;

/**
 * Entry point for the Swing UI. Responsible for bootstrapping the EDT and
 * showing the main frame.
 */
public final class LogiSimApp {
    private LogiSimApp() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LogiSimFrame frame = new LogiSimFrame();
            frame.setVisible(true);
        });
    }
}

