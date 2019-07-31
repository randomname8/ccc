package ccc

import javafx.beans.property.SimpleObjectProperty
import javafx.event.{EventHandler, Event, EventType}
import javafx.scene.control.{Control, Button, TableColumn, TableCell}
import javafx.scene.image.Image
import javafx.scene.image.ImageView

import scala.jdk.CollectionConverters._
import tangerine._, Properties._

object EmojiPicker {
  val PickedEmojiEventType = new EventType("PickedEmojiEventType")
  case class PickedEmojiEvent(emoji: String) extends Event(PickedEmojiEventType)
}
class EmojiPicker(val emojiProvider: Map[String, Image]) extends Control {
  import EmojiPicker._
  val onIconPickedProperty = new SimpleObjectProperty[EventHandler[PickedEmojiEvent]]
  def onIconPicked: EventHandler[PickedEmojiEvent] = onIconPickedProperty.get
  def onIconPicked_=(handler: EventHandler[PickedEmojiEvent]) = onIconPickedProperty.set(handler)
  
  override protected def createDefaultSkin = Skin
  
  object Skin extends javafx.scene.control.Skin[EmojiPicker] {
    val EmojisPerRow = 5
    val EmojiWidth = 2.em
    override def getSkinnable = EmojiPicker.this
    override def dispose = ()
    override val getNode = {
      val picker = new ui.EmojiPickerComponent[ButtonRow]()
      picker.component setFocusTraversable false
      val tableView = picker.emojiPicker
      tableView setFocusTraversable false
      val searchField = picker.searchTextField
      
      
      def firePickedImage(image: String): Unit = {
        Option(onIconPicked) foreach (_.handle(PickedEmojiEvent(image)))
        searchField.setText("")
      }
      
      val columns = tableView.getColumns
      for (i <- 0 until EmojisPerRow) { //configure the columns with the cellValueFactory and the cellFactory with the buttons
        val column = new TableColumn[ButtonRow, String](i.toString)
        column setCellValueFactory { features => new SimpleObjectProperty(features.getValue.buttons.drop(i).take(1).headOption.getOrElse(null)) }
        column setCellFactory { _ => new TableCell[ButtonRow, String] {
          val btn = new Button()
          btn.getStyleClass.add("emoji-picker-selection-button")
          override protected def updateItem(item: String, empty: Boolean): Unit = {
            super.updateItem(item, empty)
            if (!empty && item != null) {
              val iv = new ImageView(emojiProvider(item))
              iv.setSmooth(true)
              iv.setPreserveRatio(true)
              iv.setFitWidth(EmojiWidth)
              
              btn.setGraphic(iv)
              btn setOnAction { evt => firePickedImage(item) }
              setGraphic(btn)
              
            } else setGraphic(null)
          }
        }}
        columns.add(column)
      }
      
      val emojis = util.EmojiOne.emojiByAlphaCode.keys.toSeq.sorted
      val allGroups = emojis.grouped(EmojisPerRow).map(ButtonRow).toArray
      tableView.getItems.addAll(allGroups:_*)
      
      searchField.textProperty.addListener { (_, _, text) =>
        tableView.getItems.clear()
        if (text.isEmpty) tableView.getItems.addAll(allGroups:_*)
        else {
          tableView.getItems.addAll(emojis.filter(_ contains text).grouped(EmojisPerRow).map(ButtonRow).toArray:_*)
        }
      }
      searchField setOnAction { evt => {
        if (searchField.getText.nonEmpty) for {
          row <- tableView.getItems.asScala.headOption
          img <- row.buttons.headOption
        } {
          firePickedImage(img)
        }
      }}
    
      
      val width = Binding(columns.asScala.map(_.widthProperty).toSeq:_*) { _ => columns.asScala.map(_.getWidth).sum } + EmojisPerRow * 0.6.em
      picker.component.prefWidthProperty bind width
      
      JfxUtils.showingProperty(picker.component).foreach(b =>  if (b) searchField.requestFocus())
      picker.component
    }
    
    case class ButtonRow(buttons: Seq[String])
  }
}
