package snma.game.pong.startup

import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities

fun main() {
    SwingUtilities.invokeLater {
        with(JFrame()) {
            title = "Pong Game: Settings"
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            val switchPanelCbc: (JPanel) -> Unit = { contentPane.removeAll(); contentPane.add(it); pack() }
            contentPane.add(SelectModePanel(switchPanelCbc))
            pack()
            minimumSize = Dimension(400, 200)
            isVisible = true
        }
    }
}
