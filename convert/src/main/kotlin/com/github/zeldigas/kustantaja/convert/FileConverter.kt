package com.github.zeldigas.kustantaja.convert

import com.github.zeldigas.kustantaja.convert.confluence.LanguageMapper
import com.github.zeldigas.kustantaja.convert.confluence.ReferenceProvider
import java.nio.file.Path

interface FileConverter {

    fun readHeader(file: Path, context: HeaderReadingContext): PageHeader

    fun convert(file: Path, context: ConvertingContext): PageContent

}

data class HeaderReadingContext(
    val titleTransformer: (Path, String) -> String
)

data class ConvertingContext(
    val referenceProvider: ReferenceProvider,
    val languageMapper: LanguageMapper,
    val targetSpace: String,
    val titleTransformer: (Path, String) -> String
)
