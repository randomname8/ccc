package ccc.util

import javafx.scene.image.Image
import scala.ref.WeakReference

/**
 * An image wrapper that keeps a weak reference to the image.
 * 
 * If the image is requested after it was collected, it is re-retrieved, this allow for efficent caching of images while specially keeping
 * the visible ones, and allowing the rest to be collected.
 */
class WeakImage(imageLocation: String) {
  private[this] var imageReference = WeakReference(new Image(imageLocation))
  def get: Image = imageReference.get.getOrElse {
    val res = new Image(imageLocation, true)
    imageReference = WeakReference(res)
    res
  }
}
