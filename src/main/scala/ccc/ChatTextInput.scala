package ccc

import javafx.fxml.FXMLLoader
import javafx.scene.control.{Control, TextArea}
import javafx.scene.image.Image
import javafx.scene.layout.{VBox, Pane}
import javafx.scene.web.WebView

class ChatTextInput(val webViewCache: util.WeakObjectPool[WebView],
                    val imagesCache: collection.Map[String, util.WeakImage],
                    val emojiProvider: Map[String, Image]) extends Control {

  private[this] val nodeRoot = FXMLLoader.load[Pane](getClass.getResource("/chat-text-input.fxml"))
  val textArea = nodeRoot.lookup(".text-area").asInstanceOf[TextArea]
  textArea.textProperty foreach { t =>
    if (t != null && t.nonEmpty) textArea.prefRowCount = t.count(_ == '\n') + 1
    else textArea.prefRowCount = 1
  }
  override protected def createDefaultSkin = Skin
  
  object Skin extends javafx.scene.control.Skin[ChatTextInput] {
    override def getSkinnable = ChatTextInput.this
    override def dispose() = {}
    override val getNode = {
      val visual = nodeRoot.lookup(".entries-vbox").asInstanceOf[VBox]
      
      visual.minHeightProperty bind textArea.heightProperty
      visual.minWidthProperty bind nodeRoot.widthProperty
      
      textArea.textProperty foreach { s => 
        val nodes = if (s == null || s.isEmpty) Seq.empty 
        else  MarkdownRenderer.render(s.replace("\n", "\n\n"), visual.widthProperty, webViewCache.get _, imagesCache(_).get, emojiProvider)
        
        visual.children.forEach { case v: WebView => webViewCache.takeBack(v); case _ => }
        
        visual.children.clear
        visual.children.addAll(nodes:_*)
      }
      
      nodeRoot
    }
  }
}
