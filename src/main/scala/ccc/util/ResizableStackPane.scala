package ccc
package util

import javafx.scene.Node
import javafx.scene.input.{MouseEvent, MouseButton}
import javafx.scene.layout.StackPane

class ResizableStackPane extends StackPane {
  def this(node: Node) = {
    this()
    this.getChildren.add(node)
  }
  
  private case class ClickOrigin(mouseClicked: MouseEvent, width: Double, height: Double)
  private[this] var clickOrigin: ClickOrigin = _
  this setOnMousePressed { evt => if (evt.getButton == MouseButton.PRIMARY) clickOrigin = ClickOrigin(evt, getWidth, getHeight) }
  this setOnMouseDragged { evt => if (evt.isPrimaryButtonDown) {
    val dx = evt.getX - clickOrigin.mouseClicked.getX
    val dy = evt.getY - clickOrigin.mouseClicked.getY
    setPrefWidth(math.max(clickOrigin.width + dx, 100))
    setPrefHeight(math.max(clickOrigin.height + dy, 100))
  }}
}
