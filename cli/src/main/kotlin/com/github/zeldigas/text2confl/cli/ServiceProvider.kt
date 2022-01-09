package com.github.zeldigas.text2confl.cli

import com.github.zeldigas.confclient.ConfluenceClient
import com.github.zeldigas.confclient.ConfluenceClientConfig
import com.github.zeldigas.confclient.confluenceClient
import com.github.zeldigas.text2confl.cli.config.ConverterConfig
import com.github.zeldigas.text2confl.cli.config.UploadConfig
import com.github.zeldigas.text2confl.convert.Converter
import com.github.zeldigas.text2confl.convert.universalConverter

interface ServiceProvider {
    fun createConverter(space: String, config: ConverterConfig): Converter
    fun createConfluenceClient(clientConfig: ConfluenceClientConfig): ConfluenceClient
    fun createUploader(
        client: ConfluenceClient,
        uploadConfig: UploadConfig,
        converterConfig: ConverterConfig
    ): ContentUploader
}

class ServiceProviderImpl : ServiceProvider {
    override fun createConverter(space: String, config: ConverterConfig): Converter {
        return universalConverter(
            space = space,
            languageMapper = config.languageMapper,
            titleConverter = config.titleConverter
        )
    }

    override fun createConfluenceClient(clientConfig: ConfluenceClientConfig): ConfluenceClient {
        return confluenceClient(clientConfig)
    }

    override fun createUploader(
        client: ConfluenceClient,
        uploadConfig: UploadConfig,
        converterConfig: ConverterConfig
    ): ContentUploader {
        return ContentUploader(
            client, uploadConfig.uploadMessage, uploadConfig.notifyWatchers,
            uploadConfig.modificationCheck,
            converterConfig.editorVersion
        )
    }
}