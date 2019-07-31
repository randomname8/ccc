package ccc.util

import java.{util => ju}
import java.util.LinkedHashMap
import scala.jdk.CollectionConverters._

object LruMap {

  def apply[K, V](maxSize: Int) = {
    new LinkedHashMap[K, V](
      16, /* initial capacity */
      0.75f, /* load factor */
     true /* access order (as opposed to insertion order) */
    ) {
      override protected def removeEldestEntry(eldest: ju.Map.Entry[K, V]): Boolean = {
        this.size() > maxSize
      }
    }.asScala
  }
}