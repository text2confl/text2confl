package com.github.zeldigas.text2confl.convert.markdown

import assertk.assertThat
import com.github.zeldigas.text2confl.convert.confluence.LanguageMapper
import org.junit.jupiter.api.Test

internal class RenderingOfCodeBlocksTest : RenderingTestBase() {


    @Test
    internal fun `Fenced code block with language tag rendering`() {
        val result = toHtml(
            """
            ```kotlin
            println("Hello")
            println("world")
            ```
        """.trimIndent()
        )

        assertThat(result).isEqualToConfluenceFormat(
            """
            <ac:structured-macro ac:name="code"><ac:parameter ac:name="language">kotlin</ac:parameter><ac:plain-text-body><![CDATA[println("Hello")
            println("world")]]></ac:plain-text-body></ac:structured-macro>
        """.trimIndent()
        )

        val resultWithUndefinedLang = toHtml(
            """
            ```ruby
            puts "Hello"           
            ```
        """.trimIndent()
        )

        assertThat(resultWithUndefinedLang).isEqualToConfluenceFormat(
            """
            <ac:structured-macro ac:name="code"><ac:parameter ac:name="language">fallback</ac:parameter><ac:plain-text-body><![CDATA[puts "Hello"]]></ac:plain-text-body></ac:structured-macro>
        """.trimIndent()
        )

        val resultWithNullLang = toHtml(
            """
            ```ruby
            puts "Hello"           
            ```
        """.trimIndent(), languageMapper = LanguageMapper.nop()
        )

        assertThat(resultWithNullLang).isEqualToConfluenceFormat(
            """
            <ac:structured-macro ac:name="code"><ac:plain-text-body><![CDATA[puts "Hello"]]></ac:plain-text-body></ac:structured-macro>
        """.trimIndent()
        )
    }

    @Test
    internal fun `Fenced code block without language tag rendering`() {
        val result = toHtml(
            """
            ```
            A text in code
            block
            ```
        """.trimIndent()
        )

        assertThat(result).isEqualToConfluenceFormat(
            """
            <ac:structured-macro ac:name="code"><ac:plain-text-body><![CDATA[A text in code
            block]]></ac:plain-text-body></ac:structured-macro>
        """.trimIndent()
        )
    }

    @Test
    internal fun `Simple code block is rendered as pretext`() {
        val result = toHtml(
            """
            |    Text that will be rendered as pre
            |    code
        """.trimMargin("|")
        )

        assertThat(result).isEqualToConfluenceFormat(
            """
            <pre><code>Text that will be rendered as pre
            code
            </code></pre>
        """.trimIndent()
        )
    }

}

