package com.ullink.gradle.symstoreserer

import com.ullink.gradle.symstoreserver.UploadSymbolTask
import groovyx.net.http.Method
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class UploadSymbolTaskTests extends Specification {

    static UploadSymbolTask applyPluginAndGetTask(File zipFile) {
        def project = ProjectBuilder.builder().build()
        project.version = '1.2.3'
        project.apply plugin: 'symstore-server'
        def task = (UploadSymbolTask) project.tasks['uploadSymbol']
        task.zipPath = zipFile.path
        task.serverUrl = 'http://localhost:1234/'
        return task
    }

    static File newTempZipFile() {
        File.createTempFile('upload-symstore-server', '.zip')
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
        zipFile.delete()

        where:
        serverUrl << [null, '', "doesn't make sense !!!", 'http://fzefzefze dez/']
    }

    void 'Given task without existing zip file then an exception is thrown'() {
        given:
        def zipFile = newTempZipFile()
        def uploadTask = applyPluginAndGetTask(zipFile)
        uploadTask.zipPath = zipPath

        when:
        uploadTask.upload()

        then:
        thrown GradleException

        cleanup:
        zipFile.delete()

        where:
        zipPath << [null, '', 'whatever', 'C:\\folder_1234\\file.zip', '/root_1234/something/']
    }

    void 'Given failure when sending request then an exception is thrown'() {
        given:
        def mockHttpBuilder = new MockHttpBuilder(success: false)
        def zipFile = newTempZipFile()
        def uploadTask = applyPluginAndGetTask(zipFile)
        uploadTask.zipPath = zipFile.path
        uploadTask.serverUrl = 'whatever'
        uploadTask.createHttpBuilder = { mockHttpBuilder}

        when:
        uploadTask.upload()

        then:
        thrown(GradleException)

        cleanup:
        zipFile.delete()
    }

    void 'Given all information provided then zip file is upload successfully'() {
        given:
        final serverUrlSent = 'http://server:1234/'
        def mockHttpBuilder = new MockHttpBuilder()
        def serverUrlReceived = ''
        def zipFile = newTempZipFile()
        def uploadTask = applyPluginAndGetTask(zipFile)
        uploadTask.serverUrl = serverUrlSent
        uploadTask.createHttpBuilder = { String url ->
            serverUrlReceived = url
            mockHttpBuilder
        }

        when:
        uploadTask.upload()

        then:
        serverUrlReceived == serverUrlSent + 'cgi-bin/add.py'
        mockHttpBuilder.method == Method.POST
        // TODO: check entity, how ?
        // mockHttpBuilder.entity == ...

        cleanup:
        zipFile.delete()
    }

    void 'Given all information provided and something is wrong server side then an exception is thrown with server message'() {
        given:
        def errorMessage = 'This an error message !'
        def mockHttpBuilder = new MockHttpBuilder()
        mockHttpBuilder.reader.status = 'error'
        mockHttpBuilder.reader.message = errorMessage
        def zipFile = newTempZipFile()
        def uploadTask = applyPluginAndGetTask(zipFile)
        uploadTask.serverUrl = 'http://server:1234/'
        uploadTask.createHttpBuilder = { mockHttpBuilder }

        when:
        uploadTask.upload()

        then:
        GradleException exception = thrown()
        exception.message == errorMessage

        cleanup:
        zipFile.delete()
    }

    void 'Given comment, version and name is not provided then zip file is uploaded successfully'() {
        given:
        def mockHttpBuilder = new MockHttpBuilder()
        def zipFile = newTempZipFile()
        def uploadTask = applyPluginAndGetTask(zipFile)
        uploadTask.product_version = null
        uploadTask.product_name = null
        uploadTask.comment = null
        uploadTask.serverUrl = 'http://server:1234/'
        uploadTask.createHttpBuilder = { mockHttpBuilder }

        when:
        uploadTask.upload()

        then:
        mockHttpBuilder.method == Method.POST

        cleanup:
        zipFile.delete()
    }

    /*void integrationTest(){
        given:
        def zipFile = new File('local file')
        def uploadTask = applyPluginAndGetTask(zipFile)
        uploadTask.serverUrl = 'http://localhost:8000'

        when:
        uploadTask.upload()

        then:
        noExceptionThrown()
    }*/

    class MockHttpBuilder {
        def success = true
        def returnedData = ''
        def reader = [status: 'success', message: '']
        def method
        def entity

        def request(Method method, Closure body) {
            this.method = method
            def requestDelegate = [
                    response: [:],
            ]
            body.delegate = requestDelegate
            def req = [
                    setEntity: { entity -> this.entity = entity }
            ]
            def response = [:]
            body.call(req)
            if (success)
                requestDelegate.response.success(response, reader)
            else
                requestDelegate.response.failure(response)
            return returnedData
        }
    }
}
