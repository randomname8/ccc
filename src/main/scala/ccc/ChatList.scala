package ccc

import java.time.LocalDateTime
import java.time.format.{DateTimeFormatter, FormatStyle}
import javafx.application.HostServices
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.control._
import javafx.scene.image.Image
import javafx.scene.input.{Clipboard, ClipboardContent, MouseButton}
import javafx.scene.layout._
import javafx.scene.web.WebView
import scala.collection.JavaConverters._

object ChatList {
  case class ChatBox[User, Message](user: User, avatar: util.WeakImage, messages: Vector[Message])
}
import ChatList._
class ChatList[User, Message](val hostServices: HostServices,
                              val markdownNodeFactory: MarkdownRenderer.NodeFactory,
                              val emojiProvider: Map[String, Image],
                              val userDisplayName: User => String,
                              val messageContent: Message => String,
                              val messageDate: Message => LocalDateTime) extends ListView[ChatBox[User, Message]] {
  getStyleClass.add("chat-list")
  setItems(FXCollections.observableList(new java.util.LinkedList()))
  val itemsScala = getItems.asScala
  
  val additionalMessageControlsFactory = new SimpleObjectProperty[Message => Seq[Node]](this, "additionalMessageControlsFactory", _ => Seq.empty)
  val additionalMessageRenderFactory = new SimpleObjectProperty[(Message, MarkdownRenderer.RenderContext) => Seq[Node]](this, "additionalMessageRenderFactory", (_, _) => Seq.empty)
  val messageFormatter = new SimpleObjectProperty[Message => String](this, "messageFormatter")
  val userNameNodeFactory = new SimpleObjectProperty[User => Node](this, "userNameNodeFactory")
  getSelectionModel setSelectionMode SelectionMode.MULTIPLE
  
  setCellFactory(_ => new ChatBoxListCell())
  
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
  def removeEntry(user: User, message: Message): Unit = {
    itemsScala.iterator.find(_.messages contains message) foreach { box =>
      val updated = box.copy(messages = box.messages.filterNot(message.==))
      if (updated.messages.isEmpty) itemsScala -= updated
      else itemsScala.update(itemsScala indexOf box, updated)
    }
  }
  
  private val messagesDateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
  private class ChatBoxListCell extends ListCell[ChatBox[User, Message]] {
    val pane = FXMLLoader.load[Pane](getClass.getResource("/chat-box-entry.fxml"))
    val avatarPane = pane.lookup(".avatar-pane").asInstanceOf[Pane].modify(
      _.setOnMouseClicked(evt => if (evt.getButton == MouseButton.MIDDLE && getItem != null) {
          hostServices.showDocument(getItem.avatar.imageLocation)
        }))
    val userLabel = pane.lookup(".user-label").asInstanceOf[Label].modify(_ setText "")
    val dateLabel = pane.lookup(".chat-date-label").asInstanceOf[Label].modify(_ setText "")
    val entriesVBox = pane.lookup(".entries-vbox").asInstanceOf[VBox]
    setGraphic(pane)
    private[this] var lastItem: ChatBox[User, Message] = _
    private[this] var localMediaPlayers = Vector.empty[util.VlcMediaPlayer] //track the players used by this item in order to give them back to the pool later
    private[this] val maxWidth: ObservableValue[_ <: Number] = Bindings.subtract(ChatList.this.widthProperty, avatarPane.widthProperty).map(_.doubleValue - 100)
    private[this] val renderContext = MarkdownRenderer.RenderContext(() => {val r = new util.VlcMediaPlayer; localMediaPlayers :+= r; r})
    val renderMessage = {
      val renderPartial = (MarkdownRenderer.render(_: String, emojiProvider, markdownNodeFactory)(renderContext))
      if (messageFormatter.get != null) renderPartial compose messageFormatter.get()
      else renderPartial compose messageContent
    }
    override protected def updateItem(item: ChatBox[User, Message], empty: Boolean): Unit = {
      super.updateItem(item, empty)
      if (item == lastItem) return;
      lastItem = item
      localMediaPlayers foreach (_.dispose())
      localMediaPlayers = Vector.empty
      
      if (!empty && item.messages.nonEmpty) {
        avatarPane setBackground imageBackground(item.avatar.get)
        Option(userNameNodeFactory.get).fold(userLabel setText userDisplayName(item.user))(nodeFactory =>
          userLabel setGraphic nodeFactory(item.user))
        
        val date = messageDate(item.messages.head)
        dateLabel setText date.format(messagesDateTimeFormat)
        entriesVBox.getChildren.clear()
        
        for (msg <- item.messages) entriesVBox.getChildren add messageBox(msg)

      } else {
        avatarPane setBackground null
        userLabel setText null
        userLabel setGraphic null
        dateLabel setText null
        entriesVBox.getChildren.clear()
      }
    }
    
    def messageBox(msg: Message): Pane = {
      val chatMessagePane = FXMLLoader.load[Pane](getClass.getResource("/chat-message.fxml"))
      val messageContainer = chatMessagePane.lookup(".chat-message").asInstanceOf[Pane]
      val renderedMarkdown = renderMessage(msg) ++ additionalMessageRenderFactory.get()(msg, renderContext)
      renderedMarkdown foreach { n =>
        n match {
          case r: Region => r.maxWidthProperty bind maxWidth
          case r: WebView => r.maxWidthProperty bind maxWidth
          case _ => 
        }
        messageContainer.getChildren.add(n)
      }
      
      val controlsPane = chatMessagePane.lookup(".chat-message-controls-pane").asInstanceOf[Pane]
      controlsPane.visibleProperty bind chatMessagePane.hoverProperty
      
      Option(additionalMessageControlsFactory.get).foreach(_(msg) foreach controlsPane.getChildren.add)
      
      val ShowSourceMode = new Tooltip("Show source")
      val HideSourceMode = new Tooltip("Hide source")
      controlsPane.getChildren add new Button("🗏").modify(
        _ setTooltip ShowSourceMode,
        button => button setOnAction { evt =>
          messageContainer.getChildren.clear()
          button.getTooltip match {
            case ShowSourceMode =>
              messageContainer.getChildren add new TextArea(messageContent(msg)).modify(
                _ setEditable false, _ setWrapText true,
                ta => {
                  val bounds = util.JfxUtils.computeTextBounds(ta.getText, ta.getFont)
                  ta.setPrefSize(bounds.getWidth, bounds.getHeight)
                })
              button setTooltip HideSourceMode
            case HideSourceMode =>
              renderedMarkdown foreach messageContainer.getChildren.add
              button setTooltip ShowSourceMode
              
          }
        })
      controlsPane.getChildren add new Button("🗐").modify(
        _ setTooltip new Tooltip("Copy"),
        _ setOnAction { evt => Clipboard.getSystemClipboard setContent new ClipboardContent().modify(_.putString(messageContent(msg)))})
      
      chatMessagePane
    }
  }
}
