package ccc

import java.time.LocalDateTime
import java.time.format.{DateTimeFormatter, FormatStyle}
import javafx.application.HostServices
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.control._
import javafx.scene.control.skin.VirtualFlow
import javafx.scene.input.{Clipboard, ClipboardContent, MouseButton}
import javafx.scene.layout._
import javafx.scene.text.{Text, TextFlow}
import javafx.scene.web.WebView
import scala.collection.JavaConverters._
import tangerine._

object ChatList {
  case class ChatBox[User, Message](user: User, avatar: util.WeakImage, messages: Vector[Message])
}
import ChatList._
class ChatList[User, Message](val hostServices: HostServices,
                              val userDisplayName: User => String,
                              val messageContent: Message => String,
                              val messageDate: Message => LocalDateTime) extends ListView[ChatBox[User, Message]] {
  getStyleClass.add("chat-list")
  setItems(FXCollections.observableList(new java.util.LinkedList()))
  val itemsScala = getItems.asScala
  
  val messageControlsFactory = new SimpleObjectProperty[Message => Seq[Node]](this, "additionalMessageControlsFactory", _ => Seq.empty)
  val messageRenderFactory = new SimpleObjectProperty[(Message, () => util.VlcMediaPlayer) => Seq[Node]](this, "additionalMessageRenderFactory", (msg, _) => 
    Seq(new TextFlow(new Text(messageContent(msg)))))
  val userNameNodeFactory = new SimpleObjectProperty[User => Node](this, "userNameNodeFactory")
  val chatBoxCustomizer = new SimpleObjectProperty[(ChatBox[User, Message], ChatBoxListCell) => Unit](this, "userPictureCustomizer", (chatbox, cell) => {
      cell.avatarPane.setBackground(null)
      chatbox.avatar.onRetrieve { i =>
        def configureAvatar() = if (chatbox == cell.getItem) {
          if (util.JfxUtils.isAnimated(i)) {
            val snap = util.JfxUtils.snapshot(i)
            cell.avatarPane setBackground imageBackground(snap)
                
            lazy val hoverListener: ChangeListener[java.lang.Boolean] = { (_, _, b) =>
              if (b) cell.avatarPane setBackground imageBackground(i)
              else cell.avatarPane setBackground imageBackground(snap)
            }
            lazy val itemChangedSelfRemover: ChangeListener[ChatBox[User, Message]] = { (_, _, i) => if (i != chatbox) {
                cell.pane.hoverProperty.removeListener(hoverListener)
                cell.itemProperty.removeListener(itemChangedSelfRemover)
              }
            }
            cell.pane.hoverProperty.addListener(hoverListener)
            cell.itemProperty.addListener(itemChangedSelfRemover)
                
          } else cell.avatarPane setBackground imageBackground(i)
        }
        
        if (i.getProgress == 1) configureAvatar()
        else {
          i.progressProperty foreach {
            case n if n.doubleValue == 1 => configureAvatar()
            case _ =>
          }
        }
      }
    })
//    pane setBackground imageBackground(avatar.get))
  getSelectionModel setSelectionMode SelectionMode.MULTIPLE
  
  setCellFactory(_ => new ChatBoxListCell())
  
  val textSelectionSupport = new TextSelectionSupport
  
  //check when the skin is set, in order to retrieve the VirtualFlow and attach a TextFlowSelectionSupport to it
  skinProperty.addListener(JfxUtils.onceChangeListener[Skin[_]] { (_, skin) => 
      val vf = skin.getNode.lookup(".virtual-flow").asInstanceOf[VirtualFlow[_]]
      textSelectionSupport.rootNode set vf
      
      import javafx.scene.input._
      this.addEventHandler[KeyEvent](KeyEvent.KEY_PRESSED, evt => {
          if (evt.getCode == KeyCode.C && evt.isShortcutDown) {
            val selection = textSelectionSupport.selection.get
            println("Current selection " + selection)
            val text = selection collect { case Right(s) => s } mkString ""
            if (text.nonEmpty)
              Clipboard.getSystemClipboard.setContent(new ClipboardContent().tap(_ putString text))
          }
        })
    })
  
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
  class ChatBoxListCell private[ChatList]() extends ListCell[ChatBox[User, Message]] {
    val pane = FXMLLoader.load[Pane](getClass.getResource("/chat-box-entry.fxml"))
    val avatarPane = pane.lookup(".avatar-pane").asInstanceOf[Pane].tap(
      _.setOnMouseClicked(evt => if (evt.getButton == MouseButton.MIDDLE && getItem != null) {
          hostServices.showDocument(getItem.avatar.imageLocation)
        }))
    val userLabel = pane.lookup(".user-label").asInstanceOf[Label].tap(_ setText "")
    val dateLabel = pane.lookup(".chat-date-label").asInstanceOf[Label].tap(_ setText "")
    val entriesVBox = pane.lookup(".entries-vbox").asInstanceOf[VBox]
    setGraphic(pane)
    private[this] var lastItem: ChatBox[User, Message] = _
    private[this] var localMediaPlayers = Vector.empty[util.VlcMediaPlayer] //track the players used by this item in order to give them back to the pool later
    private[this] val maxWidth: ObservableValue[_ <: Number] = Bindings.subtract(ChatList.this.widthProperty, avatarPane.widthProperty).map(_.doubleValue - 100)
    private[this] val renderContext: () => util.VlcMediaPlayer = () => {val r = new util.VlcMediaPlayer; localMediaPlayers :+= r; r}
//    val renderMessage = {
//      val renderPartial = (MarkdownRenderer.render(_: String, emojiProvider, markdownNodeFactory)(renderContext))
//      if (messageFormatter.get != null) renderPartial compose messageFormatter.get()
//      else renderPartial compose messageContent
//    }
    override protected def updateItem(item: ChatBox[User, Message], empty: Boolean): Unit = {
      super.updateItem(item, empty)
      if (item == lastItem) return;
      lastItem = item
      localMediaPlayers foreach (_.dispose())
      localMediaPlayers = Vector.empty
      
      if (!empty && item.messages.nonEmpty) {
        chatBoxCustomizer.get.apply(item, this)
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
      val renderedMarkdown = 
        try messageRenderFactory.get()(msg, renderContext)
      catch { case ex@(_:ExceptionInInitializerError | _:NoClassDefFoundError)  => Seq(new javafx.scene.text.Text("Failed rendering message: " + ex).tap(_.setFill(javafx.scene.paint.Color.RED))) }
      renderedMarkdown foreach { n =>
//        n match {
//          case tf: TextFlow => new util.TextFlowSelectionSupport(tf)
//          case _ =>
//        }
        n match {
          case r: Region => r.maxWidthProperty bind maxWidth
          case r: WebView => r.maxWidthProperty bind maxWidth
          case _ => 
        }
        messageContainer.getChildren.add(n)
      }
      
      val controlsPane = chatMessagePane.lookup(".chat-message-controls-pane").asInstanceOf[Pane]
      controlsPane.visibleProperty bind chatMessagePane.hoverProperty
      
      Option(messageControlsFactory.get).foreach(_(msg) foreach controlsPane.getChildren.add)
      
      val ShowSourceMode = new Tooltip("Show source")
      val HideSourceMode = new Tooltip("Hide source")
      controlsPane.getChildren add new Button("🗏").tap { button =>
        button setTooltip ShowSourceMode
        button setOnAction { evt =>
          messageContainer.getChildren.clear()
          button.getTooltip match {
            case ShowSourceMode =>
              messageContainer.getChildren add new TextArea(messageContent(msg)).tap { ta => 
                ta setEditable false
                ta setWrapText true
                val bounds = JfxUtils.computeTextBounds(ta.getText, ta.getFont)
                ta.setPrefSize(bounds.getWidth, bounds.getHeight)
              }
              button setTooltip HideSourceMode
            case HideSourceMode =>
              renderedMarkdown foreach messageContainer.getChildren.add
              button setTooltip ShowSourceMode
              
          }
        }
      }
      controlsPane.getChildren add new Button("🗐").tap { b =>
        b setTooltip new Tooltip("Copy")
        b setOnAction { evt => Clipboard.getSystemClipboard setContent new ClipboardContent().tap(_.putString(messageContent(msg)))}
      }
      
      chatMessagePane
    }
  }
}
