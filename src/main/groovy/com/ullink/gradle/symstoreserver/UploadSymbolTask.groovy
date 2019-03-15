package com.ullink.gradle.symstoreserver

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.gradle.api.GradleException
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.TaskAction
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.ContentType
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class UploadSymbolTask extends ConventionTask {
    public static final String PRODUCT_NAME_FIELD = 'product_name'

    public static final String PRODUCT_VERSION_FIELD = 'product_version'

    public static final String ZIP_FIELD = 'zip'

    public static final String COMMENT_FIELD = 'comment'

    String serverUrl

    String product_name

    String product_version

    String comment

    String path

    // for unit tests purposes
    Closure<HTTPBuilder> createHttpBuilder = { url -> new HTTPBuilder(url)}

    @TaskAction
    void upload() {
        def url = new URI(serverUrl + '/cgi-bin/add.py').normalize()
        def pathFile = path ? new File(path) : null
        if (!pathFile?.exists())
            throw new GradleException("$path doesn't exits")

        def tuple = getZipFile(pathFile)
        def zipFile = tuple.first
        def cleanUp = tuple.second

        try {
            logger.info("Sending $zipFile.path to $serverUrl")
            createHttpBuilder(url.toString()).request(Method.POST) { req ->
                def builder = MultipartEntityBuilder.create()
                        .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                        .addBinaryBody(ZIP_FIELD, zipFile)
                        .setContentType(ContentType.MULTIPART_FORM_DATA)
                if (product_name)
                    builder.addTextBody(PRODUCT_NAME_FIELD, product_name)
                if (product_version)
                    builder.addTextBody(PRODUCT_VERSION_FIELD, product_version)
                if (comment)
                    builder.addTextBody(COMMENT_FIELD, comment)
                def entity = builder.build()
                logger.info("Sending: entity")
                req.setEntity(entity)
                response.success = { _, reader ->
                    if (reader.status != 'success')
                        throw new GradleException(reader.message.toString())
                    logger.info('Request succeed.')
                }
                response.failure = { r ->
                    throw new GradleException('Request failed: ' + r.toString())
                }
            }
        } finally {
            cleanUp()
        }
    }

    // first is the zip file
    // second is a cleanup action
    Tuple2<File, Closure> getZipFile(File pathFile){
        // If not a directory, it considers as a zip file
        // Server will send failure if it can't unzip it
        if (pathFile.isDirectory()) {
            def zipFile = zipFolder(pathFile)
            return new Tuple2(zipFile, { zipFile.delete() })
        } else {
            return new Tuple2(pathFile, { })
        }
    }

    File zipFolder(File folder) {
        def zipName = 'symbols'
        if(product_name)
            zipName = '_' + product_name
        if(product_version)
            zipName += '_' + product_version

        def zipFile = File.createTempFile(zipName, '.zip')
        def fileOutputStream = new FileOutputStream(zipFile)
        def zipOutputStream = new ZipOutputStream(fileOutputStream)
        folder.eachFileRecurse { file ->
            def relativePath = folder.toURI().relativize(file.toURI()).path
            zipOutputStream.putNextEntry(new ZipEntry(relativePath))
            if(file.isFile()) {
                def fileStream = new FileInputStream(file)
                zipOutputStream << fileStream
                fileStream.close()
            }
        }
        zipOutputStream.close()
        fileOutputStream.close()
        return zipFile
    }
}
