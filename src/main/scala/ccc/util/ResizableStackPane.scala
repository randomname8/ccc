package ccc
package util

import javafx.scene.Node
import javafx.scene.input.{MouseEvent, MouseButton}
import javafx.scene.layout.StackPane

class ResizableStackPane extends StackPane {
  def this(node: Node) = {
    this()
    this.children.add(node)
  }
  
  private case class ClickOrigin(mouseClicked: MouseEvent, width: Double, height: Double)
  private[this] var clickOrigin: ClickOrigin = _
  this.onMousePressed = evt => if (evt.button == MouseButton.PRIMARY) clickOrigin = ClickOrigin(evt, getWidth, getHeight)
  this.onMouseDragged = evt => if (evt.isPrimaryButtonDown) {
    val dx = evt.x - clickOrigin.mouseClicked.x
    val dy = evt.y - clickOrigin.mouseClicked.y
    setPrefWidth(math.max(clickOrigin.width + dx, 100))
    setPrefHeight(math.max(clickOrigin.height + dy, 100))
  }
}
