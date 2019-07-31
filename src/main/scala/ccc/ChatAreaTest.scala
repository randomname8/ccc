package ccc

import java.time.LocalDateTime
import javafx.application.Application
import javafx.geometry.Pos
import javafx.scene.input.KeyCode
import javafx.scene.layout.BorderPane
import javafx.scene.text.Font
import javafx.stage.Stage
import tangerine._, JfxControls._

object ChatAreaTest extends App {
  System.setProperty("prism.lcdtext", "false")
  System.setProperty("prism.text", "t2k")
  Application.launch(classOf[ChatAreaTest], args:_*)
}
class ChatAreaTest extends BaseApplication {
  override def extraInitialize(stage: Stage): Unit = {
    stage setTitle "CCC"
    stage setWidth 700
    stage setHeight 700
    stage.getScene.getStylesheets.add("/ccc-theme.css")
  }
  
  val emojis = util.EmojiOne.emojiLookup.map(e => e._1 -> new util.WeakImage(s"file:emojione_128/${e._2.filename}.png"))
  private[this] val imagesCache: collection.mutable.Map[String, util.WeakImage] = util.LruMap[String, util.WeakImage](100).withDefault { k => // cache the most recent images shown in the chat
    val res = new util.WeakImage(k)
    imagesCache(k) = res
    res
  }
  val emojisLookup = emojis.view.mapValues(_.get.value.get.get).toMap
  
  val mdRenderer = new MarkdownRenderer()
  
  val mdNodeFactory = new DefaultMarkdownNodeFactory(getHostServices, imagesCache)
  val chatList = new ChatList[String, String](getHostServices, identity, identity, _ => LocalDateTime.now())
  chatList.messageRenderFactory.set((msg, vlcFactory) => mdRenderer.render(msg, emojisLookup, mdNodeFactory)(MarkdownRenderer.RenderContext(vlcFactory)))
  
  
  val chatTextInput = new ChatTextInput(mdRenderer, mdNodeFactory, emojisLookup)
  val sceneRoot = new BorderPane {
//    this setCenter new ScrollPane(chatList).tap { s => s setFitToWidth true; s setFitToHeight true }
    this setCenter chatList
    this setBottom hbox(chatTextInput, new javafx.scene.control.Button("press me"))(Pos.BASELINE_LEFT)
  }
  
  val imgSize = Font.getDefault.getSize * 4
  val nekobus = new util.WeakImage("https://78.media.tumblr.com/tumblr_lovxnpURC01qlfu1ho1_500.gif", imgSize, imgSize)
  val totoro = new util.WeakImage("https://cdn.discordapp.com/avatars/183411122848661505/81e6a9370e6a54ea19b3acad6c811e61.png?size=256", imgSize, imgSize)
  
  chatList.addEntry("(⊙.⊙)☂", totoro, """**How** *about* __some__ ~~markdown~~ _**rendering**_? [I'm an inline-style link](https://duckduckgo.com/ "link with custom text!") 

Another **link** just in case https://duckduckgo.com/

Since we are not discord, we can use markdown for images too ![alt text](https://lord2015.files.wordpress.com/2015/02/totoro_wallpaper_by_vampiresuper_sayajin-d6gx09h.png "Totoro!")""")
  
  chatList.addEntry("(⊙.⊙)☂", totoro, "inline `code` here")
  chatList.addEntry("(⊙.⊙)☂", totoro, """code block now in scala
```scala
object ChatAreaTest extends App {
  System.setProperty("prism.lcdtext", "false")
  System.setProperty("prism.text", "t2k")
  Application.launch(classOf[ChatAreaTest], args:_*)
}
```
and some bash now
```bash
for i in {0..7}; do sudo cpufreq-set -g performance -u 2GHz -c $i; done;
```
""")
  chatList.addEntry("(⊙.⊙)☂", totoro, """Video now, because we like videos

![alt text](https://cdn.discordapp.com/attachments/307260368764534784/415004984074305556/Peek_2018-02-19_01-41.webm "prior demo of video playback!")""")
  
  chatList.addEntry("(⊙.⊙)☂", totoro, "Some emojis now, vampire :vampire:,:chopsticks:,:grin:,:runner_tone1:,:runner_tone2:,:runner_tone3:,:runner_tone4:,😼")
  chatList.addEntry("(⊙.⊙)☂", totoro, """Let's try some text\
here's some quoted text

> Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\
Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in\
reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa\
qui officia deserunt mollit anim id est laborum.
         
so he says.
""")
  
  chatList.addEntry("(⊙.⊙)☂", totoro, """An unordered list of items
* **some _item_**
* some other item
* and another

and an ordered list too
1. first
1. second :runner_tone2:
3. third""")
  
  
  for (i <- 0 until 100) {
    val (image, user) = if (i % 2 == 0) (totoro, "(⊙.⊙)☂") else (nekobus, "Nekobus")
    for (j <- 0 until (math.random * 5).toInt) chatList.addEntry(user, image, s"$i-$j " + emojis.keysIterator.drop((math.random * (emojis.size - 1)).toInt).next)
  }
  
  
  chatTextInput.textArea setOnKeyReleased { evt =>
    if (evt.getCode == KeyCode.ENTER) {
      if (evt.isShiftDown) {
        chatTextInput.textArea.insertText(chatTextInput.textArea.getCaretPosition, "\n")
      } else if (!evt.isControlDown && !evt.isAltDown) {
        evt.consume()
        val msg = chatTextInput.textArea.getText
        chatTextInput.textArea.clear()
        chatList.addEntry("(⊙.⊙)☂", totoro, msg.trim.replace("\n", "\\\n"))
      }
    }
  }
}
