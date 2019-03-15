package com.ullink.gradle.symstoreserver

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.gradle.api.GradleException
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.TaskAction
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.ContentType

class UploadSymbolTask extends ConventionTask {
    public static final String PRODUCT_NAME_FIELD = 'product_name'

    public static final String PRODUCT_VERSION_FIELD = 'product_version'

    public static final String ZIP_FIELD = 'zip'

    public static final String COMMENT_FIELD = 'comment'

    String serverUrl

    String product_name

    String product_version

    String comment

    String zipPath

    // unit tests purposes
    Closure<HTTPBuilder> createHttpBuilder = { url -> new HTTPBuilder(url)}

    @TaskAction
    void upload() {
        def url = new URI(serverUrl + '/cgi-bin/add.py').normalize()
        def zipFile = zipPath ? new File(zipPath) : null
        if(!zipFile?.exists())
            throw new GradleException("$zipPath doesn't exits")

        logger.info("Sending $zipPath to $serverUrl")
        createHttpBuilder(url.toString()).request(Method.POST) { req ->
            def builder = MultipartEntityBuilder.create()
                    .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                    .addBinaryBody(ZIP_FIELD, zipFile)
                    .setContentType(ContentType.MULTIPART_FORM_DATA)
            if(product_name)
                builder.addTextBody(PRODUCT_NAME_FIELD, product_name)
            if(product_version)
                builder.addTextBody(PRODUCT_VERSION_FIELD, product_version)
            if(comment)
                builder.addTextBody(COMMENT_FIELD, comment)
            def entity = builder.build()
            logger.info("Sending: entity")
            req.setEntity(entity)
            response.success = { _, reader ->
                if(reader.status != 'success')
                    throw new GradleException(reader.message.toString())
                logger.info('Request succeed.')
            }
            response.failure = { r ->
                throw new GradleException('Request failed: ' + r.toString())
            }
        }
    }
}
