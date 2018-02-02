package ccc.util

/**
 * An object pool were objects are weakly referenced, so when getting an instance, a new one might have to be allocated if all the stored
 * ones got reclaimed
 */
class WeakObjectPool[T](instantiator: () => T) {
  private[this] val pool = new scala.collection.mutable.WeakHashMap[T, Unit]()
  /**
   * get an instance from the pool, instantiating one if needed.
   */
  def get: T = {
    val res = pool.headOption.fold(instantiator())(_._1)
    pool -= res //make sure we remove it from the pool
    res
  }
  /**
   * return an instance to the pool (after calling this method, callers should not retain references to the instance in order for it to be
   * available for GC)
   */
  def takeBack(t: T): Unit = pool(t) = ()
}
