package ccc

import java.time.LocalDateTime
import javafx.application.Application
import javafx.scene.control.ScrollPane
import javafx.scene.layout.BorderPane
import javafx.scene.text.Font
import javafx.scene.web.WebView
import javafx.stage.Stage

object ChatAreaTest extends App {
  System.setProperty("prism.lcdtext", "false")
  System.setProperty("prism.text", "t2k")
  Application.launch(classOf[ChatAreaTest], args:_*)
}
class ChatAreaTest extends BaseApplication {
  override def extraInitialize(stage: Stage): Unit = {
    stage.title = "CCC"
    stage.width = 700
    stage.height = 700
    stage.scene.stylesheets.add("/ccc-theme.css")
  }
  
  val emojis = util.EmojiOne.emojiLookup.map(e => e._1 -> new util.WeakImage(s"/emojione/128x128_png/${e._2.filename}.png"))
  private[this] val imagesCache: collection.mutable.Map[String, util.WeakImage] = new util.LruMap[String, util.WeakImage](100).withDefault { k => // cache the most recent images shown in the chat
    val res = new util.WeakImage(k)
    imagesCache(k) = res
    res
  }
  /**
   * cache some viewpanes, though only weakly, if they get claimed that's alright
   */
  private[this] val webViewCache = new util.WeakObjectPool[WebView](() => {
      val res = new WebView()
      res.contextMenuEnabled = false
      res.styleClass add "code-block"
      res
    })
  val chatList = new ChatList[String, String](webViewCache, imagesCache, emojis.mapValues(_.get), identity, identity, _ => LocalDateTime.now())
  val sceneRoot = new BorderPane {
    this center new ScrollPane(chatList).modify(_.fitToWidth = true, _.fitToHeight = true)
    this bottom new ChatTextInput(webViewCache, imagesCache, emojis.mapValues(_.get))
  }
  
  val imgSize = Font.getDefault.getSize * 4
  val panda = new util.WeakImage("https://cdn.discordapp.com/avatars/84766711735136256/28063abbe16697aa29d99d004ebd177f.png?size=256", imgSize, imgSize)
  val totoro = new util.WeakImage("https://cdn.discordapp.com/avatars/183411122848661505/81e6a9370e6a54ea19b3acad6c811e61.png?size=256", imgSize, imgSize)
  chatList.addEntry("(⊙.⊙)☂", totoro, "**How** *about* __some__ ~~markdown~~ _**rendering**_? [I'm an inline-style link](https://duckduckgo.com/ \"link with custom text!\") \n\nAnother link just in case https://duckduckgo.com/\n\nSince we are not discord, we can use markdown for images too ![alt text](http://www.kidsarthub.com/wp-content/uploads/2015/01/How_to_draw_panda_bear-136x100.jpg \"Logo Title Text 1\")")
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
  chatList.addEntry("(⊙.⊙)☂", totoro, "Some emojis now, vampire :vampire:,:chopsticks:,:grin:,:runner_tone1:,:runner_tone2:,:runner_tone3:,:runner_tone4:,😼")
  for (i <- 0 until 100) {
    val (image, user) = if (i % 2 == 0) (totoro, "(⊙.⊙)☂") else (panda, "Panda")
    for (j <- 0 until (math.random * 5).toInt) chatList.addEntry(user, image, s"$i-$j " + emojis.keysIterator.drop((math.random * (emojis.size - 1)).toInt).next)
  }
}
