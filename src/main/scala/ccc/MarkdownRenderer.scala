package ccc

import better.files._
import javafx.beans.value.ObservableValue
import javafx.scene.Node
import javafx.scene.image.Image
import javafx.scene.text._
import javafx.scene.web.WebView
import org.commonmark.{node => md, ext => mdext}
import scala.collection.JavaConverters._

object MarkdownRenderer {

  //need to extract the highligher.js and css to temp files
  //we want to keep these cached in temp, but we have to make sure we don't conflict with other programs, so in case we do, we'll just use
  //two random new temp files
  val (highlightsJs, highlightsCss) = {
    val cccTempDir = (File.temp/"ccc").createIfNotExists(asDirectory = true)
    val (highlightsJs, highlightsCss) = cccTempDir.list.toSeq match {
      case Seq() => (cccTempDir/"highlight.min.js", cccTempDir/"highlights.css")//extract the files
      case Seq(f1, f2) if f1.name == "highlight.min.js" && f2.name == "highlights.css" => (f1, f2)
      case _ => (File.newTemporaryFile("ccc", "highlight.min.js"), File.newTemporaryFile("ccc", "highlights.css"))//unknown directory! use two random files
    }
    if (highlightsJs.isEmpty) File.copyResource("syntax-highlighter/highlight.min.js")(highlightsJs)
    if (highlightsCss.isEmpty) File.copyResource("syntax-highlighter/highlights.css")(highlightsCss)
    (highlightsJs.uri, highlightsCss.uri)
  }
  
  private[this] val markdownParser = org.commonmark.parser.Parser.builder.extensions(java.util.Arrays.asList(
      org.commonmark.ext.autolink.AutolinkExtension.create(),
      org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create())).customDelimiterProcessor(MarkdownExtensions.InsDelimiterProcessor).build()
  private[this] val emphasisFont = Font.font(Font.getDefault.getFamily, FontPosture.ITALIC, Font.getDefault.getSize)
  private[this] val strongEmphasisFont = Font.font(Font.getDefault.getFamily, FontWeight.BOLD, Font.getDefault.getSize)
  private[this] val strongerEmphasisFont = Font.font(Font.getDefault.getFamily, FontWeight.BOLD, FontPosture.ITALIC, Font.getDefault.getSize)
  
  def render(text: String, widthProperty: ObservableValue[_ <: Number],
             webViewProvider: () => WebView, emojiProvider: Map[String, Image],
             nodeFactory: NodeFactory): Seq[Node] = {
    var res = Vector.empty[Node]
    var curr: Option[TextFlow] = None //the currently TextFlow buing built, starts in none because we might not build any
    def texts = {
      if (curr.isEmpty) {
        curr = Some(new TextFlow)
        curr.get.maxWidthProperty bind widthProperty
        res :+= curr.get
      }
      curr.get.children
    }
    
    markdownParser.parse(text).accept(new md.AbstractVisitor {
        override def visit(p: md.Paragraph) = {
          super.visit(p)
          texts add new Text("\n")
        }
        override def visit(t: md.Text) = {
          val text = t.getLiteral
          val usedEmojis = text.split("""\s+|(?!;_;|;P|;p|;\))[,;\.]""").flatMap(s => emojiProvider.get(s).map(s -> _))
          var lastIdx = 0
          for ((toReplace, emoji) <- usedEmojis) {
            val idx = text.indexOf(toReplace, lastIdx) // because of our previous split and find, the index *has* to exists
            texts add new Text(text.substring(lastIdx, idx))
            texts add nodeFactory.mkEmoji(toReplace, emoji)
            lastIdx = idx + toReplace.length
          }
          if (lastIdx != text.length) texts add new Text(text.substring(lastIdx))
        }
        override def visit(e: md.Emphasis) = modifyGeneratedTexts(e)(t => if (t.getFont == strongEmphasisFont) t.setFont(strongerEmphasisFont) else t.setFont(emphasisFont))
        override def visit(e: md.StrongEmphasis) = modifyGeneratedTexts(e)(t => if (t.getFont == emphasisFont) t.setFont(strongerEmphasisFont) else t.setFont(strongEmphasisFont))
        override def visit(e: md.Image) = texts add nodeFactory.mkInlineContent(e.getTitle, e.getDestination)
        override def visit(e: md.Link) = texts add nodeFactory.mkLink(Option(e.getTitle), e.getDestination)
        override def visit(e: md.CustomNode) = {
          e match {
            case e: mdext.gfm.strikethrough.Strikethrough => modifyGeneratedTexts(e)(_.setStrikethrough(true))
            case e: mdext.ins.Ins => modifyGeneratedTexts(e)(_.setUnderline(true))
            case _ => visitChildren(e)
          }
        }
        override def visit(e: md.Code) = texts add nodeFactory.mkCodeLine(e.getLiteral)
        override def visit(e: md.FencedCodeBlock) = {
          curr = None
          res :+= nodeFactory.mkCodeBlock(Option(e.getInfo), e.getLiteral, webViewProvider, widthProperty)
        }
        def modifyGeneratedTexts(n: md.Node)(f: Text => Unit): Unit = {
          val start = texts.size
          visitChildren(n)
          for (i <- start until texts.size) texts.get(i) match {
            case t: Text => f(t)
            case _ =>
          }
        }
      })
    texts.asScala.lastOption foreach { //in the last text, make sure we remove the trailing \n
      case t: Text => t.setText(t.getText.trim)
      case _ =>
    }
    res
  }
  
  trait NodeFactory {
    def mkEmoji(name: String, image: Image): Node
    def mkInlineContent(title: String, url: String): Node
    def mkLink(title: Option[String], url: String): Node
    def mkCodeLine(code: String): Node
    def mkCodeBlock(lang: Option[String], code: String, webViewProvider: () => WebView, widthProperty: ObservableValue[_ <: Number]): Node
  }
}
