package ccc
package util

import javafx.application.Platform
import javafx.scene.control.{Label, ProgressIndicator}
import javafx.scene.image.{Image, ImageView}
import javafx.scene.layout.StackPane
import uk.co.caprica.vlcj.component.DirectMediaPlayerComponent
import uk.co.caprica.vlcj.player.direct.DefaultDirectMediaPlayer
import uk.co.caprica.vlcj.player.direct.format.RV32BufferFormat

/**
 * A MediaPlayer configuration for a vlc DirectMediaPlayerComponent.
 */
class VlcMediaPlayer extends MediaPlayer {
  val mediaPlayerComponent = new DirectMediaPlayerComponent(new RV32BufferFormat(_, _)) {
    override def finished(mediaPlayer) = Platform.runLater { () =>
      currentTime.set(totalDuration.get)
      VlcMediaPlayer.this.playing.set(false)
    }
    override def error(player) = Platform.runLater { () =>
      VlcMediaPlayer.this.playing.set(false)
      errorLabel setVisible true
    }
    override def lengthChanged(player, length) = Platform.runLater { () =>
      totalDuration set length
    }
    override def buffering(player, newCache) = Platform.runLater { () =>
      bufferingStatus setVisible newCache != 100
    }
    override def playing(player) = Platform.runLater { () =>
      errorLabel setVisible false
      bufferingStatus setVisible false
      thumbnailImageView setVisible false
      VlcMediaPlayer.this.playing.set(true)
    }
    override def paused(player) = Platform.runLater { () =>
      VlcMediaPlayer.this.playing.set(false)
    }
    override def stopped(player) = Platform.runLater { () =>
      VlcMediaPlayer.this.playing.set(false)
    }
    override def volumeChanged(player, newVolume) = Platform.runLater { () =>
      val currVolume = player.getVolume / 100.0f
      if (volume.get != currVolume) volume set currVolume
    }
    override def muted(player, muted) = Platform.runLater { () =>
      if (muted) volume set 0
    }
    override def positionChanged(player, newPosition) = Platform.runLater { () =>
      currentTime set (totalDuration.get * newPosition).toLong
    }
  }
  val mediaPlayer = mediaPlayerComponent.getMediaPlayer.asInstanceOf[DefaultDirectMediaPlayer]
  
  private val contentStackPane = new StackPane()
  private val thumbnailImageView = new ImageView().modify(_.setPreserveRatio(true), _.setSmooth(true), _ setVisible false,
                                                          _.fitWidthProperty bind contentStackPane.widthProperty,
                                                          _.fitHeightProperty bind contentStackPane.heightProperty)
  private val bufferingStatus = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS).modify(_ setVisible false)
  private val errorLabel = new Label("An error occurred").modify(_ setVisible false)
  private val textureNode = new TextureNode(new VlcTexture(mediaPlayer), 60)
  playing foreach (b => if (b) textureNode.renderer.start() else textureNode.renderer.stop())
  contentStackPane.getChildren.addAll(thumbnailImageView, textureNode, bufferingStatus, errorLabel)
  content set contentStackPane
  
  volume.foreach { v => mediaPlayer setVolume (v.floatValue * 2).toInt }
  onPlay set { () =>
    if (mediaPlayer.isPlaying) mediaPlayer.pause()
    else mediaPlayer.play()
  }
  onSeeking set { time => 
    if ((mediaPlayer.getTime - time).abs > 100) { //avoid the loop positionChanged → slider updated → setTime
      mediaPlayer.setTime(time)
    }
  }
  
  def setMedia(mediaUrl: String, thumbnailImage: Option[Image]): Unit = {
    mediaPlayer.prepareMedia(mediaUrl, "--live-caching")
    thumbnailImage foreach { img =>
      thumbnailImageView.setImage(img)
      thumbnailImageView setVisible true
    }
  }
  
  def dispose(): Unit = {
    mediaPlayerComponent.release()
    mediaPlayer.stop()
    mediaPlayer.release()
  }
}
