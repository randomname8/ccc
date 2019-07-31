package ccc
package ui

import javafx.geometry.Pos
import javafx.scene.control._
import javafx.scene.layout._
import scala.util.chaining._
import tangerine._

private[ccc] class ChatMessageComponent extends UiComponent {

  val chatMessages: Pane = new VBox().tap(_.getStyleClass add "chat-message")
  val chatMessageControls: Pane = new HBox().tap{ b => 
    b.getStyleClass add "chat-message-controls-pane"
    b.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE)
  }
  val component: Pane = new StackPane().tap { sp =>
    sp.setMaxSize(Double.MaxValue, Double.MaxValue)
    sp.getStyleClass add "chat-message-container"
    sp.getChildren.addAll(
      chatMessages,
      chatMessageControls.tap { p => StackPane.setAlignment(p, Pos.TOP_RIGHT); StackPane.setMargin(p, Margin(right = 1.em)) }
    )
  }
  
  override def setupSample() = {
    chatMessages.getChildren.addAll(
      new Label("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua"),
      new Label("text2"),
      new Label("text3"))
    
    chatMessageControls.getChildren.addAll(new Button("⎘"), new Button("🗏"), new Button("🗐"))
    
    chatMessageControls.visibleProperty bind component.hoverProperty
  }
}
