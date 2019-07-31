package ccc
package ui

import java.time.format.{DateTimeFormatter, FormatStyle}
import javafx.beans.value.ObservableValue
import javafx.collections.ListChangeListener
import javafx.geometry.Pos
import javafx.scene.control._
import javafx.scene.input.{Clipboard, ClipboardContent, MouseButton}
import javafx.scene.layout.{Region, VBox, Pane}
import javafx.scene.web.WebView
import scala.jdk.CollectionConverters._
import scala.util.chaining._
import tangerine._, Properties._

private[ccc] class ChatListVboxSkin[User, Message](chatList: ChatList[User, Message]) extends Skin[ChatList[User, Message]]{
  def getSkinnable = chatList
  def dispose = ()
  val messagesPane = new VBox 
  messagesPane.setFillWidth(true)
  messagesPane.setAlignment(Pos.TOP_LEFT)
  val getNode = new ScrollPane(messagesPane).tap { s => s setFitToWidth true; s setFitToHeight true }
  chatList.textSelectionSupport.rootNode set messagesPane
  
  
  chatList.getProperties.put(ChatList.scrollHandlerKey, new ChatList.ScrollHandler {
      def scrollTo(index: Int) = {}
      def scrollTo(box: ChatList.ChatBox[_, _]) = {}
    })
  
//  import javafx.scene.input.MouseEvent
//  chatList.addEventHandler[MouseEvent](MouseEvent.ANY, evt => println(evt))
  
  val createCell = (startOffset: Int) => { (cb: ChatList.ChatBox[User, Message], index: Int) =>
    val res = new ChatBoxCell()
    res.setItem(cb)
    res.updateIndex(startOffset + index)
    res.updateItem(cb, false)
    res
  }.tupled
  
  chatList.itemsScala.iterator.zipWithIndex.map(createCell(0)) foreach messagesPane.getChildren.add
  
  chatList.items.addListener({ evt =>
      val children = messagesPane.getChildren
      while (evt.next) {
        if (evt.wasPermutated) {
          children.subList(evt.getFrom, evt.getTo).clear()
          children.addAll(evt.getFrom, evt.getList.asScala.slice(evt.getFrom, evt.getTo).zipWithIndex.map(createCell(evt.getFrom)).asJava)
        } else {
          if (evt.wasRemoved) children.subList(evt.getFrom, evt.getFrom + evt.getRemovedSize).clear()
          if (evt.wasAdded) children.addAll(evt.getFrom, evt.getAddedSubList.asScala.zipWithIndex.map(createCell(evt.getFrom)).asJava)
        }
      }
    }: ListChangeListener[ChatList.ChatBox[User, Message]])
  
  private lazy val messagesDateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
  class ChatBoxCell private[ChatListVboxSkin]() extends IndexedCell[ChatList.ChatBox[User, Message]] with ChatList.ChatBoxCell[User, Message] {
    val boxEntryComponent = new ui.ChatBoxEntryComponent()
    val pane = boxEntryComponent.component
    val avatarPane = boxEntryComponent.avatarPane.tap(
      _.setOnMouseClicked(evt => if (evt.getButton == MouseButton.MIDDLE && getItem != null) {
          chatList.hostServices.showDocument(getItem.avatar.imageLocation)
        }))
    val userLabel = boxEntryComponent.userLabel
    val dateLabel = boxEntryComponent.chatDateLabel
    val entriesVBox = boxEntryComponent.entries
    setGraphic(pane)
    setMaxSize(Double.MaxValue, 1) //somehow, this makes it have PREF_SIZE for maximum height
    getStyleClass add "list-cell"
    private[this] var lastItem: ChatList.ChatBox[User, Message] = _
    private[this] var localMediaPlayers = Vector.empty[util.VlcMediaPlayer] //track the players used by this item in order to give them back to the pool later
    private[this] val maxWidth: ObservableValue[_ <: Number] = chatList.widthProperty - avatarPane.widthProperty - 100
    private[this] val renderContext: () => util.VlcMediaPlayer = () => {val r = new util.VlcMediaPlayer; localMediaPlayers :+= r; r}
    override def updateItem(item: ChatList.ChatBox[User, Message], empty: Boolean): Unit = {
      super.updateItem(item, empty)
      if (item == lastItem) return;
      lastItem = item
      localMediaPlayers foreach (_.dispose())
      localMediaPlayers = Vector.empty
      
      if (!empty && item.messages.nonEmpty) {
        chatList.chatBoxCustomizer.get.apply(item, this)
        Option(chatList.userNameNodeFactory.get).fold(boxEntryComponent.userLabel setText chatList.userDisplayName(item.user))(nodeFactory =>
          userLabel setGraphic nodeFactory(item.user))
        
        val date = chatList.messageDate(item.messages.head)
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
    override def createDefaultSkin = new javafx.scene.control.skin.LabeledSkinBase(this) {
      consumeMouseEvents(false)
    }
    
    def messageBox(msg: Message): Pane = {
      val chatMessageComponent = new ui.ChatMessageComponent()
      val renderedMarkdown = 
        try chatList.messageRenderFactory.get()(msg, renderContext)
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
      
      Option(chatList.messageControlsFactory.get).foreach(_(msg) foreach chatMessageComponent.chatMessageControls.getChildren.add)
      
      val ShowSourceMode = new Tooltip("Show source")
      val HideSourceMode = new Tooltip("Hide source")
      chatMessageComponent.chatMessageControls.getChildren add new Button("🗏").tap { button =>
        button setTooltip ShowSourceMode
        button setOnAction { evt =>
          chatMessageComponent.chatMessages.getChildren.clear()
          button.getTooltip match {
            case ShowSourceMode =>
              chatMessageComponent.chatMessages.getChildren add new TextArea(chatList.messageContent(msg)).tap { ta => 
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
        b setOnAction { evt => Clipboard.getSystemClipboard setContent new ClipboardContent().tap(_.putString(chatList.messageContent(msg)))}
      }
      
      chatMessageComponent.component
    }
  }
}
