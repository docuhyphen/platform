package com.docuhyphen.app.api.service.communication

import jakarta.enterprise.context.ApplicationScoped
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

@ApplicationScoped
class MarkdownRenderer
{
    private val parser: Parser = Parser.builder().build()
    private val renderer: HtmlRenderer = HtmlRenderer.builder().sanitizeUrls(true).build()

    fun toHtml(markdown: String): String = renderer.render(parser.parse(markdown))
}
