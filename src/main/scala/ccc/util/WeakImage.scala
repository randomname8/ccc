package ccc
package util

import javafx.scene.image.Image
import scala.concurrent.Future
import scala.ref.WeakReference
import scala.util.Success

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
  @volatile private[this] var imageReference = WeakReference[Image](null)
  protected def fetchImage(): Future[Image] = Future.successful(new Image(imageLocation, requestedWidth, requestedHeight, preserveRatio, smooth, backgroundLoading))
  def get(): Future[Image] = imageReference.get match {
    case None =>
      val imageFuture = fetchImage()
      if (imageFuture.isCompleted) {
        val res = imageFuture.value.get.get
        imageReference = WeakReference(res)
        imageFuture
      } else imageFuture.andThen { case Success(im) => imageReference = WeakReference(im) }(JavafxExecutionContext)
      
    case Some(i) => Future successful i
  }
  def onRetrieve(f: Image => Unit) = {
    val imageFuture = get()
    if (imageFuture.isCompleted) f(imageFuture.value.get.get) else imageFuture.foreach(f)(JavafxExecutionContext)
  }
}
