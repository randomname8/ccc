package ccc

import java.time.LocalDateTime
import java.time.format.{DateTimeFormatter, FormatStyle}
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.control._
import javafx.scene.image.Image
import javafx.scene.input.{Clipboard, ClipboardContent}
import javafx.scene.layout._
import javafx.scene.web.WebView
import scala.collection.JavaConverters._

object ChatList {
  case class ChatBox[User, Message](user: User, avatar: util.WeakImage, messages: Vector[Message])
}
import ChatList._
class ChatList[User, Message](val markdownNodeFactory: MarkdownRenderer.NodeFactory,
                              val webViewCache: util.WeakObjectPool[WebView],
                              val emojiProvider: Map[String, Image],
                              val userDisplayName: User => String,
                              val messageContent: Message => String,
                              val messageDate: Message => LocalDateTime) extends ListView[ChatBox[User, Message]] {
  this.styleClass.add("chat-list")
  this.items = FXCollections.observableList(new java.util.LinkedList())
  val itemsScala = this.items.asScala
  
  val additionalMessageControlsFactory = new SimpleObjectProperty[Message => Seq[Node]](this, "additionalMessageControlsFactory", _ => Seq.empty)
  val additionalMessageRenderFactory = new SimpleObjectProperty[(Message, ObservableValue[_ <: Number]) => Seq[Node]](this, "additionalMessageRenderFactory", (_, _) => Seq.empty)
  this.selectionModel.selectionMode = SelectionMode.MULTIPLE
  
  this.cellFactory = _ => new ChatBoxListCell()
  
  def addEntry(user: User, avatar: util.WeakImage, message: Message): Unit = {
    itemsScala.lastOption match {
      case Some(box) if box.user == user && {
          val date = messageDate(box.messages.head)
          val now = messageDate(message)
          date.getDayOfYear == now.getDayOfYear && date.isAfter(now.minusMinutes(15))
        } =>
        itemsScala -= box
        itemsScala += box.copy(messages = box.messages :+ message)
      case _ => itemsScala += ChatBox(user, avatar, Vector(message))
    }
  }
  
  private val messagesDateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
  private class ChatBoxListCell extends ListCell[ChatBox[User, Message]] {
    val pane = FXMLLoader.load[Pane](getClass.getResource("/chat-box-entry.fxml"))
    val avatarPane = pane.lookup(".avatar-pane").asInstanceOf[Pane]
    val userLabel = pane.lookup(".user-label").asInstanceOf[Label].modify(_.text = "")
    val dateLabel = pane.lookup(".chat-date-label").asInstanceOf[Label].modify(_.text = "")
    val entriesVBox = pane.lookup(".entries-vbox").asInstanceOf[VBox]
    this.graphic = pane
    private[this] var lastItem: ChatBox[User, Message] = _
    private[this] var localWebView = Vector.empty[WebView] //track the webviews used by this item in order to give them back to the pool later
    private[this] val maxWidth: ObservableValue[_ <: Number] = Bindings.subtract(ChatList.this.widthProperty, avatarPane.widthProperty).map(_.doubleValue - 100)
    val renderMessage = (MarkdownRenderer.render(_: String,
                                                 maxWidth,
                                                 () => {val r = webViewCache.get; localWebView :+= r; r},
                                                 emojiProvider, markdownNodeFactory)) compose messageContent
    override protected def updateItem(item: ChatBox[User, Message], empty: Boolean): Unit = {
      super.updateItem(item, empty)
      if (item == lastItem) return;
      lastItem = item
      //discard our stored local web views by returning them to the cache
      localWebView foreach webViewCache.takeBack
      localWebView = Vector.empty
      
      if (!empty && item.messages.nonEmpty) {
        avatarPane.background = imageBackground(item.avatar.get)
        userLabel.text = userDisplayName(item.user)
        val date = messageDate(item.messages.head)
        dateLabel.text = date.format(messagesDateTimeFormat)
        entriesVBox.children.clear()
        
        for (msg <- item.messages) entriesVBox.children add messageBox(msg)

      } else {
        avatarPane.background = null
        userLabel.text = null
        dateLabel.text = null
        entriesVBox.children.clear()
      }
    }
    
    def messageBox(msg: Message): Pane = {
      val chatMessagePane = FXMLLoader.load[Pane](getClass.getResource("/chat-message.fxml"))
      val messageContainer = chatMessagePane.lookup(".chat-message").asInstanceOf[Pane]
      val renderedMarkdown = renderMessage(msg) ++ additionalMessageRenderFactory.get()(msg, maxWidth)
      renderedMarkdown foreach messageContainer.children.add
      
      val controlsPane = chatMessagePane.lookup(".chat-message-controls-pane").asInstanceOf[Pane]
      controlsPane.visibleProperty bind chatMessagePane.hoverProperty
      
      Option(additionalMessageControlsFactory.get).foreach(_(msg) foreach controlsPane.children.add)
      
      val ShowSourceMode = new Tooltip("Show source")
      val HideSourceMode = new Tooltip("Hide source")
      controlsPane.children add new Button("🗏").modify(
        _.tooltip = ShowSourceMode,
        button => button.onAction = { evt =>
          messageContainer.children.clear()
          button.getTooltip match {
            case ShowSourceMode =>
              messageContainer.children add new TextArea(messageContent(msg)).modify(
                _.editable = false, _.wrapText = true,
                ta => {
                  val bounds = util.JfxUtils.computeTextBounds(ta.getText, ta.getFont)
                  ta.setPrefSize(bounds.getWidth, bounds.getHeight)
                })
              button.tooltip = HideSourceMode
            case HideSourceMode =>
              renderedMarkdown foreach messageContainer.children.add
              button.tooltip = ShowSourceMode
              
          }
        })
      controlsPane.children add new Button("🗐").modify(
        _.tooltip = new Tooltip("Copy"),
        _.onAction = evt => Clipboard.getSystemClipboard setContent new ClipboardContent().modify(_.putString(messageContent(msg))))
      
      chatMessagePane
    }
  }
}
