package ccc
package util

import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage

object JfxUtils {
  
  private val animationFieldAccessor = classOf[Image].getDeclaredMethod("isAnimation")
  animationFieldAccessor.setAccessible(true)
  def isAnimated(image: Image): Boolean = animationFieldAccessor.invoke(image).asInstanceOf[Boolean]
  def snapshot(image: Image): Image = new ImageView(image).snapshot(null, new WritableImage(image.getWidth.toInt, image.getHeight.toInt))
}
