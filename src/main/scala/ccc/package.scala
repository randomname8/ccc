import javafx.scene.image.Image

package object ccc {
  import javafx.beans.value.ObservableValue
  import javafx.geometry._
  import javafx.beans.binding._
  import javafx.scene.Node
  import javafx.scene.control._
  import javafx.scene.layout._
  import javafx.scene.paint.Color
  
  /*****************************************
   * builder api
   *****************************************/
  
  implicit class PaneBuilder(val peer: Pane) extends AnyVal {
    def \[N <: Node](node: N): node.type = { peer.getChildren.add(node); node }
  }
  implicit class BorderPaneBuilder(val peer: BorderPane) extends AnyVal {
    def top[N <: Node](node: N): N = { peer.setTop(node); node }
    def center[N <: Node](node: N): N = { peer.setCenter(node); node }
    def left[N <: Node](node: N): N = { peer.setLeft(node); node }
    def right[N <: Node](node: N): N = { peer.setRight(node); node }
    def bottom[N <: Node](node: N): N = { peer.setBottom(node); node }
  }
  implicit class GridPaneBuilder(val peer: GridPane) extends AnyVal {
    def update[N <: Node](col: Int, row: Int, node: N): N = { peer.add(node, col, row); node }
    def update[N <: Node](col: Int, row: Int, colSpan: Int, rowSpan: Int, node: N): N = { peer.add(node, col, row); node }
  }
  implicit class ToolBarBuilder(val peer: ToolBar) extends AnyVal {
    def \[N <: Node](node: N): N = { peer.getItems.add(node); node }
  }
  implicit class MenuBarBuilder(val peer: MenuBar) extends AnyVal {
    def \[M <: Menu](node: M): M = { peer.getMenus.add(node); node }
  }
  implicit class MenuBuilder(val peer: Menu) extends AnyVal {
    def \[M <: MenuItem](node: M): M = { peer.getItems.add(node); node }
  }
  implicit class ToggleButtonBuilder(val peer: ToggleGroup) extends AnyVal {
    def \[T <: Toggle](node: T): T = { peer.getToggles.add(node); node }
  }
  
  /**********************************
   * Useful layouts
   **********************************/
  
  def hbox(nodes: Node*)(implicit spacing: Double = 10, alignment: Pos = Pos.BASELINE_LEFT, fillHeight: Boolean = false) = {
    val res = new HBox(nodes:_*)
    res.setSpacing(spacing)
    res.setAlignment(alignment)
    res.setFillHeight(fillHeight)
    res
  }
  def vbox(nodes: Node*)(implicit spacing: Double = 10, alignment: Pos = Pos.BASELINE_LEFT, fillWidth: Boolean = false) = {
    val res = new VBox(nodes:_*)
    res.setSpacing(spacing)
    res.setAlignment(alignment)
    res.setFillWidth(fillWidth)
    res
  }
  
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
  
  implicit class ObservableValueExt[T](val property: ObservableValue[T]) extends AnyVal {
    import language.existentials
    def foreach(f: T => Unit): Unit = property.addListener((_: t forSome {type t >: T}, _, v) => f(v))
    def map[U](f: T => U): Binding[U] = new ObjectBinding[U] {
      bind(property)
      override def computeValue = f(property.getValue)
    }
  }
  
  implicit class Modifier[T](val t: T) extends AnyVal {
    @inline def modify(functions: T => Any*): T = { functions foreach (_(t)); t }
  }
  
  implicit class ColorExt(val c: Color) extends AnyVal {
    def colorToWeb = "#%02X%02X%02X".format((c.getRed * 255).toInt, (c.getGreen * 255).toInt, (c.getBlue * 255).toInt)
  }
  
  implicit class SafeAbscribe[T](val t: T) extends AnyVal {
    def abscribe[U >: T]: U = t
  }
}
