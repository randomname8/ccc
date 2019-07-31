package ccc
package util

import java.nio.ByteBuffer
import javafx.animation.AnimationTimer
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.canvas.Canvas
import javafx.scene.image.PixelFormat
import javafx.scene.layout.StackPane
import scala.concurrent.duration.DurationLong
import tangerine._

trait Texture {
  def width: Int
  def height: Int
  def pixelFormat: PixelFormat[ByteBuffer]
  def scanlineStride: Int
  def capture(): Option[ByteBuffer]
  def release(): Unit
}

class TextureNode(val texture: Texture, val targetFps: Int) extends StackPane {

  private val canvas = new Canvas()
  StackPane.setAlignment(canvas, Pos.CENTER)
  getChildren add new Group(canvas)
  
  setMinSize(0, 0)
  
  private val viewportWriter = canvas.getGraphicsContext2D.getPixelWriter
  private val pixelFormat = PixelFormat.getByteBgraPreInstance
  
  val renderer = new AnimationTimer() {
    val totalTimePerFrame = 1000L / targetFps
    var nextFrame: Long = 0
    override def handle(now: Long): Unit = {
      if (now >= nextFrame) {
        try texture.capture() match {
          case Some(buffer) =>
            if (canvas.getWidth == 0) {
              canvas.setWidth(texture.width.toDouble)
              canvas.setHeight(texture.height.toDouble)
              calculateCanvasScaling()
            }
            if (canvas.getWidth != texture.width.toDouble) canvas.setWidth(texture.width.toDouble)
            if (canvas.getHeight != texture.height.toDouble) canvas.setHeight(texture.height.toDouble)
        
            viewportWriter.setPixels(0, 0, texture.width, texture.height, pixelFormat, buffer, texture.scanlineStride)
          case None =>
        } finally texture.release()
        
        val renderTime = (System.nanoTime - now).nanos.toMillis
        nextFrame = now + (totalTimePerFrame - renderTime).millis.toNanos
      }
    }
  }
  
  boundsInParentProperty foreach (_ => calculateCanvasScaling())
  private def calculateCanvasScaling(): Unit = {
    val (width, height) = (getWidth, getHeight) 
    val canvasBounds = canvas.getBoundsInLocal
    val ratio = {
      if (width > height) height / canvasBounds.getHeight
      else width / canvasBounds.getWidth
    }
    if (!ratio.isInfinity && !ratio.isNaN) {
      canvas setScaleX ratio
      canvas setScaleY ratio
    }
  }
}