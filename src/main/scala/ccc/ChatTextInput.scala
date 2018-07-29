package ccc

import javafx.fxml.FXMLLoader
import javafx.scene.control.{Control, TextArea, ScrollPane, TitledPane, Button, ListCell}
import javafx.scene.image.Image
import javafx.scene.layout.{VBox, Pane, Region}
import javafx.scene.web.WebView
import javafx.stage.Popup
import javafx.stage.PopupWindow

class ChatTextInput(val markdownRenderer: MarkdownRenderer,
                    val markdownNodeFactory: MarkdownRenderer.NodeFactory,
                    val emojiProvider: Map[String, Image]) extends Control {

  private[this] val nodeRoot = FXMLLoader.load[Pane](getClass.getResource("/chat-text-input.fxml"))
  val textArea = nodeRoot.lookup(".text-area").asInstanceOf[TextArea]
  textArea.textProperty foreach { t =>
    if (t != null) {
      val bounds = util.JfxUtils.computeTextBounds(t, textArea.getFont, textArea.getWidth - textArea.getFont.getSize) //take into account the paddings of the text area
      textArea.setPrefHeight(bounds.getHeight + textArea.getFont.getSize)
    } else textArea setPrefRowCount 1
  }
  //make sure the scrollpane of the text area NEVER shows
  textArea.sceneProperty.foreach(s => if (s!= null) {
      textArea.applyCss()
      textArea.lookup(".scroll-pane").asInstanceOf[ScrollPane].setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    })
  
  override protected def createDefaultSkin = Skin
  
  object Skin extends javafx.scene.control.Skin[ChatTextInput] {
    override def getSkinnable = ChatTextInput.this
    override def dispose() = {}
    override val getNode = {
      val visual = nodeRoot.lookup(".entries-vbox").asInstanceOf[VBox]
      
      val container = visual.getParent.asInstanceOf[Pane]
      val visualScrollPane = new ScrollPane(visual)
      visualScrollPane setFitToWidth true
      visualScrollPane setFitToHeight true
      val previewTitledPane = new TitledPane("preview", visualScrollPane).modify(_.getStyleClass add "chat-preview-title-pane")
      container.getChildren.set(container.getChildren.indexOf(visual), previewTitledPane)
      
      visualScrollPane.prefHeightProperty bind textArea.heightProperty
      
      var instantiatedPlayers = Vector.empty[util.VlcMediaPlayer]
      textArea.textProperty foreach { s =>
        val nodes = if (s == null || s.isEmpty) Seq.empty
        else {
          markdownRenderer.render(s.replace("\n", "\n\n"), emojiProvider, markdownNodeFactory)(
            MarkdownRenderer.RenderContext(() => {
                val res = new util.VlcMediaPlayer
                instantiatedPlayers +:= res
                res
              }))
        }
        
        instantiatedPlayers foreach (_.dispose())
        instantiatedPlayers = Vector.empty
        
        visual.getChildren.clear
        nodes foreach { n =>
          n match {
            case r: Region => r.maxWidthProperty bind visual.widthProperty
            case r: WebView => r.maxWidthProperty bind visual.widthProperty
            case _ =>
          }
          visual.getChildren add n
        }
      }
      
      val completer = util.AutoCompleter.install(textArea) { (text, word, index) =>
        util.EmojiOne.emojiLookup.keysIterator.filter(_ startsWith word).to[Vector]
      }
      completer.completionsList setCellFactory {_ => new ListCell[String] {
          this.getStyleClass add "emoji-autocompletion-cell"
          val emoji = new Pane()
          emoji.getStyleClass add "emoji-autocompletion-cell-image"
          setMaxWidth(Double.MaxValue)
          override def updateItem(item: String, empty: Boolean): Unit = {
            super.updateItem(item, empty)
            if (!empty && item != null) {
              setText(item)
              emoji setBackground imageBackground(emojiProvider(item))
              setGraphic(emoji)
            } else {
              setText(null)
              setGraphic(null)
            }
          }
        }}
      

      val popup = new Popup
      popup setAutoHide true
      val emojiPicker = new EmojiPicker(emojiProvider)
      popup.getContent.add(emojiPicker)
      val emojiPickerButton = nodeRoot.lookup(".emoji-picker-button").asInstanceOf[Button]
      emojiPickerButton.disableProperty bind textArea.disabledProperty
      emojiPickerButton setOnAction { _ => {
          popup.setAnchorLocation(PopupWindow.AnchorLocation.CONTENT_BOTTOM_LEFT)
          val pos = emojiPickerButton.localToScreen(0, 0)
          popup.show(emojiPickerButton, pos.getX, pos.getY)
        }}
      
      emojiPicker.onIconPicked = evt => {
        textArea.appendText(evt.emoji + " ")
        popup.hide()
        textArea.requestFocus()
      }
      
      nodeRoot
    }
  }
}
