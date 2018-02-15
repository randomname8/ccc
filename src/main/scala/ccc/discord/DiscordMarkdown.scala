package ccc.util

object DiscordMarkdown {

  /**
   * Apply necessary transformations to the Discord supported markdown to transform it to standard markdown.
   */
  def adaptToMarkdown(discordMarkdown: String): String = {
    discordMarkdown.replace(" ```", "\n```")
  }
}
