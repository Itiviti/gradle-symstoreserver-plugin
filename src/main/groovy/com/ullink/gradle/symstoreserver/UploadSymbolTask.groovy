package com.ullink.gradle.symstoreserver

import org.apache.commons.io.IOUtils
import org.apache.http.HttpEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.gradle.api.GradleException
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.TaskAction
import org.json.JSONObject

import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class UploadSymbolTask extends ConventionTask {
    public static final String PRODUCT_NAME_FIELD = 'product_name'

    public static final String PRODUCT_VERSION_FIELD = 'product_version'

    public static final String ZIP_FIELD = 'zip'

    public static final String COMMENT_FIELD = 'comment'

    public interface RESPONSE {
        public static final String STATUS_KEY = 'status'

        public static final String MESSAGE_KEY = 'message'

        public static final String STATUS_VALUE_SUCCESS = 'success'
    }

    String serverUrl

    String product_name

    String product_version

    String comment

    String path

    // for unit tests purposes
    Closure<CloseableHttpClient> createHttpClient = { HttpClients.createDefault() }

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
            def post = new HttpPost(url.toString())
            post.setEntity(createPostEntity(zipFile))

            def client = createHttpClient()
            try {
                def response = client.execute(post)
                try { handleResponse(response) }
                finally { response.close() }
            } finally { client.close() }
        } finally { cleanUp() }
    }

    void handleResponse(CloseableHttpResponse response) {
        if (response.statusLine.statusCode == 200) {
            def rawJson = IOUtils.toString(response.entity.content, StandardCharsets.UTF_8)
            def json = new JSONObject(rawJson)
            if (json.getString(RESPONSE.STATUS_KEY) != RESPONSE.STATUS_VALUE_SUCCESS)
                throw new GradleException(json.getString(RESPONSE.MESSAGE_KEY))
            logger.info('Request succeed.')
        } else {
            throw new GradleException('Request failed: ' + response.statusLine.reasonPhrase)
        }
    }

    private HttpEntity createPostEntity(File zipFile) {
        def entityBuilder = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .addBinaryBody(ZIP_FIELD, zipFile)
        if (product_name)
            entityBuilder.addTextBody(PRODUCT_NAME_FIELD, product_name)
        if (product_version)
            entityBuilder.addTextBody(PRODUCT_VERSION_FIELD, product_version)
        if (comment)
            entityBuilder.addTextBody(COMMENT_FIELD, comment)
        return entityBuilder.build()
    }

    // first, zip file path
    // second, cleanup action
    Tuple2<File, Closure> getZipFile(File pathFile){
        // If not a directory, it considers as a zip file
        // Server will send failure if it can't unzip it
        if (pathFile.isDirectory()) {
            logger.info('path is a directory, zipping it.')
            def zipFile = zipFolder(pathFile)
            return new Tuple2(zipFile, { zipFile.delete() })
        } else {
            return new Tuple2(pathFile, { })
        }
    }

    File zipFolder(File folder) {
        def zipName = 'symbols'
        if(product_name)
            zipName += '_' + product_name
        if(product_version)
            zipName += '_' + product_version
        zipName += '_'

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
