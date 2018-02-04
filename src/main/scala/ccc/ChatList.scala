package ccc

import javafx.beans.binding.Bindings
import javafx.collections.FXCollections
import javafx.fxml.FXMLLoader
import javafx.scene.control._
import javafx.scene.image.Image
import javafx.scene.layout._
import javafx.scene.web.WebView
import scala.collection.JavaConverters._

object ChatList {
  case class ChatBox(user: String, avatar: Image, messages: Vector[String])
}
import ChatList._
class ChatList(val webViewCache: util.WeakObjectPool[WebView],
               val imagesCache: collection.Map[String, util.WeakImage],
               val emojiProvider: Map[String, Image]) extends ListView[ChatBox] {
  this.styleClass.add("chat-list")
  this.items = FXCollections.observableList(new java.util.LinkedList())
  private[this] val itemsScala = this.items.asScala
  
  this.cellFactory = _ => new ChatBoxListCell()
  
  def addEntry(user: String, avatar: Image, message: String): Unit = {
    itemsScala.lastOption match {
      case Some(box) if box.user == user =>
        itemsScala -= box
        itemsScala += box.copy(messages = box.messages :+ message)
      case _ => itemsScala += ChatBox(user, avatar, Vector(message))
    }
  }
  
  private class ChatBoxListCell extends ListCell[ChatBox] {
    val pane = FXMLLoader.load[Pane](getClass.getResource("/chat-box-entry.fxml"))
    val avatarPane = pane.lookup(".avatar-pane").asInstanceOf[Pane]
    val userLabel = pane.lookup(".user-label").asInstanceOf[Label].modify(_.text = "")
    val dateLabel = pane.lookup(".chat-date-label").asInstanceOf[Label].modify(_.text = "")
    val entriesVBox = pane.lookup(".entries-vbox").asInstanceOf[VBox]
    this.graphic = pane
    private[this] var lastItem: ChatBox = _
    private[this] var localWebView = Vector.empty[WebView] //track the webviews used by this item in order to give them back to the pool later
    val renderMessage = MarkdownRenderer.render(_: String,
                                                Bindings.subtract(ChatList.this.widthProperty, avatarPane.widthProperty).map(_.doubleValue - 100),
                                                webViewCache.get _,
                                                imagesCache(_).get,
                                                emojiProvider)
    override protected def updateItem(item: ChatBox, empty: Boolean): Unit = {
      super.updateItem(item, empty)
      if (item == lastItem) return;
      lastItem = item
      //discard our stored local web views by returning them to the cache
      localWebView foreach webViewCache.takeBack
      localWebView = Vector.empty
      
      if (!empty && item.messages.nonEmpty) {
        avatarPane.background = new Background(new BackgroundImage(item.avatar, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, BackgroundSize.DEFAULT))
        userLabel.text = item.user
        dateLabel.text = "yyyy/mm/dd"
        entriesVBox.children.clear()
        
        item.messages.map(renderMessage).flatten foreach { node =>
          entriesVBox.children.add(node)
          node match {
            case v: WebView => localWebView :+= v
            case _ =>
          }
        }
      } else {
        avatarPane.background = null
        userLabel.text = null
        dateLabel.text = null
        entriesVBox.children.clear()
      }
    }
  }
}
