package snma.game.pong.client

import com.google.common.base.Preconditions
import com.jme3.app.SimpleApplication
import com.jme3.bullet.BulletAppState
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.SphereCollisionShape
import com.jme3.bullet.control.RigidBodyControl
import com.jme3.input.KeyInput
import com.jme3.input.controls.AnalogListener
import com.jme3.input.controls.KeyTrigger
import com.jme3.math.Vector3f
import com.jme3.network.Client
import com.jme3.network.ClientStateListener
import com.jme3.network.Network
import com.jme3.network.NetworkClient
import com.jme3.scene.Node
import com.jme3.texture.Texture2D
import com.jme3.ui.Picture
import snma.game.pong.Constants
import snma.game.pong.Model
import snma.game.pong.messages.ClientMovedMessage
import snma.game.pong.messages.PhysicsStateMessage
import kotlin.math.max
import kotlin.math.min

class ClientApp(
    private val host: String,
    private val port: Int,
    private val onExit: () -> Unit,
) : SimpleApplication() {
    private lateinit var client: NetworkClient
    private lateinit var model: Model

    private fun log(msg: String) {
        System.err.println(msg)
    }

    override fun simpleInitApp() {
        setDisplayStatView(false)
        isPauseOnLostFocus = false
        flyCam.isEnabled = false

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
                is PhysicsStateMessage -> {
                    enqueue { model.applyMessage(message) }
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
//            model.playerPos = newPos
//            model.enemyPos = newPos
        }, Input.LEFT.name, Input.RIGHT.name)
    }

//    private var lastUpdateTime = 0L;
//    override fun simpleUpdate(tpf: Float) {
//        if (lastUpdateTime + 100L < System.currentTimeMillis()) {
//            lastUpdateTime = System.currentTimeMillis()
//            model.applyMessage(model.generateMessage(false))
//        }
//    }

    override fun destroy() {
        if (client.isConnected) {
            client.close()
        }
        super.destroy()
        onExit()
    }
}

private enum class Input {
    LEFT,
    RIGHT,
}