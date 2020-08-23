package snma.game.pong.startup

import com.jme3.system.AppSettings
import com.jme3.system.JmeContext
import net.miginfocom.swing.MigLayout
import snma.game.pong.Constants
import snma.game.pong.client.ClientApp
import snma.game.pong.server.ServerApp
import java.awt.Color
import javax.swing.*
import kotlin.system.exitProcess

const val DEFAULT_PORT = 4321

class SelectModePanel(switchPanelCbc: (JPanel) -> Unit): JTabbedPane() {
    init {
        addTab("Client", JPanel(MigLayout("wrap 2, fill", "[][grow]", "[][][][grow]")).also { panel ->
            val addrField = JTextField("localhost")
            val portField = JTextField("$DEFAULT_PORT")
            val errorLabel = JLabel().apply {
                foreground = Color.RED
            }
            val connectBtn = JButton("Connect").apply {
                addActionListener {
                    errorLabel.text = ""

                    try {
                        ClientApp(
                            host = addrField.text,
                            port = portField.text.toInt(),
                            onExit = { SwingUtilities.invokeLater {
                                isEnabled = true
                                this@SelectModePanel.isEnabled = true
                            } }
                        ).apply {
                            setSettings(AppSettings(true).apply {
                                title = "Pong Game"
                                setResolution(Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT)
                                samples = 4
                                isVSync = true
                            })
                            start(JmeContext.Type.Display)
                        }
                        isEnabled = false
                        this@SelectModePanel.isEnabled = false
                    } catch (ex: Exception) {
                        errorLabel.text = "Failed: $ex"
                    }
                }
            }

            panel.apply {
                add(JLabel("Address (ip):"))
                add(addrField, "growx")

                add(JLabel("Port:"))
                add(portField, "growx")

                add(errorLabel, "span")

                add(connectBtn, "span, grow")
            }
        })

        addTab("Server", JPanel(MigLayout("wrap 2, fill", "[][grow]", "[][][][grow]")).apply {
            val portField = JTextField("$DEFAULT_PORT")
            val showGuiCheckbox = JCheckBox("Show the game")
            val errorLabel = JLabel().apply {
                foreground = Color.RED
            }
            val launchBtn = JButton("Launch").apply {
                addActionListener {
                    errorLabel.text = ""

                    try {
                        if (showGuiCheckbox.isSelected) {
                            ServerApp(
                                port = portField.text.toInt(),
                                showTheGame = true,
                                onExit = { exitProcess(0) }
                            ).apply {
                                setSettings(AppSettings(true).apply {
                                    title = "Pong Game: SERVER"
                                    setResolution(Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT)
                                    samples = 4
                                    isVSync = true
                                })
                                start(JmeContext.Type.Display)
                                switchPanelCbc(view)
                            }
                        } else {
                            ServerApp(
                                port = portField.text.toInt(),
                                showTheGame = false,
                                onExit = { exitProcess(0) }
                            ).apply {
                                start(JmeContext.Type.Headless)
                                switchPanelCbc(view)
                            }
                        }
                    } catch (ex: Exception) {
                        errorLabel.text = "Failed: $ex"
                    }
                }
            }

            add(JLabel("Port:"))
            add(portField, "growx")

            add(showGuiCheckbox, "span")

            add(errorLabel, "span")

            add(launchBtn, "span, grow")
        })
    }
}