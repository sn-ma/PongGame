package snma.game.pong.model

import com.jme3.math.Vector2f

sealed class Collision(val position: Vector2f)

class WallCollision(position: Vector2f): Collision(position)
class FloorCollision(position: Vector2f, val isYourWin: Boolean): Collision(position)
class PlayerCollision(position: Vector2f, val isYou: Boolean): Collision(position)