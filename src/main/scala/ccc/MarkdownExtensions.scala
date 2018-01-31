package ccc

import org.commonmark.ext.ins.Ins
import org.commonmark.node.Text
import org.commonmark.parser.delimiter.DelimiterProcessor
import org.commonmark.parser.delimiter.DelimiterRun

object MarkdownExtensions {

  object InsDelimiterProcessor extends DelimiterProcessor {
    override def getOpeningCharacter = '_'
    override def getClosingCharacter = '_'
    override def getMinLength = 2
    override def getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun) = {
      if (opener.length >= 2 && closer.length >= 2) {
        // Use exactly two delimiters even if we have more, and don't care about internal openers/closers.
        2
      } else 0
    }
    
    override def process(opener: Text, closer: Text, delimiterCount: Int) = {
      // Wrap nodes between delimiters in ins.
      val ins = new Ins()

      var tmp = opener.getNext()
      while (tmp != null && tmp != closer) {
        val next = tmp.getNext()
        ins.appendChild(tmp)
        tmp = next
      }
      opener.insertAfter(ins)
    }
  }
}
