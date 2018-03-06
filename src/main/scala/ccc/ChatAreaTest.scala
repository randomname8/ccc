package ccc

import java.time.LocalDateTime
import javafx.application.Application
import javafx.scene.control.ScrollPane
import javafx.scene.input.KeyCode
import javafx.scene.layout.BorderPane
import javafx.scene.text.Font
import javafx.stage.Stage

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
  private[this] val imagesCache: collection.mutable.Map[String, util.WeakImage] = new util.LruMap[String, util.WeakImage](100).withDefault { k => // cache the most recent images shown in the chat
    val res = new util.WeakImage(k)
    imagesCache(k) = res
    res
  }
  
  val markdownRenderer = new DefaultMarkdownNodeFactory(getHostServices, imagesCache)
  val chatList = new ChatList[String, String](getHostServices, markdownRenderer, emojis.mapValues(_.get), identity, identity, _ => LocalDateTime.now())
  val chatTextInput = new ChatTextInput(markdownRenderer, emojis.mapValues(_.get))
  val sceneRoot = new BorderPane {
    this center new ScrollPane(chatList).modify(_ setFitToWidth true, _ setFitToHeight true)
    this bottom chatTextInput
  }
  
  val imgSize = Font.getDefault.getSize * 4
  val panda = new util.WeakImage("https://cdn.discordapp.com/avatars/84766711735136256/28063abbe16697aa29d99d004ebd177f.png?size=256", imgSize, imgSize)
  val totoro = new util.WeakImage("https://cdn.discordapp.com/avatars/183411122848661505/81e6a9370e6a54ea19b3acad6c811e61.png?size=256", imgSize, imgSize)
  chatList.addEntry("(⊙.⊙)☂", totoro, """**How** *about* __some__ ~~markdown~~ _**rendering**_? [I'm an inline-style link](https://duckduckgo.com/ \"link with custom text!\") 

Another link just in case https://duckduckgo.com/

nSince we are not discord, we can use markdown for images too ![alt text](https://lord2015.files.wordpress.com/2015/02/totoro_wallpaper_by_vampiresuper_sayajin-d6gx09h.png "Totoro!")""")
  
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
  for (i <- 0 until 100) {
    val (image, user) = if (i % 2 == 0) (totoro, "(⊙.⊙)☂") else (panda, "Panda")
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
