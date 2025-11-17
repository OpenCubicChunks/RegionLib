import nl.javadude.gradle.plugins.license.LicenseExtension
import nl.javadude.gradle.plugins.license.LicensePlugin
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.exception.GrgitException
import org.ajoberstar.grgit.operation.DescribeOp
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.extra

buildscript {
    repositories {
        maven {
            setUrl("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("org.ajoberstar:grgit:1.4.+")
    }
}

group = "io.github.opencubicchunks"
version = getProjectVersion()

val licenseYear: String by project
val projectName: String by project
val doRelease: String by project

plugins {
    java
    signing
    `maven-publish`
    id("com.github.hierynomus.license").version("0.16.1")
}

base {
    archivesName.set("RegionLib")
}

// configure
java.toolchain.languageVersion.set(JavaLanguageVersion.of(8))

license {
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
    archiveClassifier.set("javadoc")
    from(tasks["javadoc"])
}
val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].java.srcDirs)
}

// tasks must be before artifacts, don't change the order
artifacts {
    archives(tasks["jar"])
    /*withGroovyBuilder {
        "archives"(tasks["jar"], sourcesJar, javadocJar)
    }*/
}

publishing {
    repositories {
        //only register maven.daporkchop.net repository if these environment variables are set
        val ossrhMavenUsername = (project.properties["OSSRH_USERNAME"] ?: System.getenv("OSSRH_USERNAME")) as String?
        val ossrhMavenPassword = (project.properties["OSSRH_PASSWORD"] ?: System.getenv("OSSRH_PASSWORD")) as String?
        maven {
            name = "main"

            val local = ossrhMavenUsername == null || ossrhMavenPassword == null
            if (local) {
                logger.warn("Username or password not set, publishing to local repository in build/mvnrepo/")
            }
            val localUrl = "$buildDir/mvnrepo"
            val releasesRepoUrl = "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
            //NOTE:
            //
            //Consuming Via Gradle⚓︎
            //
            //Configure your build.gradle file with the following:
            //
            //repositories {
            //  maven {
            //    name = 'Central Portal Snapshots'
            //    url = 'https://central.sonatype.com/repository/maven-snapshots/'
            //
            //    // Only search this repository for the specific dependency
            //    content {
            //      includeModule("<the snapshot's groupId>", "<the snapshot's artifactId>")
            //    }
            //  }
            //  mavenCentral()
            //}
            val snapshotsRepoUrl = "https://central.sonatype.com/repository/maven-snapshots/"

            setUrl(if (local) localUrl else if (doRelease.toBoolean()) releasesRepoUrl else snapshotsRepoUrl)
            if (!local) {
                credentials {
                    username = ossrhMavenUsername
                    password = ossrhMavenPassword
                }
            }
        }

        //only register maven.daporkchop.net repository if these environment variables are set
        val daporkchopMavenUsername = (project.properties["daporkchopMavenUsername"] ?: System.getenv("daporkchopMavenUsername")) as String?
        val daporkchopMavenPassword = (project.properties["daporkchopMavenPassword"] ?: System.getenv("daporkchopMavenPassword")) as String?
        if (daporkchopMavenUsername != null && daporkchopMavenPassword != null) {
            maven {
                name = "DaPorkchop_"

                val releasesRepoUrl = "https://maven.daporkchop.net/release/"
                val snapshotsRepoUrl = "https://maven.daporkchop.net/snapshot/"

                setUrl(if (doRelease.toBoolean()) releasesRepoUrl else snapshotsRepoUrl)
                credentials {
                    username = daporkchopMavenUsername
                    password = daporkchopMavenPassword
                }
            }
        }
    }
    publications {
        fun configureArtifacts(publication: MavenPublication) {
            publication.artifact(tasks["jar"]) {
                classifier = ""
            }
            publication.artifact(tasks["sourcesJar"]) {
                classifier = "sources"
            }
            publication.artifact(tasks["javadocJar"]) {
                classifier = "javadoc"
            }
        }

        fun configurePom(publication: MavenPublication) {
            publication.pom {
                name.set(projectName)
                description.set("Minecraft-like Region data format library")
                packaging = "jar"
                url.set("https://github.com/OpenCubicChunks/RegionLib")
                scm {
                    connection.set("scm:git:git://github.com/OpenCubicChunks/RegionLib.git")
                    developerConnection.set("scm:git:ssh://git@github.com:OpenCubicChunks/RegionLib.git")
                    url.set("https://github.com/OpenCubicChunks/RegionLib")
                }

                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("http://www.tldrlegal.com/license/mit-license")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("Barteks2x")
                        name.set("Barteks2x")
                    }
                    developer {
                        id.set("xcube16")
                        name.set("xcube16")
                    }
                    // TODO: add more developers
                }

                issueManagement {
                    system.set("github")
                    url.set("https://github.com/OpenCubicChunks/RegionLib/issues")
                }
            }
        }

        /*create<MavenPublication>("library") {
            version = project.ext["mavenProjectVersion"]!!.toString()
            artifactId = "regionlib"

            configureArtifacts(this)
            configurePom(this)
        }*/

        //same as "library", but using the full project version from mcGitVersion instead of mavenProjectVersion.
        create<MavenPublication>("versionedLibrary") {
            version = project.version.toString()
            artifactId = "regionlib"

            configureArtifacts(this)
            configurePom(this)
        }
    }

    //all publish tasks should depend on all of the tasks which generate the artifacts being published
    tasks.withType<AbstractPublishToMaven>().configureEach {
        dependsOn("jar", "sourcesJar", "javadocJar")
    }

    //this is kinda gross, but is apparently the recommended way to conditionally publish specific publications to specific repositories:
    //  see https://docs.gradle.org/current/userguide/publishing_customization.html#sec:publishing_maven:conditional_publishing
    /*tasks.withType<PublishToMavenRepository>().configureEach {
        val predicate = provider {
            (publication == publications["library"] && repository == repositories.findByName("main")) ||
            (publication == publications["versionedLibrary"] && repository == repositories.findByName("DaPorkchop_"))
        }
        onlyIf("publishing library to Sonatype repository, and versioned library to DaPorkchop_ repository") {
            predicate.get()
        }
    }*/
}

signing {
    isRequired = false
    // isRequired = gradle.taskGraph.hasTask("uploadArchives")
    sign(configurations.archives.get())
}

// repositories and deps
repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.11")
}

tasks.withType<Test>().configureEach {
    minHeapSize = "256M"
    maxHeapSize = "2G"
}

// returns version string according to this: http://semver.org/
// format: MAJOR.MINOR.PATCH
// also appends -SNAPSHOT for snapshot versions, so they can be uploaded as snapshots to maven
fun getProjectVersion(): String {
    try {
        val git = Grgit.open()
        val describe = DescribeOp(git.repository).call()
        val branch = getGitBranch(git)
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
