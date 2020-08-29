package snma.game.pong.client

import com.jme3.asset.AssetManager
import com.jme3.audio.AudioData
import com.jme3.audio.AudioNode

class Sounds(
        private val assetManager: AssetManager
) {
    private val explosion = load("explosion")
    private val playerCollision = load("player_collision", 0.6f)
    private val wallCollision = load("wall_collision")

    fun playExplosion() {
        explosion.playInstance()
    }

    fun playPlayerCollision() {
        playerCollision.playInstance()
    }

    fun playWallCollision() {
        wallCollision.playInstance()
    }

    private fun load(name: String, volume: Float = 1f): AudioNode {
        return AudioNode(assetManager, "sounds/$name.wav", AudioData.DataType.Buffer).apply {
            isPositional = false
            isReverbEnabled = false
            isLooping = false
            this.volume = volume
        }
    }
}