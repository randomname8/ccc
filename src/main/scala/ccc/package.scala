import javafx.application.Platform
import javafx.scene.image.Image
import scala.concurrent.ExecutionContext

package object ccc {
  import javafx.scene.Node
  import javafx.scene.control._
  import javafx.scene.layout._

  def gridPane(rows: Seq[Node]*)(implicit vgap: Double = 10, hgap: Double = 10) = new GridPane {
    setVgap(vgap)
    setHgap(hgap)
    for ((row, idx) <- rows.zipWithIndex)
      addRow(idx, row:_*)
  }
  
  def combobox[T](elems: T*) = {
    val res = new ComboBox[T]
    res.getItems.addAll(elems:_*)
    res.getSelectionModel.selectFirst()
    res
  }
  
  val FitImageToBackground = new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, true, true, true, true)
  def imageBackground(img: Image): Background =
    new Background(new BackgroundImage(img, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, FitImageToBackground))
  
  /*********************
   * MISC
   *********************/
  
  implicit class SafeAbscribe[T](private val t: T) extends AnyVal {
    def abscribe[U >: T]: U = t
  }
  
  object JavafxExecutionContext extends ExecutionContext {
    implicit val context = this
    override def execute(runnable: Runnable) = Platform.runLater(runnable)
    override def reportFailure(cause: Throwable) = ExecutionContext.defaultReporter(cause)
  }
}
