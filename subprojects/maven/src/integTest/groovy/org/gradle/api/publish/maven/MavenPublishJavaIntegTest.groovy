/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */




package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class MavenPublishJavaIntegTest extends AbstractIntegrationSpec {
    public void "can publish jar and meta-data to maven repository"() {
        given:
        settingsFile << "rootProject.name = 'publishTest' "

        and:
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            group = 'org.gradle.test'
            version = '1.9'

            repositories {
                mavenCentral()
            }

            dependencies {
                compile "commons-collections:commons-collections:3.2.1"
                runtime "commons-io:commons-io:1.4"
                testCompile "junit:junit:4.11"
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    add('maven', org.gradle.api.publish.maven.MavenPublication) {
                        from components['java']
                    }
                }
            }
"""

        when:
        run "publish"

        then:
        def mavenModule = mavenRepo.module("org.gradle.test", "publishTest", "1.9")
        mavenModule.assertPublishedAsJavaModule()

        mavenModule.parsedPom.scopes.runtime.dependencies.size() == 2
        mavenModule.parsedPom.scopes.runtime.assertDependsOn("commons-collections", "commons-collections", "3.2.1")
        mavenModule.parsedPom.scopes.runtime.assertDependsOn("commons-io", "commons-io", "1.4")
        mavenModule.parsedPom.scopes.compile == null
        mavenModule.parsedPom.scopes.testCompile == null
    }

}
