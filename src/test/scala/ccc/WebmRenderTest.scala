package ccc

import javafx.scene.image.Image
import javafx.stage.Stage

class WebmRenderTest extends BaseApplication {
  override val sceneRoot = new util.VlcMediaPlayer()
  
  override def extraInitialize(stage: Stage) = {
    stage setTitle "playing"
    stage setX 2000
    stage setY 200
    stage setWidth 500
    stage setHeight 500
    
    stage.show()
    println("playing")
    
    sceneRoot.setMedia("https://cdn.discordapp.com/attachments/307260368764534784/415004984074305556/Peek_2018-02-19_01-41.webm",
                       Some(new Image("http://images6.fanpop.com/image/photos/33300000/MNT-my-neighbor-totoro-33302087-2304-1728.jpg")))
  }
}
