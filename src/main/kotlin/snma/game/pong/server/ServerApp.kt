package snma.game.pong.server

import com.jme3.app.SimpleApplication
import com.jme3.network.ConnectionListener
import com.jme3.network.HostedConnection
import com.jme3.network.Network
import com.jme3.network.Server
import net.miginfocom.swing.MigLayout
import snma.game.pong.Constants
import snma.game.pong.event_queue.EventQueue
import snma.game.pong.messages.*
import snma.game.pong.model.FloorCollision
import snma.game.pong.model.Model
import snma.game.pong.model.PlayerCollision
import snma.game.pong.model.WallCollision
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities


class ServerApp(
    private val port: Int,
    private val showTheGame: Boolean,
    private val onExit: () -> Unit,
) : SimpleApplication() {
    private val textPane = JTextArea().apply {
        isEditable = false
    }
    val view = JPanel(MigLayout("fill")).apply {
        add(JScrollPane(textPane), "grow")
    }

    private lateinit var server: Server
    private var players = listOf<HostedConnection>()
    private var playersScore = arrayListOf(0, 0)
    private var model: Model? = null

    private var lastUpdateTime: Long = 0L

    private val eventQueue = EventQueue()

    private fun log(msg: String) {
        SwingUtilities.invokeLater {
            if (textPane.text != "") {
                textPane.text += "\n"
            }
            textPane.text += msg
        }
    }

    override fun simpleInitApp() {
        isPauseOnLostFocus = false
        flyCam.isEnabled = false

        log("Launching on port $port...")
        server = Network.createServer(Constants.GAME_NAME, Constants.GAME_VERSION, port, port)
        server.addConnectionListener(object : ConnectionListener {
            override fun connectionAdded(server: Server, connection: HostedConnection) {
                log("Connected ${connection.id} from ${connection.address}")
                if (server.connections.size == 2) {
                    enqueue {
                        players = ArrayList(server.connections)
                        if (players.size != 2) {
                            players = listOf()
                            return@enqueue
                        }
                        playersScore = arrayListOf(0, 0)

                        var t = System.currentTimeMillis()
                        for (i in Constants.COUNTDOWN_FROM downTo 0) {
                            eventQueue.enqueue(t) {
                                val msg = CountdownMessage(i)
                                players.forEach { it.send(msg) }
                                log("Sending countdown $i")
                            }
                            t += 1000L
                        }
                        t -= 1000L
                        eventQueue.enqueue(t) {
                            log("Starting the game")
                            model = Model(stateManager, assetManager, showTheGame, guiNode)

                            model!!.setBallVerticalVelocity(100f) // Hack
                            model!!.adjustBallHorizontalVelocity(1f, 3f, 1f)

                            model!!.setCollisionListener { collision ->
                                when (collision) {
                                    is WallCollision -> {
                                        val msg = WallCollisionMessage(collision.position)
                                        players.forEach { it.send(msg) }
                                    }
                                    is PlayerCollision -> {
                                        model!!.adjustBallHorizontalVelocity(0.3f, 2f, 1f)

                                        players.getOrNull(0)?.send(PlayerCollisionMessage(collision.position, collision.isYou))
                                        players.getOrNull(1)?.send(PlayerCollisionMessage(collision.position, !collision.isYou))
                                    }
                                    is FloorCollision -> {
                                        model!!.relocateBall()

                                        if (collision.isYourWin) {
                                            playersScore[0]++
                                        } else {
                                            playersScore[1]++
                                        }

                                        players.getOrNull(0)
                                            ?.send(ScoreIncreaseMessage(collision.position, playersScore[0], playersScore[1], collision.isYourWin))
                                        players.getOrNull(1)
                                            ?.send(ScoreIncreaseMessage(collision.position, playersScore[1], playersScore[0], !collision.isYourWin))
                                    }
                                }
                            }
                        }
                    }
                    log("2 players connected, starting the game")
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
                    log("Player disconnected, stopping the game")
                    enqueue {
                        if (model != null) {
                            model!!.destroy()
                            model = null
                        }
                        players = listOf()
                        server.connections.forEach { it.close("End of game") }
                        eventQueue.cancelAll()
                    }
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
        val curTime = System.currentTimeMillis()

        if (model != null && players.size == 2) {
            if (lastUpdateTime + Constants.UPDATE_TIME_MILLIS <= curTime) {
                lastUpdateTime = curTime

                model!!.setBallVerticalVelocity(100f) // Hack

                val normalMsg = model!!.generateMessage(false)
                model!!.applyMessage(normalMsg)
                players[0].send(normalMsg)
                players[1].send(model!!.generateMessage(true))
            }
        }

        eventQueue.executeIfNeeded()
    }

    override fun destroy() {
        server.close()
        super.destroy()
        onExit()
    }
}