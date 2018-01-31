package ccc

import java.net.URI
import javafx.collections.FXCollections
import javafx.fxml.FXMLLoader
import javafx.scene.control._
import javafx.scene.image.{Image, ImageView}
import javafx.scene.layout._
import javafx.scene.text.{Text, TextFlow}
import scala.collection.JavaConverters._

object ChatList {
  case class ChatBox(user: String, avatar: Image, messages: Vector[String])
}
import ChatList._
class ChatList extends ListView[ChatBox] {
  this.items = FXCollections.observableList(new java.util.LinkedList())
  this.stylesheets.add("/ccc-theme.css")
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
    override protected def updateItem(item: ChatBox, empty: Boolean): Unit = {
      super.updateItem(item, empty)
      if (!empty) {
        avatarPane.cssMetaData.forEach(cms => println(cms))
        avatarPane.background = new Background(new BackgroundImage(item.avatar, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, BackgroundSize.DEFAULT))
        userLabel.text = item.user
        dateLabel.text = "a date goes here, seriously "
        entriesVBox.children.clear()
        item.messages.map(m => new TextFlow(new Text(m))) foreach entriesVBox.children.add
      } else {
        avatarPane.background = null
        userLabel.text = null
        dateLabel.text = null
        entriesVBox.children.clear()
      }
    }
  }
}
