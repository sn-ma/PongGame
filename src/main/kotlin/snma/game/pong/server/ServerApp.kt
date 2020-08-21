package snma.game.pong.server

import com.jme3.app.SimpleApplication
import com.jme3.network.ConnectionListener
import com.jme3.network.HostedConnection
import com.jme3.network.Network
import com.jme3.network.Server
import net.miginfocom.swing.MigLayout
import snma.game.pong.Constants
import snma.game.pong.Model
import snma.game.pong.messages.ClientMovedMessage
import snma.game.pong.messages.Messages
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import kotlin.system.exitProcess


class ServerApp(private val port: Int) : SimpleApplication() {
    private val textPane = JTextArea().apply {
        isEditable = false
    }
    val view = JPanel(MigLayout("fill")).apply {
        add(JScrollPane(textPane), "grow")
    }

    private lateinit var server: Server
    private var players = listOf<HostedConnection>()
    private var model: Model? = null

    private var requiredAction: Action? = null
    private var lastUpdateTime: Long = 0L

    private fun log(msg: String) {
        SwingUtilities.invokeLater {
            if (textPane.text != "") {
                textPane.text += "\n"
            }
            textPane.text += msg
        }
    }

    override fun simpleInitApp() {
        log("Launching on port $port...")
        server = Network.createServer(Constants.GAME_NAME, Constants.GAME_VERSION, port, port)
        server.addConnectionListener(object : ConnectionListener {
            override fun connectionAdded(server: Server, connection: HostedConnection) {
                log("Connected ${connection.id} from ${connection.address}")
                if (server.connections.size == 2) {
                    enqueue {
                        players = ArrayList(server.connections)
                        requiredAction = Action.START_GAME
                    }
                    log("2 players connected, starting game")
                } else if (server.connections.size > 2) {
                    log("Kicking an extra player")
                    connection.close("Too much players!")
                }
            }

            override fun connectionRemoved(_server: Server, connection: HostedConnection) {
                SwingUtilities.invokeLater {
                    log("Disconnected ${connection.id} from ${connection.address}")
                }
                if (connection in players) {
                    requiredAction = Action.STOP_GAME
                    log("Player disconnected, stopping the game")
                }
            }
        })
        server.addMessageListener { source, message ->
            if (model != null) {
                when (message) {
                    is ClientMovedMessage -> {
                        when (source) {
                            players[0] -> {
                                enqueue { model!!.playerPos = message.newPosition }
                            }
                            players[1] -> {
                                enqueue { model!!.enemyPos = message.newPosition }
                            }
                        }
                    }
                }
            }
        }
        Messages.register()
        server.start()
        log("Launched.")
    }

    override fun simpleUpdate(tpf: Float) {
        when (requiredAction) {
            Action.START_GAME -> {
                model = Model(stateManager, assetManager, false)
            }
            Action.STOP_GAME -> {
                if (model != null) {
                    model!!.destroy()
                    model = null
                }
                players = listOf()
                server.connections.forEach{ it.close("End of game") }
            }
            null -> {}
            else -> {
                log("Unexpected action: $requiredAction")
            }
        }
        requiredAction = null

        if (model != null && players.size == 2) {
            val curTime = System.currentTimeMillis()
            if (lastUpdateTime + Constants.UPDATE_TIME_MILLIS <= curTime) {
                lastUpdateTime = curTime

                model!!.setBallVerticalSpeed(100f)

                val normalMsg = model!!.generateMessage(false)
                model!!.applyMessage(normalMsg)
                players[0].send(normalMsg)
                players[1].send(model!!.generateMessage(true))
            }
        }
        model?.debugLog()
    }

    override fun destroy() {
        server.close()

        super.destroy()
    }
}

private enum class Action {
    START_GAME,
    STOP_GAME,
}