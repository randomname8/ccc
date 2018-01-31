package ccc

import javafx.collections.FXCollections
import javafx.fxml.FXMLLoader
import javafx.scene.control._
import javafx.scene.image.{Image, ImageView}
import javafx.scene.layout._
import javafx.scene.paint.Color
import javafx.scene.text.{Text, TextFlow, Font, FontPosture, FontWeight}
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
  
  private[this] val markdownParser = org.commonmark.parser.Parser.builder.extensions(java.util.Arrays.asList(
      org.commonmark.ext.autolink.AutolinkExtension.create(),
      org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create())).customDelimiterProcessor(MarkdownExtensions.InsDelimiterProcessor).build()
  private[this] val emphasisFont = Font.font(Font.getDefault.getFamily, FontPosture.ITALIC, Font.getDefault.getSize)
  private[this] val strongEmphasisFont = Font.font(Font.getDefault.getFamily, FontWeight.BOLD, Font.getDefault.getSize)
  private[this] val strongerEmphasisFont = Font.font(Font.getDefault.getFamily, FontWeight.BOLD, FontPosture.ITALIC, Font.getDefault.getSize)
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
        avatarPane.background = new Background(new BackgroundImage(item.avatar, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, BackgroundSize.DEFAULT))
        userLabel.text = item.user
        dateLabel.text = "a date goes here, seriously "
        entriesVBox.children.clear()
        item.messages map renderMessage foreach entriesVBox.children.add
      } else {
        avatarPane.background = null
        userLabel.text = null
        dateLabel.text = null
        entriesVBox.children.clear()
      }
    }
    
    private def renderMessage(msg: String): TextFlow = {
      import org.commonmark.{node => md, ext => mdext}
      val res = new TextFlow()
      val texts = res.children.asScala
      markdownParser.parse(msg).accept(new md.AbstractVisitor {
          override def visit(p: md.Paragraph) = {
            super.visit(p)
            texts.lastOption foreach {
              case t: Text => t.setText(t.getText + "\n")
              case _ => texts += new Text("\n")
            }
          }
          override def visit(t: md.Text) = texts += new Text(t.getLiteral)
          override def visit(e: md.Emphasis) = modifyGeneratedTexts(e)(t => if (t.getFont == strongEmphasisFont) t.setFont(strongerEmphasisFont) else t.setFont(emphasisFont))
          override def visit(e: md.StrongEmphasis) = modifyGeneratedTexts(e)(t => if (t.getFont == emphasisFont) t.setFont(strongerEmphasisFont) else t.setFont(strongEmphasisFont))
          override def visit(e: md.Image) = texts += new Label(null, new ImageView(new Image(e.getDestination))).modify(_.tooltip = new Tooltip(e.getTitle))
          override def visit(e: md.Link) = texts += new Text(Option(e.getTitle).getOrElse(e.getDestination)).modify(_.setFill(Color.BLUE), _.setUnderline(true))
          override def visit(e: md.CustomNode) = {
            e match {
              case e: mdext.gfm.strikethrough.Strikethrough => modifyGeneratedTexts(e)(_.setStrikethrough(true))
              case e: mdext.ins.Ins => modifyGeneratedTexts(e)(_.setUnderline(true))
              case _ => visitChildren(e)
            }
          }
          def modifyGeneratedTexts(n: md.Node)(f: Text => Unit): Unit = {
            val start = texts.size
            visitChildren(n)
            for (i <- start until texts.size) texts(i) match {
              case t: Text => f(t)
              case _ =>
            }
          }
        })
      texts.lastOption foreach { //in the last text, make sure we remove the trailing \n
        case t: Text => t.setText(t.getText.trim)
      }
      res
    }
  }
}
