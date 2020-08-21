package snma.game.pong

import com.google.common.base.Preconditions
import com.jme3.app.state.AppStateManager
import com.jme3.asset.AssetManager
import com.jme3.bullet.BulletAppState
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.PlaneCollisionShape
import com.jme3.bullet.collision.shapes.SphereCollisionShape
import com.jme3.bullet.control.RigidBodyControl
import com.jme3.math.Plane
import com.jme3.math.Vector3f
import com.jme3.scene.Node
import com.jme3.texture.Texture2D
import com.jme3.ui.Picture
import snma.game.pong.messages.PhysicsState
import snma.game.pong.messages.PhysicsStateMessage

class Model(private val stateManager: AppStateManager, private val assetManager: AssetManager, private val isClient: Boolean) {
    private val player: Node
    private val enemy: Node
    private val ball: Node
    val xLimits = Pair(Constants.PLAYER_WIDTH / 2f, Constants.SCREEN_WIDTH - Constants.PLAYER_WIDTH / 2f)
    var playerPos: Float
        get() = player.localTranslation.x
        set(value) {
            val loc = player.localTranslation
            loc.set(value, Constants.PLAYER_HEIGHT / 2f, -500f) // FIXME do we need this -500?
            player.localTranslation = loc
        }
    var enemyPos: Float
        get() = enemy.localTranslation.x
        set(value) {
            val loc = enemy.localTranslation
            loc.set(value, Constants.SCREEN_HEIGHT - Constants.PLAYER_HEIGHT / 2f, -500f)
            enemy.localTranslation = loc
        }

    private val bulletAppState: BulletAppState

    init {
        player = if (isClient) loadImageNode("player") else Node()
        enemy = if (isClient) loadImageNode("player") else Node()
        ball = if (isClient) loadImageNode("ball") else Node()
        if (isClient) {
            Preconditions.checkArgument(player.getUserData<Int>("widthInt") == Constants.PLAYER_WIDTH)
            Preconditions.checkArgument(player.getUserData<Int>("heightInt") == Constants.PLAYER_HEIGHT)
            Preconditions.checkArgument(ball.getUserData<Int>("radiusInt") == Constants.BALL_RADIUS)
        }

        player.setLocalTranslation(Constants.SCREEN_WIDTH / 2f, Constants.PLAYER_HEIGHT / 2f, -500f)
        enemy.setLocalTranslation(Constants.SCREEN_WIDTH / 2f, Constants.SCREEN_HEIGHT - Constants.PLAYER_HEIGHT / 2f, -500f)
        ball.setLocalTranslation(Constants.SCREEN_WIDTH / 2f, Constants.SCREEN_HEIGHT / 2f, 0f)

        bulletAppState = BulletAppState()
        stateManager.attach(bulletAppState)
        bulletAppState.physicsSpace.setGravity(Vector3f.ZERO)

        ball.addControl(RigidBodyControl(SphereCollisionShape(Constants.BALL_RADIUS.toFloat()), 1f).apply {
            friction = 0.5f
            restitution = 1f
            linearVelocity = /*if (isClient) Vector3f.ZERO else*/ Vector3f(100f, -200f, 0f)
            angularVelocity = Vector3f(0f, 0f, 10f)
        })
        bulletAppState.physicsSpace.add(ball)

        for (k in listOf(player, enemy)) {
            k.addControl(RigidBodyControl(
                BoxCollisionShape(Vector3f(Constants.PLAYER_WIDTH / 2f, Constants.PLAYER_HEIGHT / 2f, 500f)),
                10f
            ).apply {
                friction = 0.5f
                restitution = 1f
                isKinematic = true
            })
            bulletAppState.physicsSpace.add(k)
        }

        for ((normal, shift) in listOf(
            Vector3f(-1f, 0f, 0f) to -Constants.SCREEN_WIDTH.toFloat(),
            Vector3f(1f, 0f, 0f) to 0f,
            Vector3f(0f, 1f, 0f) to 0f - 20f,
            Vector3f(0f, -1f, 0f) to -Constants.SCREEN_HEIGHT.toFloat() - 20f,
        )) {
            val wall = Node()
            wall.addControl(RigidBodyControl(
                PlaneCollisionShape(Plane(normal, shift)),
                0f
            ).apply {
                friction = 0f
                restitution = 1f
            })
            bulletAppState.physicsSpace.add(wall)
        }
    }

    fun attachToGuiNode(guiNode: Node) {
        guiNode.apply {
            attachChild(player)
            attachChild(enemy)
            attachChild(ball)
        }
    }

    fun generateMessage(swap: Boolean): PhysicsStateMessage {
        val ballControl = ball.getControl(RigidBodyControl::class.java)
        return if (!swap) {
            PhysicsStateMessage(
                you = playerPos,
                enemy = enemyPos,
                ball = PhysicsState.ofRigidBody(ballControl, false),
            )
        } else {
            PhysicsStateMessage(
                you = enemyPos,
                enemy = playerPos,
                ball = PhysicsState.ofRigidBody(ballControl, true),
            )
        }
    }

    fun applyMessage(message: PhysicsStateMessage) {
        val ballControl = ball.getControl(RigidBodyControl::class.java)

        playerPos = message.you
        enemyPos = message.enemy

        message.ball.applyToRigidBody(ballControl)
    }

    fun setBallVerticalSpeed(vSpeed: Float) {
        val ballControl = ball.getControl(RigidBodyControl::class.java)
        val vel = ballControl.linearVelocity
        vel.y = if (vel.y > 0) vSpeed else -vSpeed
        ballControl.linearVelocity = vel
    }

    fun destroy() {
        bulletAppState.stopPhysics()
        stateManager.detach(bulletAppState)
    }

    fun debugLog() {
        val ballControl = ball.getControl(RigidBodyControl::class.java)
    }

    private fun loadImageNode(name: String): Node {
        val texture = assetManager.loadTexture("textures/$name.png") as Texture2D
        val width = texture.image.width
        val height = texture.image.height
        val picture = Picture(name).apply {
            setTexture(assetManager, texture, true)
            setWidth(width.toFloat())
            setHeight(height.toFloat())
            move(-width / 2f, -height / 2f, 0f)
        }
        return Node(name).apply {
            setUserData("radiusInt", (width + height) / 4)
            setUserData("widthInt", width)
            setUserData("heightInt", height)
            attachChild(picture)
        }
    }
}