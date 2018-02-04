package ccc.util

import better.files._
import com.univocity.parsers.csv.{CsvParser, CsvParserSettings}
import scala.collection.JavaConverters._

object EmojiOne {

  case class Description(filename: String, unicode: String, name: String, alphaCode: String, aliases: Array[String])
  
  val (allEmojis, emojiLookup) = {
    val settings = new CsvParserSettings()
    settings.getFormat.setLineSeparator("\n")
    val parser = new CsvParser(settings)
    
    val noAliases = Array.empty[String]
    val descriptions = resourceAsStream("emojione/eac.csv").autoClosed { in =>
      parser.iterate(in, "utf-8").iterator.asScala.drop(1).map(row =>
        Description(row(0), parseUnicode(row(1)), row(2), row(3),
                    Option(row(4)).map(_.split("\\|")).getOrElse(noAliases))).toArray
    }
    val indexedByUnicode = descriptions.map(e => e.unicode -> e).toMap
    val indexedByAlphaCode = descriptions.map(e => e.alphaCode -> e).toMap
    val indexedByAlias = descriptions.flatMap(e => e.aliases.map(_ -> e)).toMap
    val emojiLookup = indexedByUnicode ++ indexedByAlias ++ indexedByAlphaCode
    (descriptions, emojiLookup)
  }
  
  /**
   * Parses the unicode representation of emoji one into a javascript with the encoded unicodes.
   * This involves a bit of processing since java's char are always 2 bytes
   */
  private def parseUnicode(unicode: String): String = {
    if (unicode.isEmpty) return unicode
    
    unicode.split("-").foldLeft(new StringBuilder()) { (sb, s) =>
      val part = Integer.parseInt(s, 16)
      if (part >= 0x10000 && part <= 0x10FFFF) {
        val hi = ((part - 0x10000) / 0x400 + 0xD800).toInt
        val lo = ((part - 0x10000) % 0x400) + 0xDC00
        sb append new String(Character.toChars(hi)) append new String(Character.toChars(lo))
      } else {
        sb append new String(Character.toChars(part))
      }
      sb
    }.result()
  }
}
