package snma.game.pong.model

import com.jme3.math.Vector3f

sealed class Collision(val position: Vector3f)

class WallCollision(position: Vector3f): Collision(position)
class FloorCollision(position: Vector3f): Collision(position)
class PlayerCollision(position: Vector3f, val isYou: Boolean): Collision(position)