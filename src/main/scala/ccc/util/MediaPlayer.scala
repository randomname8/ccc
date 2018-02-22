package ccc
package util

import javafx.beans.property.{SimpleBooleanProperty, SimpleFloatProperty, SimpleStringProperty, SimpleLongProperty, SimpleObjectProperty}
import javafx.fxml.FXMLLoader
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.control.{Button, Control, Label, Slider}
import javafx.scene.layout.{Pane, StackPane}
import javafx.stage.{Popup, PopupWindow}

class MediaPlayer extends Control {

  val playing = new SimpleBooleanProperty(this, "playing", false)
  val fullscreen = new SimpleBooleanProperty(this, "fullscreen", false)
  val volume = new SimpleFloatProperty(this, "volume", 1)
  val title = new SimpleStringProperty(this, "title", "")
  
  val totalDuration = new SimpleLongProperty(this, "totalDuration")
  val currentTime = new SimpleLongProperty(this, "currentTime")
  val onPlay = new SimpleObjectProperty[() => Unit](this, "onPlay", () => ())
  val onSeeking = new SimpleObjectProperty[Long => Unit](this, "onSeeking", _ => ())
  
  val content = new SimpleObjectProperty[Node](this, "content")
  
  override protected def createDefaultSkin = Skin
  object Skin extends javafx.scene.control.Skin[MediaPlayer] {
    override def getSkinnable = MediaPlayer.this
    override def dispose() = {}
    val timeChangeBinding = JfxUtils.Binding(currentTime, totalDuration)(() => (currentTime.get, totalDuration.get)) //store the binding in a field to prevent it from being GCd
    override val getNode = {
      val root = FXMLLoader.load[StackPane](getClass.getResource("/media-player.fxml"))
      root.getStylesheets.add("/media-player.css")
      
      val overlay = root.lookup(".media-overlay").asInstanceOf[Pane]
      overlay.visibleProperty bind root.hoverProperty.or(playing.not)
      
      val titleLabel = root.lookup(".media-title-label").asInstanceOf[Label]
      titleLabel.textProperty bind title
      
      val progressText = root.lookup(".media-progress-text").asInstanceOf[Label]
      val progressSlider = root.lookup(".media-progress-slider").asInstanceOf[Slider]
      progressSlider.modify(
        _.min = 0, _.max = 1,
        _.blockIncrement = 0.01,
        _.valueProperty foreach (n => if (!progressSlider.valueChanging) onSeeking.get()((n.doubleValue * totalDuration.get).toLong)))
      
      timeChangeBinding.foreach { tuple => tuple match {
          case (currentTime, totalTime) =>
            progressSlider.value = currentTime.toDouble / totalTime
            progressText.text = s"${millisToString(currentTime)} / ${millisToString(totalTime)}"
        }
      }
      
      val playButton = root.lookup(".media-play-button").asInstanceOf[Button]
      playButton.onAction = evt => onPlay.get().apply()
      playing.foreach { playing => 
        if (playing) playButton.text = "⏸"
        else if (currentTime.get == totalDuration.get) playButton.text = "🔁"
        else playButton.text = "⏵"
      }
      
      val mediaSoundButton = root.lookup(".media-sound-button").asInstanceOf[Button]
      val currentVolume = new Slider(0, 100, 100).modify(
        _.orientation = Orientation.VERTICAL,
        _.blockIncrement = 10)
      val volumePopup = new Popup().modify(
        _.content add currentVolume,
        _.hideOnEscape = true, _.autoHide = true,
        _.anchorLocation = PopupWindow.AnchorLocation.CONTENT_BOTTOM_LEFT)
      volume.foreach { n =>
        val volume = n.floatValue
        if (volume == 0) mediaSoundButton.text = "🔇"
        else if (volume > 0 && volume <= 33) mediaSoundButton.text = "🔈"
        else if (volume > 33 && volume <= 66) mediaSoundButton.text = "🔉"
        else mediaSoundButton.text = "🔊"
        if (currentVolume.getValue != volume) currentVolume.setValue(volume.toDouble)
      }
      currentVolume.valueProperty foreach { newVolume =>
        if (!currentVolume.valueChanging) volume set newVolume.floatValue
      }
      
      mediaSoundButton.onAction = { evt =>
        val p = mediaSoundButton.localToScreen(10, 0)
        volumePopup.show(mediaSoundButton, p.x, p.y)
      }
      
      val fullscreenButton = root.lookup(".media-fullscreen-button").asInstanceOf[Button]
      
      content.foreach { node =>
        if (root.children.size == 1) {
          if (node != null) root.children.add(0, node)
        } else {
          if (node != null) root.children.set(0, node)
          else root.children.remove(0)
        }
      }
      val c = content.get
      content.set(null)
      content.set(c) //want to cause the listener to trigger
      
      root
    }
    
    def millisToString(millis: Long) = {
      val seconds = millis / 1000
      val days = if (seconds >= 3600*24) (seconds / (3600*24)) + ":" else ""
      val hours = if (seconds >= 3600) (seconds / 3600) + ":" else ""
      f"${days}${hours}${(seconds % 3600) / 60}%02d:${seconds % 60}%02d"
    }
  }
}
