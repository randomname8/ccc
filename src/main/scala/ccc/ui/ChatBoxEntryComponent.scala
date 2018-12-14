package ccc
package ui

import javafx.geometry.Pos
import javafx.scene.control._
import javafx.scene.image.Image
import javafx.scene.layout._
import tangerine._, JfxControls._

private[ccc] class ChatBoxEntryComponent extends UiComponent {

  val avatarPane: Pane = new StackPane().tap(_.getStyleClass add "avatar-pane")
  val userLabel: Label = new Label().tap(_.getStyleClass add "user-label")
  val chatDateLabel: Label = new Label().tap(_.getStyleClass add "chat-date-label")
  val entries: VBox = vbox()(Pos.TOP_LEFT, fillWidth = true).tap(_.getStyleClass add "entries-vbox")
  val component: Pane = hbox(
    avatarPane.tap(HBox.setMargin(_, Margin(right = 1.em))),
    new BorderPane().tap { bp =>
      HBox.setHgrow(bp, Priority.ALWAYS)
      bp setTop hbox(userLabel, chatDateLabel)(Pos.BASELINE_LEFT, spacing = 1.em).tap { n => 
        BorderPane.setAlignment(n, Pos.CENTER); BorderPane.setMargin(n, Margin(bot = 0.5.em)) }
      bp setCenter entries.tap { v => BorderPane.setAlignment(v, Pos.CENTER); BorderPane.setMargin(v, Margin(left = 1.em)) }
    }
  )(Pos.TOP_LEFT, fillHeight = false).tap { box => 
    box.setMaxSize(Double.MaxValue, Region.USE_PREF_SIZE)
    box.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE)
    box.getStyleClass add "chat-list-entry"
  }
  
  override def setupSample() = {
    userLabel.setText("User")
    chatDateLabel.setText("Today at 3pm")
    avatarPane.setBackground(imageBackground(new Image("https://cdn.discordapp.com/avatars/183411122848661505/81e6a9370e6a54ea19b3acad6c811e61.png?size=64")))
    avatarPane.setPrefSize(32, 32)
    entries.getChildren.addAll(new Label("text1"), new Label("text2"), new Label("text3"))
  }
}
