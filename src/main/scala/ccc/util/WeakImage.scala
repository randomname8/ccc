package ccc.util

import javafx.scene.image.Image
import scala.ref.WeakReference

/**
 * An image wrapper that keeps a weak reference to the image.
 * 
 * If the image is requested after it was collected, it is re-retrieved, this allow for efficent caching of images while specially keeping
 * the visible ones, and allowing the rest to be collected.
 */
class WeakImage(val imageLocation: String,
                val requestedWidth: Double = 0, val requestedHeight: Double = 0,
                val preserveRatio: Boolean = true, val smooth: Boolean = true,
                val backgroundLoading: Boolean = true) {
  private[this] var imageReference = WeakReference[Image](null)
  def get: Image = imageReference.get.getOrElse {
    val res = new Image(imageLocation, requestedWidth, requestedHeight, preserveRatio, smooth, backgroundLoading)
    imageReference = WeakReference(res)
    res
  }
}
