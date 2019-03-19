package com.ullink.gradle.symstoreserver

import groovy.json.JsonBuilder
import org.apache.commons.io.IOUtils
import org.apache.http.HttpEntity
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class UploadSymbolTaskTests extends Specification {

    static final String TEMP_ZIP_FILE_NAME = 'upload-symstore-server-'

    static UploadSymbolTask applyPluginAndGetTask(File file) {
        def project = ProjectBuilder.builder().build()
        project.version = '1.2.3'
        project.apply plugin: 'symstore-server'
        def task = (UploadSymbolTask) project.tasks['uploadSymbol']
        task.path = file.path
        task.serverUrl = 'http://localhost:1234/'
        return task
    }

    static File newTempZipFile() {
        File.createTempFile(TEMP_ZIP_FILE_NAME, '.zip')
    }

    void 'Given task without valid server url then an exception is thrown'() {
        given:
        def zipFile = newTempZipFile()
        def uploadTask = applyPluginAndGetTask(zipFile)
        uploadTask.serverUrl = serverUrl

        when:
        uploadTask.upload()

        then:
        thrown Exception

        cleanup:
        zipFile?.delete()

        where:
        serverUrl << [null, '', "doesn't make sense !!!", 'http://fzefzefze dez/']
    }

    void 'Given task without existing zip file then an exception is thrown'() {
        given:
        def zipFile = newTempZipFile()
        def uploadTask = applyPluginAndGetTask(zipFile)
        uploadTask.path = zipPath

        when:
        uploadTask.upload()

        then:
        thrown GradleException

        cleanup:
        zipFile?.delete()

        where:
        zipPath << [null, '', 'whatever', 'C:\\folder_1234\\file.zip', '/root_1234/something/']
    }

    void 'Given failure when sending request then an exception is thrown'() {
        given:
        def mockHttpClient = new MockHttpClient().withHttpFailure()
        def zipFile = newTempZipFile()
        def uploadTask = applyPluginAndGetTask(zipFile)
        uploadTask.path = zipFile.path
        uploadTask.serverUrl = 'whatever'
        uploadTask.createHttpClient = { mockHttpClient }

        when:
        uploadTask.upload()

        then:
        thrown(GradleException)

        cleanup:
        zipFile?.delete()
    }

    void 'Given all information provided then zip file is uploaded successfully'() {
        given:
        final serverUrlSent = 'http://server:1234/'
        def mockHttpClient = new MockHttpClient().withHttpSuccess().withJsonSuccess()
        def zipFile = newTempZipFile()
        def uploadTask = applyPluginAndGetTask(zipFile)
        uploadTask.serverUrl = serverUrlSent
        uploadTask.createHttpClient = { mockHttpClient }

        when:
        uploadTask.upload()

        then:
        def post = mockHttpClient.httpPostReceived
        def outputStream = new ByteArrayOutputStream()
        post.entity.writeTo(outputStream)
        def content = outputStream.toString().replace('\n', '').replace('\r', '')
        outputStream.close()

        post.URI.toString() == serverUrlSent + 'cgi-bin/add.py'
        content =~ /Content-Disposition: form-data; name="zip"; filename="$TEMP_ZIP_FILE_NAME.*\.zip"Content-Type: application\/octet-stream/
        content =~ /Content-Disposition: form-data; name="product_name"test/
        content =~ /Content-Disposition: form-data; name="product_version"1.2.3/
        content =~ /Content-Disposition: form-data; name="comment"added by/

        cleanup:
        zipFile?.delete()
    }

    void 'Given a folder then it is zipped and uploaded successfully'() {
        given:
        def mockHttpClient = new MockHttpClient().withHttpSuccess().withJsonSuccess()
        def folder = File.createTempDir()
        def subFolder = new File(folder, 'subfolder')
        subFolder.mkdir()
        def symbolFile = new File(subFolder, 'whatever.pdb')
        symbolFile.createNewFile()
        def uploadTask = applyPluginAndGetTask(folder)
        uploadTask.serverUrl = 'http://server:1234/'
        uploadTask.createHttpClient = { mockHttpClient }

        when:
        uploadTask.upload()

        then:
        mockHttpClient.httpPostReceived != null

        cleanup:
        folder?.deleteDir()
    }

    void 'Given something went wrong on symbols server side then an exception is thrown with server message'() {
        given:
        def errorMessage = 'This an error message !'
        def mockHttpClient = new MockHttpClient().withHttpSuccess().withJsonFailure(errorMessage)
        def zipFile = newTempZipFile()
        def uploadTask = applyPluginAndGetTask(zipFile)
        uploadTask.serverUrl = 'http://server:1234/'
        uploadTask.createHttpClient = { mockHttpClient }

        when:
        uploadTask.upload()

        then:
        GradleException exception = thrown()
        exception.message == errorMessage

        cleanup:
        zipFile?.delete()
    }

    void 'Given comment, version and name is not provided then zip file is uploaded successfully'() {
        given:
        def mockHttpClient = new MockHttpClient().withHttpSuccess().withJsonSuccess()
        def zipFile = newTempZipFile()
        def uploadTask = applyPluginAndGetTask(zipFile)
        uploadTask.product_version = null
        uploadTask.product_name = null
        uploadTask.comment = null
        uploadTask.serverUrl = 'http://server:1234/'
        uploadTask.createHttpClient = { mockHttpClient }

        when:
        uploadTask.upload()

        then:
        mockHttpClient.httpPostReceived != null

        cleanup:
        zipFile?.delete()
    }

    class MockHttpClient {
        int statusCode
        String reasonPhrase
        String json
        HttpPost httpPostReceived

        def execute(HttpPost post) {
            httpPostReceived = post
            def statusLineDelegate = [
                    getStatusCode: { statusCode },
                    getReasonPhrase : { reasonPhrase }
            ] as StatusLine
            def entityDelegate = {
                getContent: { IOUtils.toInputStream((String)json, StandardCharsets.UTF_8) }
            } as HttpEntity
            def responseDelegate = [
                    close : {},
                    getStatusLine: { statusLineDelegate },
                    getEntity: { entityDelegate }
            ] as CloseableHttpResponse

            return responseDelegate
        }

        void close() {}

        MockHttpClient withHttpSuccess() {
            statusCode = 200
            return this
        }

        MockHttpClient withHttpFailure() {
            statusCode = 500
            return this
        }

        MockHttpClient withJsonSuccess() {
            return withJsonResponse(UploadSymbolTask.RESPONSE.STATUS_VALUE_SUCCESS, '')
        }

        MockHttpClient withJsonFailure(String failureMessage) {
            return withJsonResponse('failure', failureMessage)
        }

        MockHttpClient withJsonResponse(String status, String message) {
            json = new JsonBuilder([
                    (UploadSymbolTask.RESPONSE.STATUS_KEY) : status,
                    (UploadSymbolTask.RESPONSE.MESSAGE_KEY): message])
                    .toString()
            return this
        }
    }
}
