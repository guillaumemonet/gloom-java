package gloom.host;

import gloom.boot.DiskFetch;

import javax.swing.JOptionPane;
import java.awt.GraphicsEnvironment;

/**
 * Comment le desktop pose la question des disquettes : une boîte de dialogue Swing, ou le terminal
 * sans écran. (Même principe que le portage Alien Breed 3D II.) Exclu des builds Android.
 */
public final class DesktopAsk implements DiskFetch.Ask {

    public static final DesktopAsk INSTANCE = new DesktopAsk();

    private static final String TITLE = "Gloom — donnees du jeu";

    private DesktopAsk() {
    }

    @Override
    public boolean confirm(String message) {
        if (GraphicsEnvironment.isHeadless()) return DiskFetch.CONSOLE.confirm(message);
        return JOptionPane.showConfirmDialog(null, message, TITLE,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    @Override
    public void info(String message) {
        DiskFetch.CONSOLE.info(message);
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(null, message, TITLE, JOptionPane.INFORMATION_MESSAGE);
        }
    }

    @Override
    public void error(String message) {
        DiskFetch.CONSOLE.error(message);
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(null, message, TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }
}
