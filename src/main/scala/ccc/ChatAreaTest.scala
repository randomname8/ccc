package ccc

import javafx.application.Application
import javafx.scene.control.ScrollPane
import javafx.scene.image.Image
import javafx.scene.text.Font
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
  }
  
  val chatList = new ChatList()
  val sceneRoot = new ScrollPane(chatList).modify(_.fitToWidth = true, _.fitToHeight = true)
  
  val imgSize = Font.getDefault.getSize * 4
  val panda = new Image("https://cdn.discordapp.com/avatars/84766711735136256/28063abbe16697aa29d99d004ebd177f.png?size=256", imgSize, imgSize, true, true)
  val totoro = new Image("https://cdn.discordapp.com/avatars/183411122848661505/81e6a9370e6a54ea19b3acad6c811e61.png?size=256", imgSize, imgSize, true, true)
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
  for (i <- 0 until 100) {
    val (image, user) = if (i % 2 == 0) (totoro, "(⊙.⊙)☂") else (panda, "Panda")
    for (j <- 0 until (math.random * 5).toInt) chatList.addEntry(user, image, s"$i-$j")
  }
}
