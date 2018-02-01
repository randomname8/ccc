package ccc

import better.files._
import javafx.beans.value.ObservableValue
import javafx.scene.control.{Label, Tooltip}
import javafx.scene.image.{Image, ImageView}
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
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
  private[this] val monoscriptFont = Font.font("Monospaced")
  
  def render(text: String, withProperty: ObservableValue[_ <: Number], webViewCache: WeakObjectPool[WebView]): TextFlow = {
    val res = new TextFlow()
    res.maxWidthProperty bind withProperty
    val texts = res.children.asScala
    markdownParser.parse(text).accept(new md.AbstractVisitor {
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
        override def visit(e: md.Image) = texts += new Label(null, new ImageView(new Image(e.getDestination, true))).modify(_.tooltip = new Tooltip(e.getTitle))
        override def visit(e: md.Link) = texts += new Text(Option(e.getTitle).getOrElse(e.getDestination)).modify(_.setFill(Color.BLUE), _.setUnderline(true))
        override def visit(e: md.CustomNode) = {
          e match {
            case e: mdext.gfm.strikethrough.Strikethrough => modifyGeneratedTexts(e)(_.setStrikethrough(true))
            case e: mdext.ins.Ins => modifyGeneratedTexts(e)(_.setUnderline(true))
            case _ => visitChildren(e)
          }
        }
        override def visit(e: md.Code) = texts += new Label(e.getLiteral).modify(_.setFont(monoscriptFont), _.setStyle("-fx-background-color: lightgray;"))
        override def visit(e: md.FencedCodeBlock) = {
          //check if we have some webview available, otherwise create one
          val webView = webViewCache.get
          webView.setMinHeight((e.getLiteral.split("\n").length + 1) * Font.getDefault.getSize)
          webView.setMaxHeight(res.getMinHeight)
            
//          localWebView :+= webView //track it ourselves
          val lang = e.getInfo.replace("\"", "&quot;")
          val content = e.getLiteral.replace("<", "&lt;").replace(">", "&gt;")
          val html = s"""
<html>
<head>
<link rel="stylesheet" href="$highlightsCss">
<script src="$highlightsJs"></script>
<script>hljs.initHighlightingOnLoad();</script>
</head>
<body>
<pre><code class="$lang">$content</code></pre>
</body>
</html>"""
          webView.engine.loadContent(html)
          texts += new StackPane(webView)
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
      case _ =>
    }
    res
  }
}
