package snma.game.pong.client

import com.jme3.app.SimpleApplication
import com.jme3.font.BitmapFont
import com.jme3.font.BitmapText
import com.jme3.input.KeyInput
import com.jme3.input.controls.AnalogListener
import com.jme3.input.controls.KeyTrigger
import com.jme3.network.Client
import com.jme3.network.ClientStateListener
import com.jme3.network.Network
import com.jme3.network.NetworkClient
import com.jme3.post.FilterPostProcessor
import com.jme3.post.filters.BloomFilter
import snma.game.pong.Constants
import snma.game.pong.event_queue.EventQueue
import snma.game.pong.messages.*
import snma.game.pong.model.Model
import kotlin.math.max
import kotlin.math.min

class ClientApp(
    private val host: String,
    private val port: Int,
    private val onExit: () -> Unit,
) : SimpleApplication() {
    private lateinit var client: NetworkClient
    private lateinit var model: Model

    private lateinit var font: BitmapFont
    private lateinit var countdownText: BitmapText
    private lateinit var yourScoreText: BitmapText
    private lateinit var enemyScoreText: BitmapText

    private val eventQueue = EventQueue()

    private fun log(msg: String) {
        System.err.println(msg)
    }

    override fun simpleInitApp() {
        setDisplayStatView(false)
        isPauseOnLostFocus = false
        flyCam.isEnabled = false

        font = assetManager.loadFont("fonts/Ubuntu.fnt")

        model = Model(stateManager, assetManager, true, guiNode)

        client = Network.createClient(Constants.GAME_NAME, Constants.GAME_VERSION)!!
        client.addClientStateListener(object : ClientStateListener {
            override fun clientConnected(c: Client) {
                log("Connected")
            }

            override fun clientDisconnected(c: Client, info: ClientStateListener.DisconnectInfo?) {
                log("Disconnected")
                stop(false)
            }
        })
        client.addMessageListener { _, message ->
            when (message) {
                is CountdownMessage -> enqueue {
                    log("Countdown: ${message.value}")
                    guiNode.detachChild(countdownText)

                    countdownText.apply {
                        text = message.value.let { if (it == 0) "Go!" else it.toString() }
                        setLocalTranslation(
                            (Constants.SCREEN_WIDTH - lineWidth) / 2f,
                            (Constants.SCREEN_HEIGHT + height) / 2f,
                            0f
                        )
                        guiNode.attachChild(this)
                    }

                    eventQueue.enqueue(System.currentTimeMillis() + 700L) {
                        guiNode.detachChild(countdownText)
                    }

                    if (message.value == 0) {
                        displayScore(0, 0)
                    }
                }
                is PhysicsStateMessage -> enqueue {
                    model.applyMessage(message)
                }
                is WallCollisionMessage -> enqueue {
                    // TODO: sound
                }
                is PlayerCollisionMessage -> enqueue {
                    // TODO: sound
                }
                is ScoreIncreaseMessage -> enqueue {
                    model.relocateBall()
                    // TODO: sound
                    displayScore(message.yourNewScore, message.enemyNewScore)
                }
            }
        }
        client.connectToServer(host, port, port)
        client.start()

        inputManager.addMapping(Input.LEFT.name, KeyTrigger(KeyInput.KEY_LEFT), KeyTrigger(KeyInput.KEY_A))
        inputManager.addMapping(Input.RIGHT.name, KeyTrigger(KeyInput.KEY_RIGHT), KeyTrigger(KeyInput.KEY_D))
        inputManager.addListener(AnalogListener { name: String, value: Float, _ ->
            var dx = value * Constants.MOVE_SPEED
            if (name == Input.LEFT.name) {
                dx *= -1f
            }
            var newPos = model.playerPos + dx
            newPos = min(max(model.xLimits.first, newPos), model.xLimits.second)
            client.send(ClientMovedMessage(newPos))
            model.playerPos = newPos
        }, Input.LEFT.name, Input.RIGHT.name)

        val fpp = FilterPostProcessor(assetManager)
        fpp.addFilter(BloomFilter().apply {
            bloomIntensity = 2f
            exposurePower = 2f
            exposureCutOff = 0f
            blurScale = 1.5f
        })
        guiViewPort.addProcessor(fpp)
        guiViewPort.isClearColor = true

        countdownText = BitmapText(font).apply {
            size = font.charSet.renderedSize.toFloat()
            text = "Wait"
            setLocalTranslation(
                (Constants.SCREEN_WIDTH - lineWidth) / 2f,
                (Constants.SCREEN_HEIGHT + height) / 2f,
                0f
            )
            guiNode.attachChild(this)
        }
    }

    override fun simpleUpdate(tpf: Float) {
        eventQueue.executeIfNeeded()
    }

    override fun destroy() {
        if (client.isConnected) {
            client.close()
        }
        eventQueue.cancelAll()
        super.destroy()
        onExit()
    }

    private fun displayScore(yourScore: Int, enemyScore: Int) {
        log("$yourScore:$enemyScore")
        if (!this::yourScoreText.isInitialized) {
            yourScoreText = BitmapText(font).apply {
                size = font.charSet.renderedSize.toFloat()
                guiNode.attachChild(this)
            }
            enemyScoreText = BitmapText(font).apply {
                size = font.charSet.renderedSize.toFloat()
                guiNode.attachChild(this)
            }
        }
        yourScoreText.apply {
            text = yourScore.toString()
            setLocalTranslation(Constants.SCREEN_WIDTH - lineWidth - 20f, Constants.SCREEN_HEIGHT * 0.25f + 0.5f * height, 0f)
        }
        enemyScoreText.apply {
            text = enemyScore.toString()
            setLocalTranslation(Constants.SCREEN_WIDTH - lineWidth - 20f, Constants.SCREEN_HEIGHT * 0.75f + 0.5f * height, 0f)
        }
    }
}

private enum class Input {
    LEFT,
    RIGHT,
}