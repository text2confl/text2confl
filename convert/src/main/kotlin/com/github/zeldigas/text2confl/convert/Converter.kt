package com.github.zeldigas.text2confl.convert

import com.github.zeldigas.text2confl.convert.confluence.LanguageMapper
import com.github.zeldigas.text2confl.convert.confluence.ReferenceProvider
import com.github.zeldigas.text2confl.convert.markdown.MarkdownFileConverter
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

interface Converter {

    fun convertFile(file: Path): Page

    fun convertDir(dir: Path): List<Page>

}

class FileDoesNotExistException(val file: Path) : RuntimeException("File does not exist: $file")

fun universalConverter(
    space: String,
    languageMapper: LanguageMapper,
    titleConverter: (Path, String) -> String = { _, title -> title },
): Converter {
    return UniversalConverter(space, languageMapper, titleConverter, mapOf(
        "md" to MarkdownFileConverter()
    ))
}

internal class UniversalConverter(
    val space: String,
    val languageMapper: LanguageMapper,
    val titleConverter: (Path, String) -> String,
    val converters: Map<String, FileConverter>
) : Converter {

    override fun convertFile(file: Path): Page {
        val converter = converterFor(file)
        if (!file.exists()) {
            throw FileDoesNotExistException(file)
        }

        return Page(
            converter.convert(file, ConvertingContext(ReferenceProvider.nop(), languageMapper, space, titleConverter)),
            file,
            emptyList()
        )
    }

    override fun convertDir(dir: Path): List<Page> {
        val documents = scanDocuments(dir)

        return convertFilesInDirectory(
            dir,
            ConvertingContext(ReferenceProvider.fromDocuments(dir, documents), languageMapper, space, titleConverter)
        )
    }

    private fun scanDocuments(dir: Path) =
        dir.toFile().walk().filter { it.supported() }
            .map { it.toPath() to converters.getValue(it.extension.lowercase()).readHeader(it.toPath(), HeaderReadingContext(titleConverter)) }
            .toMap()

    private fun convertFilesInDirectory(dir: Path, context: ConvertingContext): List<Page> {
        return dir.listDirectoryEntries().filter { it.supported() }.sorted()
            .map { file ->
                val content = convertSupported(file, context)
                val subdirectory = file.parent.resolve(file.nameWithoutExtension)
                val children = if (Files.exists(subdirectory) && Files.isDirectory(subdirectory)) {
                    convertFilesInDirectory(subdirectory, context)
                } else {
                    emptyList()
                }
                Page(content, file, children)
            }
    }

    private fun convertSupported(file: Path, context: ConvertingContext): PageContent {
        return converterFor(file).convert(file, context)
    }

    private fun converterFor(file: Path) =
        converters[file.extension] ?: throw IllegalArgumentException("Unsupported extension: ${file.extension}")

    private fun File.supported() = isFile && !name.startsWith("_") && extension.lowercase() in converters
    private fun Path.supported() = toFile().supported()
}