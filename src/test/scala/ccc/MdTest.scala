package ccc

import org.commonmark.node._
import org.commonmark.parser.Parser
object MdTest extends App {
  val parser = Parser.builder.extensions(java.util.Arrays.asList(
      org.commonmark.ext.autolink.AutolinkExtension.create(),
      org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create())).customDelimiterProcessor(MarkdownExtensions.InsDelimiterProcessor).build()
  val node = parser.parse("""
![alt text](https://lord2015.files.wordpress.com/2015/02/totoro_wallpaper_by_vampiresuper_sayajin-d6gx09h.png "Totoro!")
>quoted text!
                          
non quoted text
                          
>
""".trim)
  node accept new AbstractVisitor {
    override def visit(t: Paragraph) = {
      visitChildren(t)
      println("\n")
    }
    override def visit(t: Text) = print("Text{" + t.getLiteral + "}")
    override def visit(t: Code) = print("Code{" + t.getLiteral + "}")
    override def visit(t: FencedCodeBlock) = print(s"Fenced(${t.getInfo}){${t.getLiteral}")
    override def visit(t: Image) = {println(t); visitChildren(t)}
    override def visitChildren(node: Node): Unit = {
      print(node.getClass.getSimpleName + "{")
      super.visitChildren(node)
      print("}")
    }
  }
}
