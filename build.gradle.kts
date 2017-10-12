import nl.javadude.gradle.plugins.license.LicenseExtension
import nl.javadude.gradle.plugins.license.LicensePlugin
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.exception.GrgitException
import org.ajoberstar.grgit.operation.DescribeOp
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.gradle.api.JavaVersion
import org.gradle.api.internal.HasConvention
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.kotlin.dsl.extra
import org.gradle.script.lang.kotlin.*

buildscript {
    repositories {
        maven {
            setUrl("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("org.ajoberstar:grgit:1.4.+")
        classpath("gradle.plugin.nl.javadude.gradle.plugins:license-gradle-plugin:0.13.1")
    }
}

group = "io.github.opencubicchunks"
version = getProjectVersion()

val licenseYear = properties["licenseYear"] as String
val projectName = properties["projectName"] as String

val sourceSets = the<JavaPluginConvention>().sourceSets!!

plugins {
    java
    signing
    maven
}
apply {
    plugin<LicensePlugin>()
}

base {
    archivesBaseName = "RegionLib"
}

// configure
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

license {
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

val javadocJar by tasks.creating(Jar::class) {
    classifier = "javadoc"
    from(tasks["javadoc"])
}
val sourcesJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(sourceSets["main"].java.srcDirs)
}


// based on:
// https://github.com/Ordinastie/MalisisCore/blob/30d8efcfd047ac9e9bc75dfb76642bd5977f0305/build.gradle#L204-L256
// https://github.com/gradle/kotlin-dsl/blob/201534f53d93660c273e09f768557220d33810a9/samples/maven-plugin/build.gradle.kts#L10-L44
val uploadArchives: Upload by tasks
uploadArchives.apply {
    repositories {
        withConvention(MavenRepositoryHandlerConvention::class) {
            mavenDeployer {
                // Sign Maven POM
                beforeDeployment {
                     signing.signPom(this)
                }

                val username = if (project.hasProperty("sonatypeUsername")) project.properties["sonatypeUsername"] else System.getenv("sonatypeUsername")
                val password = if (project.hasProperty("sonatypePassword")) project.properties["sonatypePassword"] else System.getenv("sonatypePassword")

                withGroovyBuilder {
                    "snapshotRepository"("url" to "https://oss.sonatype.org/content/repositories/snapshots") {
                        "authentication"("userName" to username, "password" to password)
                    }

                    "repository"("url" to "https://oss.sonatype.org/service/local/staging/deploy/maven2") {
                        "authentication"("userName" to username, "password" to password)
                    }
                }

                // Maven POM generation
                pom.project {
                    withGroovyBuilder {
                        "name"(projectName)
                        "artifactId"(base.archivesBaseName.toLowerCase())
                        "packaging"("jar")
                        "url"("https://github.com/OpenCubicChunks/RegionLib")
                        "description"("Minecraft-like Region data format library")


                        "scm" {
                            "connection"("scm:git:git://github.com/OpenCubicChunks/RegionLib.git")
                            "developerConnection"("scm:git:ssh://git@github.com:OpenCubicChunks/RegionLib.git")
                            "url"("https://github.com/OpenCubicChunks/RegionLib")
                        }

                        "licenses" {
                            "license" {
                                "name"("The MIT License")
                                "url"("http://www.tldrlegal.com/license/mit-license")
                                "distribution"("repo")
                            }
                        }

                        "developers" {
                            "developer" {
                                "id"("Barteks2x")
                                "name"("Barteks2x")
                            }
                            "developer" {
                                "id"("xcube16")
                                "name"("xcube16")
                            }
                        }

                        "issueManagement" {
                            "system"("github")
                            "url"("https://github.com/OpenCubicChunks/RegionLib/issues")
                        }
                    }
                }
            }
        }
    }
}

// tasks must be before artifacts, don't change the order
artifacts {
    withGroovyBuilder {
        "archives"(tasks["jar"], sourcesJar, javadocJar)
    }
}

signing {
    isRequired = false
    // isRequired = gradle.taskGraph.hasTask("uploadArchives")
    sign(configurations.archives)
}

// repositories and deps
repositories {
    mavenCentral()
}

dependencies {
    testCompile("junit:junit:4.11")
}

// returns version string according to this: http://semver.org/
// format: MAJOR.MINOR.PATCH
// also appends -SNAPSHOT for snapshot versions, so they can be uploaded as snapshots to maven
fun getProjectVersion(): String {
    try {
        val git = Grgit.open()
        val describe = DescribeOp(git.repository).call()
        val branch = getGitBranch()
        val snapshotSuffix = if (project.hasProperty("doRelease")) "" else "-SNAPSHOT"
        return getVersion_do(describe, branch) + snapshotSuffix
    } catch(ex: RuntimeException) {
        logger.error("Unknown error when accessing git repository! Are you sure the git repository exists?", ex)
        return String.format("%s.%s.%s%s", "9999", "9999", "9999", "NOVERSION")
    }
}


fun getGitBranch(git: Grgit): String {
    var branch: String = git.branch.current.name
    if (branch == "HEAD") {
        branch = when {
            System.getenv("TRAVIS_BRANCH")?.isEmpty() == false -> // travis
                System.getenv("TRAVIS_BRANCH")
            System.getenv("GIT_BRANCH")?.isEmpty() == false -> // jenkins
                System.getenv("GIT_BRANCH")
            System.getenv("BRANCH_NAME")?.isEmpty() == false -> // ??? another jenkins alternative?
                System.getenv("BRANCH_NAME")
            else -> throw RuntimeException("Found HEAD branch! This is most likely caused by detached head state! Will assume unknown version!")
        }
    }

    if (branch.startsWith("origin/")) {
        branch = branch.substring("origin/".length)
    }
    return branch
}

fun getVersion_do(describe: String, branch: String) : String {

    val versionMinorFreeze = project.property("versionMinorFreeze") as String

    //branch "master" is not appended to version string, everything else is
    //only builds from "master" branch will actually use the correct versioning
    //but it allows to distinguish between builds from different branches even if version number is the same
    val branchSuffix = if (branch == "master") "" else ("-" + branch.replace("[^a-zA-Z0-9.-]", "_"))

    val baseVersionRegex = "v[0-9]+"
    val unknownVersion = String.format("UNKNOWN_VERSION%s", branchSuffix)
    if (!describe.contains('-')) {
        //is it the "vX" format?
        if (describe.matches(Regex(baseVersionRegex))) {
            return String.format("%s.0.0%s", describe, branchSuffix)
        }
        logger.error("Git describe information: \"$describe\" in unknown/incorrect format")
        return unknownVersion
    }
    //Describe format: vX-build-hash
    val parts = describe.split("-")
    if (!parts[0].matches(Regex(baseVersionRegex))) {
        logger.error("Git describe information: \"$describe\" in unknown/incorrect format")
        return unknownVersion
    }
    if (!parts[1].matches(Regex("[0-9]+"))) {
        logger.error("Git describe information: \"$describe\" in unknown/incorrect format")
        return unknownVersion
    }
    val apiVersion = parts[0].substring(1)
    //next we have commit-since-tag
    val commitSinceTag = Integer.parseInt(parts[1])

    val minorFreeze = if (versionMinorFreeze.isEmpty()) -1 else Integer.parseInt(versionMinorFreeze)

    val minor = if (minorFreeze < 0) commitSinceTag else minorFreeze
    val patch = if (minorFreeze < 0) 0 else (commitSinceTag - minorFreeze)

    val version = String.format("%s.%d.%d%s", apiVersion, minor, patch, branchSuffix)
    return version
}
