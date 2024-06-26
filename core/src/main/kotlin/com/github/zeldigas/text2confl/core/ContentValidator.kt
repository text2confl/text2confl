package com.github.zeldigas.text2confl.core

import com.github.zeldigas.text2confl.convert.Page
import com.github.zeldigas.text2confl.convert.Validation
import io.github.oshai.kotlinlogging.KotlinLogging
import com.github.zeldigas.text2confl.core.upload.ContentUploadException
import com.github.zeldigas.text2confl.model.ConfluencePage

class ContentValidationFailedException(val errors: List<String>) : RuntimeException()

interface ContentValidator {
    fun validate(content: List<Page>)
    fun fixHtml(content: List<Page>)
    fun checkNoClashWithParent(publishUnder: ConfluencePage, pagesToPublish: List<Page>)
}

class ContentValidatorImpl : ContentValidator {

    companion object {
        private val logger = KotlinLogging.logger { }
    }
    override fun validate(content: List<Page>) {
        val foundIssues: MutableList<String> = arrayListOf()
        collectErrors(content, foundIssues)
        if (foundIssues.isNotEmpty()) {
            throw ContentValidationFailedException(foundIssues)
        }
    }

    override fun fixHtml(content: List<Page>) {
        logger.info { "Fixing html pages : " + content.size }
        for (page in content){
            page.content.fixHtml()
            fixHtml(page.children)
        }
    }

    private fun collectErrors(pages: List<Page>, foundIssues: MutableList<String>) {
        for (page in pages) {
            logger.debug {  "Validating : ${page.source}: "}
            val validationResult = page.content.validate()
            if (validationResult is Validation.Invalid) {
                logger.debug { validationResult.issue }
                foundIssues.add("${page.source}: ${validationResult.issue}")
            }
            collectErrors(page.children, foundIssues)
        }
    }

    override fun checkNoClashWithParent(publishUnder: ConfluencePage, pagesToPublish: List<Page>) {
        val conflictWithParent = pagesToPublish.find { it.title == publishUnder.title } ?: return

        throw ContentUploadException("Page to publish clashes with parent under which pages will be published. Problem file - ${conflictWithParent.source}, parent confluence page - ${publishUnder.title} (id=${publishUnder.id})")
    }
}