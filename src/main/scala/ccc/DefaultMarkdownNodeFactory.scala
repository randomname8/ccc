package ccc

import better.files._
import javafx.animation.{Animation, KeyFrame, KeyValue, Timeline}
import javafx.application.HostServices
import javafx.scene.{Node, Scene}
import javafx.scene.control.{Label, Tooltip, TitledPane, ScrollPane}
import javafx.scene.image.{ImageView}
import javafx.scene.input.MouseButton
import javafx.scene.text.{Font, Text}
import javafx.stage.Stage
import javafx.util.Duration
import netscape.javascript.JSObject

class DefaultMarkdownNodeFactory(
  val hostServices: HostServices,
  val imagesCache: collection.Map[String, util.WeakImage]) extends MarkdownRenderer.NodeFactory {
  import DefaultMarkdownNodeFactory._
  
  def desiredImageHeight = Font.getDefault.getSize * 1.8
  override def mkEmoji(context)(name, image): Node = {
    val emojiImageView = new ImageView(image).modify(_.setPreserveRatio(true), _.setSmooth(true), _.setFitHeight(desiredImageHeight))
    val emojiLabel = new Label(null, emojiImageView)
    emojiLabel setTooltip new Tooltip(name)
    val expandAnimation = new Timeline(
      new KeyFrame(Duration.millis(0), new KeyValue(emojiImageView.fitHeightProperty(), desiredImageHeight: Number)),
      new KeyFrame(Duration.millis(200), new KeyValue(emojiImageView.fitHeightProperty(), 128.0.max(desiredImageHeight * 3): Number)))
    emojiLabel setOnMouseClicked {evt => if (evt.getButton == MouseButton.PRIMARY) {
        expandAnimation.setRate(1)
        expandAnimation.playFromStart()
      }}
    emojiLabel setOnMouseExited { evt => if (emojiImageView.getFitHeight != desiredImageHeight) {
        expandAnimation.setRate(-1)
        if (expandAnimation.getStatus == Animation.Status.STOPPED) {
          expandAnimation.jumpTo(Duration.millis(200))
          expandAnimation.play()
        }
      }}
    emojiLabel
  }
  
  //we'll store the collpased state of the last 1000 generated collapsible elements, so when we regenerate their node, we use the previous
  //collapsed state
  private[this] val collapsedElementState = new util.LruMap[Any, Boolean](1000)
  
  override def mkInlineContent(context)(title, url, altText): Node = {
    def content(fitWidth: Double, fitHeight: Double) = {
      val container = new util.ResizableStackPane()
      val content = if (url matches ".+(avi|flv|mkv|webm|mp4)") {
        val mediaPlayer = context.mediaPlayerProvider()
        mediaPlayer.setMedia(url, None)
        mediaPlayer
      } else {
        val image = imagesCache(url).get
        val imageView = new ImageView(image).modify(_.setFitHeight(fitWidth), _.setFitWidth(fitHeight), _.setPreserveRatio(true))
        imageView.fitWidthProperty.bind(container.prefWidthProperty.map(v => if (v.doubleValue == -1) fitWidth else v))
        imageView.fitHeightProperty.bind(container.prefHeightProperty.map(v => if (v.doubleValue == -1) fitHeight else v))
        imageView
      }
      container.getChildren.add(content)
      container
    }
    new CollapsibleContent(title, content(500, 500), url).modify(
      _.setTooltip(new Tooltip(altText)),
      self => self setOnMouseClicked { evt => evt.getButton match {
          case MouseButton.SECONDARY =>
            val stage = new Stage()
            stage initOwner self.getScene.getWindow
            stage setTitle title
            stage setScene new Scene(new ScrollPane(content(0, 0)))
            stage.sizeToScene()
            stage.show()
          case MouseButton.MIDDLE => hostServices.showDocument(url)
          case _ =>
        }})
  }
  
  def mkLink(context)(title, url): Node = new Text(title.getOrElse(url)).modify(
    _.getStyleClass.add("md-link"),
    _ setOnMouseClicked { evt => if (evt.getButton == MouseButton.PRIMARY) hostServices.showDocument(url) })

  def mkCodeLine(context)(code): Node = new Label(code).modify(_.setFont(monoscriptFont), _.setStyle("-fx-background-color: lightgray;"))
  
  def mkCodeBlock(context)(language: Option[String], code: String): Node = {
    //check if we have some webview available, otherwise create one
    val webView = context.webViewProvider()
    val langClass = language.getOrElse("").replace("\"", "&quot;")
    val content = code.replace("<", "&lt;").replace(">", "&gt;")
    val html = s"""
<html>
<head>
<link rel="stylesheet" href="$highlightsCss">
<script src="$highlightsJs"></script>
<script>hljs.initHighlightingOnLoad();</script>
</head>
<body>
<pre><code class="$langClass" id="code">$content</code></pre>
</body>
</html>"""
    webView.getEngine.getLoadWorker.stateProperty.addListener { (_, _, state) => 
      if (state == javafx.concurrent.Worker.State.SUCCEEDED) {
        val el = webView.getEngine.getDocument.getElementById("code")
        val height = el.asInstanceOf[JSObject].getMember("scrollHeight").asInstanceOf[Int].toDouble + Font.getDefault.getSize * 2
        webView.setMinHeight(height)
        webView.setMaxHeight(height + Font.getDefault.getSize)
      }
    }
    webView.getEngine.loadContent(html)
    webView
  }
  
  class CollapsibleContent(title: String, content: Node, url: String) extends TitledPane(title, content) {
    getStyleClass.add("collapsible-content")
    setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE)
    this setExpanded collapsedElementState.get(url).getOrElse(true)
    expandedProperty.foreach(collapsedElementState(url) = _)
  }
}

object DefaultMarkdownNodeFactory {
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
  
  private[DefaultMarkdownNodeFactory] val monoscriptFont = Font.font("Monospaced")
}
