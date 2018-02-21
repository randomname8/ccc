package ccc.util

import javafx.scene.image.PixelFormat
import uk.co.caprica.vlcj.player.direct.DefaultDirectMediaPlayer

class VlcTexture(val mediaPlayer: DefaultDirectMediaPlayer) extends Texture {
  override def width = mediaPlayer.getBufferFormat.getWidth
  override def height = mediaPlayer.getBufferFormat.getHeight
  override val pixelFormat = PixelFormat.getByteBgraPreInstance
  override def scanlineStride = mediaPlayer.getBufferFormat.getPitches()(0)
  override def capture() = {
    val memories = mediaPlayer.lock()
    if (memories != null) {
      val memory = memories(0)
      Some(memory.getByteBuffer(0, memory.size))
    } else None
  }
  override def release() = mediaPlayer.unlock()
}
