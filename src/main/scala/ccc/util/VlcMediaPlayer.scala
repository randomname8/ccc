package ccc
package util

import javafx.application.Platform
import javafx.scene.control.{Label, ProgressIndicator}
import javafx.scene.image.{Image, ImageView}
import javafx.scene.layout.StackPane
import uk.co.caprica.vlcj.component.DirectMediaPlayerComponent
import uk.co.caprica.vlcj.player.{MediaPlayer => VlcPlayer}
import uk.co.caprica.vlcj.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.direct.DefaultDirectMediaPlayer
import uk.co.caprica.vlcj.player.direct.format.RV32BufferFormat
import tangerine._

object VlcMediaPlayer {
  private[VlcMediaPlayer] lazy val runNativeDiscovery = new NativeDiscovery().discover()
}

/**
 * A MediaPlayer configuration for a vlc DirectMediaPlayerComponent.
 */
class VlcMediaPlayer extends MediaPlayer {
  VlcMediaPlayer.runNativeDiscovery //run this only once
  val mediaPlayerComponent = new DirectMediaPlayerComponent(new RV32BufferFormat(_, _)) {
    override def finished(mediaPlayer: VlcPlayer) = Platform.runLater { () =>
      currentTime.set(totalDuration.get)
      VlcMediaPlayer.this.playing.set(false)
    }
    override def error(player: VlcPlayer) = Platform.runLater { () =>
      VlcMediaPlayer.this.playing.set(false)
      errorLabel setVisible true
    }
    override def lengthChanged(player: VlcPlayer, length: Long) = Platform.runLater { () =>
      totalDuration set length
    }
    override def buffering(player: VlcPlayer, newCache: Float) = Platform.runLater { () =>
      bufferingStatus setVisible newCache != 100
    }
    override def playing(player: VlcPlayer) = Platform.runLater { () =>
      errorLabel setVisible false
      bufferingStatus setVisible false
      thumbnailImageView setVisible false
      VlcMediaPlayer.this.playing.set(true)
    }
    override def paused(player: VlcPlayer) = Platform.runLater { () =>
      VlcMediaPlayer.this.playing.set(false)
    }
    override def stopped(player: VlcPlayer) = Platform.runLater { () =>
      VlcMediaPlayer.this.playing.set(false)
    }
    override def volumeChanged(player: VlcPlayer, newVolume: Float) = Platform.runLater { () =>
      val currVolume = newVolume / 100.0f
      if (volume.get != currVolume) volume set currVolume
    }
    override def muted(player: VlcPlayer, muted: Boolean) = Platform.runLater { () =>
      if (muted) volume set 0
    }
    override def positionChanged(player: VlcPlayer, newPosition: Float) = Platform.runLater { () =>
      currentTime set (totalDuration.get * newPosition).toLong
    }
  }
  val mediaPlayer = mediaPlayerComponent.getMediaPlayer.asInstanceOf[DefaultDirectMediaPlayer]
  
  private val contentStackPane = new StackPane()
  private val thumbnailImageView = new ImageView().tap { i => 
    i.setPreserveRatio(true); i.setSmooth(true); i setVisible false
    i.fitWidthProperty bind contentStackPane.widthProperty
    i.fitHeightProperty bind contentStackPane.heightProperty
  }
  private val bufferingStatus = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS).tap(_ setVisible false)
  private val errorLabel = new Label("An error occurred").tap(_ setVisible false)
  private val textureNode = new TextureNode(new VlcTexture(mediaPlayer), 60)
  playing foreach (b => if (b) textureNode.renderer.start() else textureNode.renderer.stop())
  contentStackPane.getChildren.addAll(thumbnailImageView, textureNode, bufferingStatus, errorLabel)
  content set contentStackPane
  
//  volume.foreach { v => mediaPlayer setVolume (v.floatValue * 2).toInt }
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
    mediaPlayer.stop()
    mediaPlayer.release()
    mediaPlayerComponent.release()
  }
}
