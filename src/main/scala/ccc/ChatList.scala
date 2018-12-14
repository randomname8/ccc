package ccc

import java.time.LocalDateTime
import java.time.format.{DateTimeFormatter, FormatStyle}
import javafx.application.HostServices
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
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
                              val messageDate: Message => LocalDateTime) extends Control {
  getStyleClass.add("chat-list")
  val items = FXCollections.observableList(new java.util.LinkedList[ChatBox[User, Message]]())
  val itemsScala = items.asScala
  
  val messageControlsFactory = new SimpleObjectProperty[Message => Seq[Node]](this, "additionalMessageControlsFactory", _ => Seq.empty)
  val messageRenderFactory = new SimpleObjectProperty[(Message, () => util.VlcMediaPlayer) => Seq[Node]](this, "additionalMessageRenderFactory", (msg, _) => 
    Seq(new TextFlow(new Text(messageContent(msg)))))
  val userNameNodeFactory = new SimpleObjectProperty[User => Node](this, "userNameNodeFactory")
  val chatBoxCustomizer = new SimpleObjectProperty[(ChatBox[User, Message], ChatBoxCell) => Unit](this, "userPictureCustomizer", (chatbox, cell) => {
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
                cell.hoverProperty.removeListener(hoverListener)
                cell.itemProperty.removeListener(itemChangedSelfRemover)
              }
            }
            cell.hoverProperty.addListener(hoverListener)
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
  
  val textSelectionSupport = new TextSelectionSupport
  
  {
    import javafx.scene.input._
    this.addEventHandler[KeyEvent](KeyEvent.KEY_PRESSED, evt => {
        if (evt.getCode == KeyCode.C && evt.isShortcutDown) {
          val selection = textSelectionSupport.selection.get
          val text = selection collect { case Right(s) => s } mkString ""
          if (text.nonEmpty)
            Clipboard.getSystemClipboard.setContent(new ClipboardContent().tap(_ putString text))
        }
      })
  }
  
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
  
  trait ChatBoxCell extends Cell[ChatBox[User, Message]] {
    def avatarPane: Pane
    def userLabel: Label
    def dateLabel: Label
    def entriesVBox: VBox
  }

  
  override def createDefaultSkin: Skin[ChatList[User, Message]] = ListSkin
  
  private object VboxSkin extends Skin[ChatList[User, Message]] {
    override def dispose = ()
    override def getSkinnable = ChatList.this
    override val getNode = new ScrollPane(new VBox().tap { vbox => 
        vbox.setFillWidth(true)
//        val cells = getItems.mapLazy { cb => 
//          val res = new ChatBoxListCell()
//          res.setMaxHeight(Region.USE_PREF_SIZE)
//          res.setItem(cb)
//          res.updateItem(cb, false)
//          res
//        }
//        vbox.getChildren.addAll(cells)
//        cells.addListener({ evt =>
//            val children = vbox.getChildren
//            while (evt.next) {
//              if (evt.wasPermutated || evt.wasReplaced) children.setAll(cells)
//              else {
//                evt.getRemoved forEach (children.remove(_))
//                evt.getAddedSubList forEach (children.add(_))
//              }
//            }
//          }: ListChangeListener[ChatBoxListCell])
      
      }).tap(_.setFitToWidth(true))
    
  }
  private object ListSkin extends Skin[ChatList[User, Message]] {
    def dispose = ()
    def getSkinnable = ChatList.this
    val getNode = new ListView[ChatBox[User, Message]]().tap { view =>
      view.setItems(items)
      view.getSelectionModel setSelectionMode SelectionMode.MULTIPLE
      view.setCellFactory(_ => new ChatBoxListCell())
      view.getStyleClass addAll getSkinnable.getStyleClass
      
      //check when the skin is set, in order to retrieve the VirtualFlow and attach a TextFlowSelectionSupport to it
      view.skinProperty.addListener(JfxUtils.onceChangeListener[Skin[_]] { (_, skin) =>
          val vf = skin.getNode.lookup(".virtual-flow").asInstanceOf[VirtualFlow[_]]
          textSelectionSupport.rootNode set vf
        })
      
    }
    
    private val messagesDateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
    class ChatBoxListCell private[ChatList]() extends ListCell[ChatBox[User, Message]] with ChatBoxCell {
      val boxEntryComponent = new ui.ChatBoxEntryComponent()
      val pane = boxEntryComponent.component
      val avatarPane = boxEntryComponent.avatarPane.tap(
        _.setOnMouseClicked(evt => if (evt.getButton == MouseButton.MIDDLE && getItem != null) {
            hostServices.showDocument(getItem.avatar.imageLocation)
          }))
      val userLabel = boxEntryComponent.userLabel
      val dateLabel = boxEntryComponent.chatDateLabel
      val entriesVBox = boxEntryComponent.entries
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
      override def updateItem(item: ChatBox[User, Message], empty: Boolean): Unit = {
        super.updateItem(item, empty)
        if (item == lastItem) return;
        lastItem = item
        localMediaPlayers foreach (_.dispose())
        localMediaPlayers = Vector.empty
      
        if (!empty && item.messages.nonEmpty) {
          chatBoxCustomizer.get.apply(item, this)
          Option(userNameNodeFactory.get).fold(boxEntryComponent.userLabel setText userDisplayName(item.user))(nodeFactory =>
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
        val chatMessageComponent = new ui.ChatMessageComponent()
        val renderedMarkdown = 
          try messageRenderFactory.get()(msg, renderContext)
          catch { case ex@(_:ExceptionInInitializerError | _:NoClassDefFoundError)  => Seq(new javafx.scene.text.Text("Failed rendering message: " + ex).tap(_.setFill(javafx.scene.paint.Color.RED))) }
        renderedMarkdown foreach { n =>
          n match {
            case r: Region => r.maxWidthProperty bind maxWidth
            case r: WebView => r.maxWidthProperty bind maxWidth
            case _ => 
          }
          chatMessageComponent.chatMessages.getChildren.add(n)
        }
      
        chatMessageComponent.chatMessageControls.visibleProperty bind chatMessageComponent.component.hoverProperty
      
        Option(messageControlsFactory.get).foreach(_(msg) foreach chatMessageComponent.chatMessageControls.getChildren.add)
      
        val ShowSourceMode = new Tooltip("Show source")
        val HideSourceMode = new Tooltip("Hide source")
        chatMessageComponent.chatMessageControls.getChildren add new Button("🗏").tap { button =>
          button setTooltip ShowSourceMode
          button setOnAction { evt =>
            chatMessageComponent.chatMessages.getChildren.clear()
            button.getTooltip match {
              case ShowSourceMode =>
                chatMessageComponent.chatMessages.getChildren add new TextArea(messageContent(msg)).tap { ta => 
                  ta setEditable false
                  ta setWrapText true
                  val bounds = JfxUtils.computeTextBounds(ta.getText, ta.getFont)
                  ta.setPrefSize(bounds.getWidth, bounds.getHeight)
                }
                button setTooltip HideSourceMode
              case HideSourceMode =>
                renderedMarkdown foreach chatMessageComponent.chatMessages.getChildren.add
                button setTooltip ShowSourceMode
              
            }
          }
        }
        chatMessageComponent.chatMessageControls.getChildren add new Button("🗐").tap { b =>
          b setTooltip new Tooltip("Copy")
          b setOnAction { evt => Clipboard.getSystemClipboard setContent new ClipboardContent().tap(_.putString(messageContent(msg)))}
        }
      
        chatMessageComponent.component
      }
    }
  }
}
