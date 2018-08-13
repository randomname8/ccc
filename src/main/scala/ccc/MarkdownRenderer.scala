package ccc

import better.files._
import javafx.geometry.{Orientation, Pos}
import javafx.scene.Node
import javafx.scene.control.Separator
import javafx.scene.image.Image
import javafx.scene.text._
import org.commonmark.{node => md, ext => mdext}
import org.commonmark.parser.{Parser => MdParser}
import scala.collection.JavaConverters._

class MarkdownRenderer(
  val parser: MdParser = MdParser.builder.extensions(java.util.Arrays.asList(
      org.commonmark.ext.autolink.AutolinkExtension.create(),
      org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create())).customDelimiterProcessor(MarkdownExtensions.InsDelimiterProcessor).build(),
  val emphasisFont: Font = Font.font(Font.getDefault.getFamily, FontPosture.ITALIC, Font.getDefault.getSize),
  val strongEmphasisFont: Font = Font.font(Font.getDefault.getFamily, FontWeight.BOLD, Font.getDefault.getSize),
  val strongerEmphasisFont: Font = Font.font(Font.getDefault.getFamily, FontWeight.BOLD, FontPosture.ITALIC, Font.getDefault.getSize)) {
  import MarkdownRenderer._

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
  
  def render(text: String,
             emojiProvider: Map[String, Image],
             nodeFactory: NodeFactory)(context: RenderContext): Seq[Node] = renderMarkdown(parser.parse(text), emojiProvider, nodeFactory, context)

  private def renderMarkdown(node: md.Node, emojiProvider: Map[String, Image],
                             nodeFactory: NodeFactory, context: RenderContext): Seq[Node] = {
    var res = Vector.empty[Node]
    var curr: Option[TextFlow] = None //the currently TextFlow buing built, starts in none because we might not build any
    def texts = {
      if (curr.isEmpty) {
        curr = Some(new TextFlow)
        res :+= curr.get
      }
      curr.get.getChildren
    }
    def appendText(t: String) = {
      val ts = texts.asScala
      ts.lastOption match {
        case Some(st: Text) => st.setText(st.getText + t)
        case _ => ts += nodeFactory.mkText(context)(t)
      }
    }
    def ensureNewTextRun(): Int = {
      texts.asScala.lastOption match {
        case Some(t: Text) => //check if the last text is already a plain text, if it is empty it serves our purposes, otherwise we need a new one to ensure the style applied here does not leak
          if (t.getText.nonEmpty) texts add nodeFactory.mkText(context)("")
          texts.size - 1
              
        case other => texts.size
      }
    }
    def sealCurrentTextRun() = {
      //check if the last node is already an empty text, if not, add one to avoid getting new text appended to this format and hence leaking this format
      texts.asScala.lastOption match {
        case Some(t: Text) if t.getText.nonEmpty => texts add nodeFactory.mkText(context)("")
        case _ =>
      }
    }
    node.accept(new md.AbstractVisitor {
        override def visit(p: md.Paragraph) = {
          super.visit(p)
          appendText("\n")
        }
        override def visit(t: md.Text) = {
          val text = t.getLiteral
          val usedEmojis = text.split("""\s+|(?!;_;|;P|;p|;\))[,;\.]""").flatMap(s => emojiProvider.get(s).map(s -> _))
          var lastIdx = 0
          for ((toReplace, emoji) <- usedEmojis) {
            val idx = text.indexOf(toReplace, lastIdx) // because of our previous split and find, the index *has* to exists
            appendText(text.substring(lastIdx, idx))
            texts add nodeFactory.mkEmoji(context)(toReplace, emoji)
            lastIdx = idx + toReplace.length
          }
          if (lastIdx != text.length) appendText(text.substring(lastIdx))
        }
        override def visit(e: md.HardLineBreak) = appendText("\n")
        override def visit(e: md.Emphasis) = modifyGeneratedTexts(e)(t => if (t.getFont == strongEmphasisFont) t.setFont(strongerEmphasisFont) else t.setFont(emphasisFont))
        override def visit(e: md.StrongEmphasis) = modifyGeneratedTexts(e)(t => if (t.getFont == emphasisFont) t.setFont(strongerEmphasisFont) else t.setFont(strongEmphasisFont))
        override def visit(e: md.Image) = texts add nodeFactory.mkInlineContent(context)(e.getTitle, e.getDestination, e.getFirstChild match {
            case t: md.Text if t != null => t.getLiteral
            case _ => ""
          })
        override def visit(e: md.Link) = {
          texts add nodeFactory.mkLink(context)(Option(e.getTitle), e.getDestination)
          sealCurrentTextRun()
        }
        override def visit(e: md.BlockQuote) = {
          e.getFirstChild match {
            case null => appendText(">\n")
            case node => 
              val toBeQuoted = renderMarkdown(e.getFirstChild, emojiProvider, nodeFactory, context)
              curr = None
              res :+= hbox(new Separator(Orientation.VERTICAL), vbox(toBeQuoted:_*)(fillWidth = true, spacing = 0, alignment = Pos.TOP_LEFT))(fillHeight = true, alignment = Pos.TOP_LEFT)
          }
          
        }
        override def visit(e: md.CustomNode) = {
          e match {
            case e: mdext.gfm.strikethrough.Strikethrough => modifyGeneratedTexts(e)(_.setStrikethrough(true))
            case e: mdext.ins.Ins => modifyGeneratedTexts(e)(_.setUnderline(true))
            case e if customNodeSupport.isDefinedAt(e) => 
              val (node, inlined) = customNodeSupport(e)(emojiProvider, nodeFactory, context)
              if (inlined) {
                texts add node
                sealCurrentTextRun()
              } else {
                curr = None
                res :+= node
              }
            case _ => visitChildren(e)
          }
        }
        override def visit(e: md.Code) = {
          texts add nodeFactory.mkCodeLine(context)(e.getLiteral)
          sealCurrentTextRun()
        }
        override def visit(e: md.FencedCodeBlock) = {
          curr = None
          res :+= nodeFactory.mkCodeBlock(context)(Option(e.getInfo), e.getLiteral)
        }
        
        var currentOrderedListNumber: Int = _
        override def visit(e: md.OrderedList) = {
          currentOrderedListNumber = e.getStartNumber
          visitChildren(e)
        }
        override def visit(e: md.ListItem) = {
          val currElem = ensureNewTextRun()
          visitChildren(e)
          val toInsert = e.getParent match {
            case _: md.OrderedList => 
              val res = f"$currentOrderedListNumber% 3d. "
              currentOrderedListNumber += 1
              res
            case _ => " • "
          }
          if (texts.size == currElem) { //if nothing was added yet (empty element) just insert the point marker
            appendText(toInsert)
          } else texts.get(currElem) match {
            case t: Text => t.setText(toInsert + t.getText)
            case _ => texts.add(currElem, nodeFactory.mkText(context)(toInsert))
          }
          sealCurrentTextRun()
          
        }
        
        def modifyGeneratedTexts(n: md.Node)(f: Text => Unit): Unit = {
          val start = ensureNewTextRun()
          visitChildren(n)
          for (i <- start until texts.size) texts.get(i) match {
            case t: Text =>  f(t)
            case _ =>
          }
          sealCurrentTextRun()
        }
      })
    texts.asScala.lastOption foreach { //in the last text, make sure we remove the trailing \n
      case t: Text =>
        //remove trailing spaces to the text and check if it becomes empty to remove it, otherwise keep the trimmed version
        var text = t.getText
        var i = text.length
        var nonWhitespaceFound = false
        while (!nonWhitespaceFound && i > 0) {
          nonWhitespaceFound = !text.charAt(i - 1).isWhitespace
          if (!nonWhitespaceFound) i -= 1
        }
        if (i != text.length) //remove the whitespace found
          text = text.substring(0, i)
        
        if (text.isEmpty) texts.remove(t)
        else t.setText(text)
      case _ =>
    }
    
    
//    def describe(n: javafx.scene.Node): String = n match {
//      case t: Text => '"' + t.getText + '"'
//      case p: javafx.scene.Parent => p.getChildrenUnmodifiable.asScala.map(describe).mkString(p.getClass.getSimpleName + "{", ",", "}")
//      case n => n.toString
//    }
    res = res.filter { //remove empty TextFlows if any
      case tf: TextFlow => !tf.getChildren.isEmpty 
      case other => true
    }
    
//    println("result=======")
//    res map describe foreach println
    res
  }
  
  type Inlined = Boolean
  val customNodeSupport: PartialFunction[md.CustomNode, (Map[String, Image], NodeFactory, RenderContext) => (Node, Inlined)] = PartialFunction.empty
  
}
object MarkdownRenderer {
  case class RenderContext(mediaPlayerProvider: () => util.VlcMediaPlayer)
  trait NodeFactory {
    def mkText(context: RenderContext)(text: String): Text = {val t = new Text(text); t.getStyleClass add "md-text"; t }
    def mkEmoji(context: RenderContext)(name: String, image: Image): Node
    def mkInlineContent(context: RenderContext)(title: String, url: String, altText: String, width: Double = -1, height: Double = -1): Node
    def mkLink(context: RenderContext)(title: Option[String], url: String): Node
    def mkCodeLine(context: RenderContext)(code: String): Node
    def mkCodeBlock(context: RenderContext)(lang: Option[String], code: String): Node
  }
}
