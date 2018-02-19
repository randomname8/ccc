package ccc

import better.files._
import javafx.animation.{Animation, KeyFrame, KeyValue, Timeline}
import javafx.application.HostServices
import javafx.beans.value.ObservableValue
import javafx.scene.{Node, Scene}
import javafx.scene.control.{Label, Tooltip, TitledPane, ScrollPane}
import javafx.scene.image.{Image, ImageView}
import javafx.scene.input.MouseButton
import javafx.scene.text.{Font, Text}
import javafx.scene.web.WebView
import javafx.stage.Stage
import javafx.util.Duration
import netscape.javascript.JSObject

class DefaultMarkdownNodeFactory(
  val hostServices: HostServices,
  val imagesCache: collection.Map[String, util.WeakImage]) extends MarkdownRenderer.NodeFactory {
  import DefaultMarkdownNodeFactory._
  
  def mkEmoji(name: String, image: Image): Node = {
    val desiredImageHeight = Font.getDefault.getSize * 1.5
    val emojiImageView = new ImageView(image).modify(_.setPreserveRatio(true), _.setSmooth(true), _.setFitHeight(desiredImageHeight))
    val emojiLabel = new Label(null, emojiImageView)
    emojiLabel.tooltip = new Tooltip(name)
    val expandAnimation = new Timeline(
      new KeyFrame(Duration.millis(0), new KeyValue(emojiImageView.fitHeightProperty(), desiredImageHeight: Number)),
      new KeyFrame(Duration.millis(200), new KeyValue(emojiImageView.fitHeightProperty(), 128.0: Number)))
    emojiLabel.onMouseClicked = evt => if (evt.button == MouseButton.PRIMARY) {
      expandAnimation.setRate(1)
      expandAnimation.playFromStart()
    }
    emojiLabel.onMouseExited = evt => if (emojiImageView.getFitHeight != desiredImageHeight) {
      expandAnimation.setRate(-1)
      if (expandAnimation.getStatus == Animation.Status.STOPPED) {
        expandAnimation.jumpTo(Duration.millis(200))
        expandAnimation.play()
      }
    }
    emojiLabel
  }
  
  //we'll store the collpased state of the last 1000 generated collapsible elements, so when we regenerate their node, we use the previous
  //collapsed state
  private[this] val collapsedElementState = new util.LruMap[Any, Boolean](1000)
  
  def mkInlineImage(title: String, url: String): Node = {
    val image = imagesCache(url).get
    val imageView = new ImageView(image).modify(_.setFitHeight(500), _.setFitWidth(500), _.setPreserveRatio(true))
    val container = new util.ResizableStackPane(imageView)
    imageView.fitWidthProperty.bind(container.prefWidthProperty.map(v => if (v.doubleValue == -1) 500 else v))
    imageView.fitHeightProperty.bind(container.prefHeightProperty.map(v => if (v.doubleValue == -1) 500 else v))
    new TitledPane(title, container).modify(
      _.styleClass.add("collapsible-image"),
      _.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE),
      _.expanded = collapsedElementState.get(url).getOrElse(true),
      _.expandedProperty.foreach(collapsedElementState(url) = _),
      _.onMouseClicked = evt => evt.button match {
        case MouseButton.SECONDARY =>
          val stage = new Stage()
          stage.title = title
          val imageView = new ImageView(image).modify(_.setPreserveRatio(true))
          val container = new util.ResizableStackPane(imageView)
          imageView.fitWidthProperty.bind(container.prefWidthProperty)
          imageView.fitHeightProperty.bind(container.prefHeightProperty)
          stage.scene = new Scene(new ScrollPane(container))
          stage.sizeToScene()
          stage.show()
        case MouseButton.MIDDLE => hostServices.showDocument(url)
        case _ =>
      })
  }
  
  def mkLink(title: Option[String], url: String): Node = new Text(title.getOrElse(url)).modify(
    _.styleClass.add("md-link"),
    _.onMouseClicked = evt => if (evt.button == MouseButton.PRIMARY) hostServices.showDocument(url))

  def mkCodeLine(code: String): Node = new Label(code).modify(_.setFont(monoscriptFont), _.setStyle("-fx-background-color: lightgray;"))
  
  def mkCodeBlock(language: Option[String], code: String, webViewProvider: () => WebView, widthProperty: ObservableValue[_ <: Number]): Node = {
    //check if we have some webview available, otherwise create one
    val webView = webViewProvider()
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
    webView.engine.loadWorker.stateProperty.addListener { (_, _, state) => 
      if (state == javafx.concurrent.Worker.State.SUCCEEDED) {
        val el = webView.engine.document.getElementById("code")
        val height = el.asInstanceOf[JSObject].getMember("scrollHeight").asInstanceOf[Int].toDouble + Font.getDefault.getSize * 2
        webView.setMinHeight(height)
        webView.setMaxHeight(height + Font.getDefault.getSize)
        webView.maxWidthProperty bind widthProperty
      }
    }
    webView.engine.loadContent(html)
    webView
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
