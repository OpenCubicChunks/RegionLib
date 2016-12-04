import nl.javadude.gradle.plugins.license.LicenseExtension
import nl.javadude.gradle.plugins.license.LicensePlugin
import org.gradle.api.JavaVersion
import org.gradle.api.internal.HasConvention
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.script.lang.kotlin.*

buildscript {
    repositories {
        maven {
            setUrl("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("gradle.plugin.nl.javadude.gradle.plugins:license-gradle-plugin:0.13.1")
    }
}

group = "cubicchunks"
version = "1.0-SNAPSHOT"

val licenseYear = properties["licenseYear"] as String
val projectName = properties["projectName"] as String

apply {
    plugin<JavaPlugin>()
    plugin<LicensePlugin>()
}

configure<JavaPluginConvention> {
    setSourceCompatibility(JavaVersion.VERSION_1_8)
}


configure<LicenseExtension> {
    val ext = (this as HasConvention).convention.extraProperties
    ext["project"] = projectName
    ext["year"] = licenseYear
    exclude("**/*.info")
    exclude("**/package-info.java")
    exclude("**/*.json")
    exclude("**/*.xml")
    exclude("assets/*")
    header = file("HEADER.txt")
    ignoreFailures = false
    strictCheck = true
    mapping(mapOf("java" to "SLASHSTAR_STYLE"))
}

repositories {
    mavenCentral()
}

dependencies {
    testCompile("junit:junit:4.11")
    compile("org.apache.logging.log4j:log4j-api:2.7")
}

