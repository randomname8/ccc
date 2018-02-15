package ccc

import javafx.fxml.FXMLLoader
import javafx.scene.control.{Control, TextArea, ScrollPane, TitledPane, Button, ListCell}
import javafx.scene.image.Image
import javafx.scene.layout.{VBox, Pane}
import javafx.scene.web.WebView
import javafx.stage.Popup
import javafx.stage.PopupWindow

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
      
      val container = visual.getParent.asInstanceOf[Pane]
      val visualScrollPane = new ScrollPane(visual)
      visualScrollPane.fitToWidth = true
      visualScrollPane.fitToHeight = true
      val previewTitledPane = new TitledPane("preview", visualScrollPane).modify(_.styleClass add "chat-preview-title-pane")
      container.children.set(container.children.indexOf(visual), previewTitledPane)
      
      visualScrollPane.prefHeightProperty bind textArea.heightProperty
      
      textArea.textProperty foreach { s =>
        val nodes = if (s == null || s.isEmpty) Seq.empty
        else  MarkdownRenderer.render(util.DiscordMarkdown adaptToMarkdown s.replace("\n", "\n\n"),
                                      visual.widthProperty, webViewCache.get _, imagesCache(_).get, emojiProvider)
        
        visual.children.forEach { case v: WebView => webViewCache.takeBack(v); case _ => }
        
        visual.children.clear
        visual.children.addAll(nodes:_*)
      }
      
      val completer = util.AutoCompleter.install(textArea) { (text, word, index) =>
        util.EmojiOne.emojiLookup.keysIterator.filter(_ startsWith word).to[Vector]
      }
      completer.completionsList.cellFactory = _ => new ListCell[String] {
        this.getStyleClass add "emoji-autocompletion-cell"
        val emoji = new Pane()
        emoji.styleClass add "emoji-autocompletion-cell-image"
        setMaxWidth(Double.MaxValue)
        override def updateItem(item: String, empty: Boolean): Unit = {
          super.updateItem(item, empty)
          if (!empty && item != null) {
            setText(item)
            emoji.background = imageBackground(emojiProvider(item))
            setGraphic(emoji)
          } else {
            setText(null)
            setGraphic(null)
          }
        }
      }
      

      val popup = new Popup
      popup.autoHide = true
      val emojiPicker = new EmojiPicker(emojiProvider)
      popup.content.add(emojiPicker)
      val emojiPickerButton = nodeRoot.lookup(".emoji-picker-button").asInstanceOf[Button]
      emojiPickerButton.onAction = _ => {
        popup.setAnchorLocation(PopupWindow.AnchorLocation.CONTENT_BOTTOM_LEFT)
        val pos = emojiPickerButton.localToScreen(0, 0)
        popup.show(emojiPickerButton, pos.getX, pos.getY)
      }
      
      emojiPicker.onIconPicked = evt => {
        textArea.appendText(evt.emoji + " ")
        popup.hide()
      }
      
      nodeRoot
    }
  }
}
