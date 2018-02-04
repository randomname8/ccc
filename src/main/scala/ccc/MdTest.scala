package ccc

import org.commonmark.node._
import org.commonmark.parser.Parser
object MdTest extends App {
  val parser = Parser.builder.extensions(java.util.Arrays.asList(
      org.commonmark.ext.autolink.AutolinkExtension.create(),
      org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create())).customDelimiterProcessor(MarkdownExtensions.InsDelimiterProcessor).build()
  val node = parser.parse("""some text with `inlined text` here, and a code block too 
```scala
def haha = "as if"
```""")
  node accept new AbstractVisitor {
    override def visit(t: Paragraph) = {
      visitChildren(t)
      println("\n")
    }
    override def visit(t: Text) = print(t.getLiteral)
    override def visit(t: Code) = print("Code{" + t.getLiteral + "}")
    override def visit(t: FencedCodeBlock) = print(s"Fenced(${t.getInfo}){${t.getLiteral}")
    override def visitChildren(node: Node): Unit = {
      print(node.getClass.getSimpleName + "{")
      super.visitChildren(node)
      print("}")
    }
  }
}
