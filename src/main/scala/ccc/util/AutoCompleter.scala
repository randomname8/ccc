package ccc
package util

import javafx.animation.{Timeline, KeyFrame}
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.event.EventHandler
import javafx.scene.control.{ListView, TextInputControl}
import javafx.scene.input.{KeyEvent, KeyCode}
import javafx.stage.{Popup, PopupWindow}
import javafx.util.Duration
import scala.collection.JavaConverters._

/**
 * Implements an auto completer for arbitrary text inputs.
 * 
 * The auto completion works on words separated by whitespace (regex \s)
 */
object AutoCompleter {

  type FullText = String
  type Word = String
  type IndexInText = Int
  
  def install(textInput: TextInputControl)(
    completionProvider: (FullText, Word, IndexInText) => Seq[String]): Instance = {
    
    val completionPopup = new Popup()
    completionPopup.anchorLocation = PopupWindow.AnchorLocation.WINDOW_BOTTOM_LEFT
    completionPopup.hideOnEscape = true
    completionPopup.autoHide = true
    completionPopup.width = textInput.getWidth
    textInput.widthProperty foreach (n => completionPopup.setWidth(n.doubleValue))
    val completionList = new ListView[String]
    completionList.prefWidthProperty bind textInput.widthProperty
    completionPopup.content.add(completionList)
    
    var completionForWord: Word = null
    def calculatePopupContent() = {
      val pos = textInput.getCaretPosition
      val items = {
        if (textInput.getLength == 0) Seq.empty
        else {
          val prefixText = textInput.getText(0, pos)
          if (prefixText.substring(pos -1) matches "\\s") Seq.empty
          else {
            completionForWord = prefixText.split("\\s").last
            completionProvider(textInput.getText, completionForWord, pos)
          }
        }
      }

      completionList.items.clear()
      if (items.nonEmpty) {
        completionList.items.addAll(items:_*)
        completionPopup setHeight completionList.getPrefHeight
        val point = textInput.localToScreen(0, 0)
        completionPopup.show(textInput, point.getX, point.getY)
      } else completionPopup.hide()
    }
    
    val popupShowTask = new Timeline(new KeyFrame(Duration.millis(250), evt => calculatePopupContent()))
    
    val textChangeListener: ChangeListener[String] = (_, _, t) => if (textInput.isFocused()) {
      if (completionPopup.isShowing) Platform.runLater(() => calculatePopupContent())  //let this event finish being processed and then calculate
      else popupShowTask.playFromStart()
    }
    val focusChangeListener: ChangeListener[java.lang.Boolean] = (_, _, b) => if (!b) completionPopup.hide()
    val tabKeyFilter: EventHandler[KeyEvent] = evt => {
      if (completionPopup.isShowing) {
        val captured: Boolean = evt.getCode match {
          case KeyCode.TAB | KeyCode.ENTER =>
            Option(completionList.getSelectionModel.getSelectedItem).orElse(completionList.items.asScala.headOption) foreach { selected =>
              val toComplete = selected.stripPrefix(completionForWord)
              textInput.insertText(textInput.getCaretPosition, toComplete + " ")
            }
            true
          case KeyCode.ESCAPE => true
          case _ => false
        }
        if (captured) {
          completionPopup.hide()
          evt.consume()
        }
      }
    }
    
    textInput.addEventFilter(KeyEvent.KEY_RELEASED, tabKeyFilter)
    textInput.textProperty addListener textChangeListener
    textInput.focusedProperty addListener focusChangeListener
    
    new Instance(completionPopup, completionList, () => {
        textInput.removeEventFilter(KeyEvent.KEY_RELEASED, tabKeyFilter)
        textInput.textProperty removeListener textChangeListener
        textInput.focusedProperty removeListener focusChangeListener
      })
  }
  
  class Instance private[AutoCompleter](val popup: Popup, val completionsList: ListView[String], dispose: () => Unit) {
    def uninstall(): Unit = dispose()
  }
}
