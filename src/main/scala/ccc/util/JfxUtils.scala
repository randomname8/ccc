package ccc.util

import javafx.geometry.Bounds
import javafx.scene.text.Font
import javafx.scene.text.Text

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
}
