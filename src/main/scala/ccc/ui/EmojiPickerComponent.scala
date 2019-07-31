package ccc
package ui

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control._
import javafx.scene.layout._
import scala.util.chaining._
import tangerine._

class EmojiPickerComponent[ButtonRow] extends UiComponent {

  val emojiPicker = new TableView[ButtonRow]().tap(_.getStyleClass add "emoji-picker")
  val searchTextField = new TextField().tap(_.getStyleClass add "emoji-picker-search")
  val component: Pane = new BorderPane().tap { bp =>
    bp.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE)
    bp.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE)
    
    bp.setCenter(emojiPicker.tap { p => BorderPane.setAlignment(p, Pos.CENTER); BorderPane.setMargin(p, new Insets(10)) })
    bp.setBottom(searchTextField.tap { p => BorderPane.setAlignment(p, Pos.CENTER); BorderPane.setMargin(p, new Insets(10)) })
  }
}
