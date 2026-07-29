package com.bfunkstudios.beatclikr.music

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.readLines
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicArchitectureTest {

    @Test
    fun musicDomainDoesNotImportAndroidApis() {
        val sourceDirectory = locateMusicSourceDirectory()
        val sourceFiles = Files.walk(sourceDirectory).use { paths ->
            paths.filter { it.extension == "kt" || it.extension == "java" }.toList()
        }
        val androidImports = sourceFiles.flatMap { sourceFile ->
            sourceFile.readLines().mapIndexedNotNull { index, line ->
                if (ANDROID_IMPORT.matches(line)) {
                    "${sourceDirectory.relativize(sourceFile)}:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }
        }

        assertTrue("Expected production music sources in $sourceDirectory", sourceFiles.isNotEmpty())
        assertFalse(
            "The music domain must remain Android-free:\n${androidImports.joinToString("\n")}",
            androidImports.isNotEmpty()
        )
    }

    private fun locateMusicSourceDirectory(): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        val candidates = generateSequence(workingDirectory) { it.parent }
            .flatMap { directory ->
                sequenceOf(
                    directory.resolve("src/main/java/com/bfunkstudios/beatclikr/music"),
                    directory.resolve("app/src/main/java/com/bfunkstudios/beatclikr/music")
                )
            }

        return candidates.firstOrNull(Path::isDirectory)
            ?: error("Cannot locate the production music source directory from $workingDirectory")
    }

    private companion object {
        val ANDROID_IMPORT = Regex("""^\s*import\s+android(?:\.|\s).*""")
    }
}
