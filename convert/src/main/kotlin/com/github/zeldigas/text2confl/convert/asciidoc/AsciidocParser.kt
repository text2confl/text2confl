package com.github.zeldigas.text2confl.convert.asciidoc

import com.github.zeldigas.text2confl.convert.confluence.LanguageMapper
import com.vladsch.flexmark.util.sequence.Escaping.unescapeHtml
import org.asciidoctor.*
import org.asciidoctor.ast.Document
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.div


class AsciidocParser(
    private val config: AsciidoctorConfiguration
) {

    companion object {
        private val TEMPLATES_LOCATION = "com/github/zeldigas/text2confl/asciidoc"
    }

    private val ADOC: Asciidoctor by lazy {
        Asciidoctor.Factory.create().also { asciidoc ->
            config.libsToLoad.forEach { asciidoc.requireLibrary(it) }
            if (config.loadBundledMacros) {
                DefaultMacros().register(asciidoc)
            }
        }
    }

    private val templatesLocation: Path by lazy {
        val templateResources = AsciidocParser::class.java.classLoader.getResource(TEMPLATES_LOCATION)!!.toURI()
        if (templateResources.scheme == "file") {
            val mainPath: String = Paths.get(templateResources).toString()
            Path(mainPath)
        } else {
            val dest = config.workdir / "templates"

            extractTemplatesTo(dest, templateResources, TEMPLATES_LOCATION)

            dest
        }
    }

    fun parseDocumentHeader(file: Path): Document {
        return ADOC.loadFile(file.toFile(), headerParsingOptions())
    }

    private fun headerParsingOptions() = parserOptions { }

    fun parseDocument(file: Path, parameters: AsciidocRenderingParameters): Document {
        return ADOC.loadFile(file.toFile(), htmlConversionOptions(parameters))
    }

    fun parseDocument(source: String, parameters: AsciidocRenderingParameters): Document {
        return ADOC.load(source, htmlConversionOptions(parameters))
    }

    private fun htmlConversionOptions(parameters: AsciidocRenderingParameters) = parserOptions {
        //TODO  Add those config.attributes + parameters.extraAttrs
        attributes(
            Attributes.builder()
                    .attribute("t2c-language-mapper",parameters.languageMapper)
                    .attribute( "t2c-ref-provider",parameters.referenceProvider)
                    .attribute("t2c-attachments-collector", parameters.attachmentsCollector)
                    .attribute("t2c-space",parameters.space)
                    .attribute("t2c-decoder",Converter)
                    .attribute("idseparator","-")
                    .attribute("idprefix", "")
                .sourceHighlighter("none")
                .build()
        )
        templateDirs(templatesLocation.toFile())
    }

    private fun parserOptions(configurer: OptionsBuilder.() -> Unit) = Options.builder()
        .safe(SafeMode.UNSAFE)
        .backend("xhtml5")
        .also(configurer)
        .build()

}

data class AsciidocRenderingParameters(
    val languageMapper: LanguageMapper,
    val referenceProvider: AsciidocReferenceProvider,
    val autoText: String,
    val includeAutoText: Boolean,
    val space: String,
    val attachmentsCollector: AsciidocAttachmentCollector,
    val extraAttrs: Map<String, Any?> = emptyMap()
)

object Converter {
    fun convert(string: String): String = unescapeHtml(string)
}