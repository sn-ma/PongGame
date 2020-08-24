package snma.game.pong.model

import com.google.common.base.Preconditions
import com.jme3.app.state.AppStateManager
import com.jme3.asset.AssetManager
import com.jme3.bullet.BulletAppState
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.PlaneCollisionShape
import com.jme3.bullet.collision.shapes.SphereCollisionShape
import com.jme3.bullet.control.RigidBodyControl
import com.jme3.math.*
import com.jme3.scene.Node
import com.jme3.texture.Texture2D
import com.jme3.ui.Picture
import snma.game.pong.Constants
import snma.game.pong.messages.PhysicsState
import snma.game.pong.messages.PhysicsStateMessage
import kotlin.math.absoluteValue

class Model(
    private val stateManager: AppStateManager,
    private val assetManager: AssetManager,
    isGuiEnabled: Boolean,
    private val guiNode: Node // HACK: without attaching to scene graph physics may lag
) {
    private val player: Node
    private val enemy: Node
    private val ball: Node
    private val walls: List<Node>
    private val floors: List<Node>
    val xLimits = Pair(Constants.PLAYER_WIDTH / 2f, Constants.SCREEN_WIDTH - Constants.PLAYER_WIDTH / 2f)
    var playerPos: Float
        get() = player.localTranslation.x
        set(value) {
            val loc = player.localTranslation
            loc.set(value, Constants.PLAYER_HEIGHT / 2f, 0f)
            player.localTranslation = loc
        }
    var enemyPos: Float
        get() = enemy.localTranslation.x
        set(value) {
            val loc = enemy.localTranslation
            loc.set(value, Constants.SCREEN_HEIGHT - Constants.PLAYER_HEIGHT / 2f, 0f)
            enemy.localTranslation = loc
        }
    private var collisionListener: ((Collision) -> Unit)? = null

    private val bulletAppState: BulletAppState

    init {
        player = if (isGuiEnabled) loadImageNode("player") else Node()
        enemy = if (isGuiEnabled) loadImageNode("player") else Node()
        ball = if (isGuiEnabled) loadImageNode("ball") else Node()
        if (isGuiEnabled) {
            Preconditions.checkArgument(player.getUserData<Int>("widthInt") == Constants.PLAYER_WIDTH)
            Preconditions.checkArgument(player.getUserData<Int>("heightInt") == Constants.PLAYER_HEIGHT)
            Preconditions.checkArgument(ball.getUserData<Int>("radiusInt") == Constants.BALL_RADIUS)
        }
        guiNode.apply {
            attachChild(player)
            attachChild(enemy)
            attachChild(ball)
        }

        player.setLocalTranslation(Constants.SCREEN_WIDTH / 2f, Constants.PLAYER_HEIGHT / 2f, 0f)
        enemy.setLocalTranslation(Constants.SCREEN_WIDTH / 2f, Constants.SCREEN_HEIGHT - Constants.PLAYER_HEIGHT / 2f, 0f)
        ball.setLocalTranslation(Constants.SCREEN_WIDTH / 2f, Constants.SCREEN_HEIGHT / 2f, 0f)

        bulletAppState = BulletAppState(Vector3f(-50f, -50f, -50f), Vector3f(Constants.SCREEN_WIDTH + 50f, Constants.SCREEN_HEIGHT + 50f, 50f))
        stateManager.attach(bulletAppState)
        bulletAppState.physicsSpace.setGravity(Vector3f.ZERO)

        ball.addControl(RigidBodyControl(SphereCollisionShape(Constants.BALL_RADIUS.toFloat()), 1f).apply {
            friction = 0.5f
            restitution = 1f
            linearVelocity = Vector3f.ZERO
            angularVelocity = Vector3f.ZERO
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

        fun addWall(normal: Vector3f, shift: Float): Node {
            val wall = Node()
            wall.addControl(RigidBodyControl(
                PlaneCollisionShape(Plane(normal, shift)),
                0f
            ).apply {
                friction = 0f
                restitution = 1f
            })
            bulletAppState.physicsSpace.add(wall)
            guiNode.attachChild(wall)
            return wall
        }

        walls = listOf(
            addWall(Vector3f(-1f, 0f, 0f), -Constants.SCREEN_WIDTH.toFloat()),
            addWall(Vector3f(1f, 0f, 0f), 0f)
        )
        floors = listOf(
            addWall(Vector3f(0f, 1f, 0f), -Constants.BALL_RADIUS.toFloat()),
            addWall(Vector3f(0f, -1f, 0f), (-Constants.SCREEN_HEIGHT - Constants.BALL_RADIUS).toFloat())
        )

        bulletAppState.physicsSpace.addCollisionListener { event ->
            if (collisionListener == null) {
                return@addCollisionListener
            }
            val collidedWith = if (event.nodeA == ball) event.nodeB else if (event.nodeB == ball) event.nodeA else return@addCollisionListener
            val pos = Vector2f(event.positionWorldOnA.x, event.positionWorldOnA.y) // TODO check it is what we need
            val collision = when (collidedWith) {
                player -> PlayerCollision(pos, true)
                enemy -> PlayerCollision(pos, false)
                in walls -> WallCollision(pos)
                in floors -> FloorCollision(pos, collidedWith == floors[1])
                else -> throw RuntimeException("Unexpected collision object: $collidedWith")
            }
            collisionListener?.invoke(collision)
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

    fun setCollisionListener(listener: (Collision) -> Unit) {
        this.collisionListener = listener
    }

    fun setBallVerticalVelocity(vVelocity: Float) {
        val ballControl = ball.getControl(RigidBodyControl::class.java)
        val vel = ballControl.linearVelocity
        vel.y = when {
            vel.y > 0 -> vVelocity
            vel.y < 0 -> -vVelocity
            else -> vVelocity * if (FastMath.rand.nextBoolean()) 1 else -1
        }
        ballControl.linearVelocity = vel
    }

    fun adjustBallHorizontalVelocity(minVelocityKoeff: Float, factor: Float, chance: Float) {
        val ballControl = ball.getControl(RigidBodyControl::class.java)
        val vel = ballControl.linearVelocity
        if (vel.x.absoluteValue < minVelocityKoeff * vel.y.absoluteValue && (chance >= 1f || FastMath.rand.nextFloat() <= chance)) {
            vel.x += vel.y * factor * (0.5f - FastMath.rand.nextFloat()) * 2f
            ballControl.linearVelocity = vel
        }
    }

    fun relocateBall() {
        val ballControl = ball.getControl(RigidBodyControl::class.java)
        ballControl.physicsLocation = Vector3f(Constants.SCREEN_WIDTH / 2f, Constants.SCREEN_HEIGHT / 2f, 0f)
        ballControl.physicsRotation = Quaternion.IDENTITY
        ballControl.angularVelocity = Vector3f.ZERO
        ballControl.linearVelocity = Vector3f.ZERO
    }

    fun destroy() {
        bulletAppState.stopPhysics()
        stateManager.detach(bulletAppState)
        guiNode.detachChild(player)
        guiNode.detachChild(enemy)
        guiNode.detachChild(ball)
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