package ccc
package util

import javafx.beans.binding.BooleanBinding
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableBooleanValue
import javafx.geometry.Bounds
import javafx.scene.{Node, Scene}
import javafx.scene.text.Font
import javafx.scene.text.Text
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
      
        override def computeValue(): Boolean = window != null && window.showing
        
        override def onInvalidating(): Unit = {
          //check if transitioning from no-scene to scene
          if (scene == null && node.scene != null) {
            scene = node.scene
            bind(node.scene.windowProperty)
            if (node.scene.window != null) {
              if (window != null) unbind(window.showingProperty)
              window = node.scene.window
              bind(node.scene.window.showingProperty)
            }
          } 
          //check if transitioning from scene to no-scene
          if (node.scene == null && scene != null) {
            unbind(scene.windowProperty)
            if (window != null) {
              unbind(window.showingProperty)
              window = null
            }
            scene = null
          }
          
          if (node.scene != null) {
            //check if transitioning from window to no-window
            if (window != null && node.scene.window == null) {
              unbind(window.showingProperty)
              window = null
            } 
            // check if transitioning from no-window to window
            if (window == null && node.scene.window != null) {
              bind(window.showingProperty)
            }
          }
        }
      }
      node.getProperties.put(showingPropertyKey, property)
    }
    property
  }
}
