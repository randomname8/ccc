package ccc
package util

import javafx.beans.binding.{BooleanBinding, ObjectBinding}
import javafx.beans.value.{ObservableBooleanValue, ObservableValue}
import javafx.geometry.Bounds
import javafx.scene.{Node, Scene}
import javafx.scene.text.{Font, Text}
import javafx.stage.Window

object JfxUtils {
  //copied from com.sun.javafx.scene.control.skin.Utils
  
  //single text instance, it's not a problem because you shouldn't use this method concurrently, as per JavaFX normal rules
  private val helper = new Text()
  private val DEFAULT_WRAPPING_WIDTH = helper.getWrappingWidth()
  private val DEFAULT_LINE_SPACING = helper.getLineSpacing()
  private val DEFAULT_TEXT = helper.getText()
  
  def computeTextBounds(text: String, font: Font, maxWidth: Double = 0, lineSpacing: Double = 0): Bounds = {
    helper.setText(text)
    helper.setFont(font)
    helper.setWrappingWidth(maxWidth)
    helper.setLineSpacing(lineSpacing)
    
    val res = helper.getLayoutBounds()
    
    // RESTORE STATE
    helper.setWrappingWidth(DEFAULT_WRAPPING_WIDTH)
    helper.setLineSpacing(DEFAULT_LINE_SPACING)
    helper.setText(DEFAULT_TEXT)
    
    res
  }
  
  private[this] object showingPropertyKey
  def showingProperty(node: Node): ObservableBooleanValue = {
    var property = node.getProperties.get(showingPropertyKey).asInstanceOf[ObservableBooleanValue]
    if (property == null) {
      property = new BooleanBinding {
        var scene: Scene = _
        var window: Window = _
        bind(node.sceneProperty)
        onInvalidating()
      
        override def computeValue(): Boolean = window != null && window.isShowing
        
        override def onInvalidating(): Unit = {
          //check if transitioning from no-scene to scene
          if (scene == null && node.getScene != null) {
            scene = node.getScene
            bind(node.getScene.windowProperty)
            if (node.getScene.getWindow != null) {
              if (window != null) unbind(window.showingProperty)
              window = node.getScene.getWindow
              bind(node.getScene.getWindow.showingProperty)
            }
          } 
          //check if transitioning from scene to no-scene
          if (node.getScene == null && scene != null) {
            unbind(scene.windowProperty)
            if (window != null) {
              unbind(window.showingProperty)
              window = null
            }
            scene = null
          }
          
          if (node.getScene != null) {
            //check if transitioning from window to no-window
            if (window != null && node.getScene.getWindow == null) {
              unbind(window.showingProperty)
              window = null
            } 
            // check if transitioning from no-window to window
            if (window == null && node.getScene.getWindow != null) {
              bind(window.showingProperty)
            }
          }
        }
      }
      node.getProperties.put(showingPropertyKey, property)
    }
    property
  }
  
  def Binding[T](values: ObservableValue[_]*)(compute: () => T): ObjectBinding[T] = new ObjectBinding[T] {
    bind(values:_*)
    override def computeValue = compute()
  }
}
