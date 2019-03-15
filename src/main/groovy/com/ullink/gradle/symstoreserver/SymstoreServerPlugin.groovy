package com.ullink.gradle.symstoreserver

import org.gradle.api.Plugin
import org.gradle.api.Project

class SymstoreServerPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.task('uploadSymbol', type: UploadSymbolTask) {
            upload ->
                upload.product_name = project.name
                upload.product_version = project.version
                def user = System.getProperty("user.name")
                def localHost = InetAddress.getLocalHost()
                upload.comment = "added by $user from $localHost.hostName ($localHost.hostAddress)"
        }
    }
}
