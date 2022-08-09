import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess


const val SEPARATION = "// SequalsKclient: NEW FILE: "
val IGNORE_LIST = listOf("swiftSupportInKotlin", "kotlinSupportInSwift")

enum class Language(val extension: String) {
    kotlin("kt"), swift("swift");

    fun opposite() : Language {
        return if (this == kotlin) swift else kotlin
    }
}

fun main(args: Array<String>) {
    // input
    if (args.size < 2) {
        printHelp()
        exitProcess(-1)
    }
    val sourcesLanguage = if (args[0].toLowerCase().startsWith("k")) Language.kotlin else Language.swift
    val sourcesDirectory = args[1]
    val onlyCombining = args.size < 3
    val targetsDirectory = if (onlyCombining) "" else args[2]

    println("input language: $sourcesLanguage")
    println("sources directory: $sourcesDirectory")
    println("target directory: $targetsDirectory")

    // processing and output
    val targetsLanguage = sourcesLanguage.opposite()
    val sources = combineFiles(sourcesDirectory, sourcesLanguage.extension)
    if (onlyCombining) {
        writeCombinedSourcesToFile(sources)
    } else {
        val targets = sendPostRequest(sourcesLanguage, sources)
        writeTargets(targetsDirectory, targetsLanguage, targets)
    }

    println("Press ENTER to quit."); readLine()
}

fun printHelp() {
    println("You have to provide three arguments.")
    println("1) the input language: either kotlin or swift")
    println("2) the relative path of the source directory")
    println("3) the relative path of the target directory")
}

fun combineFiles(dir: String, ext: String) : String {
    var sources = ""
    val dirAsFile = File(dir)
    var countSources = 0
    dirAsFile.walk().filter{it.isFile && it.extension == ext}.forEach {
        val relativeFilenameWithoutExtension = it.toRelativeString(dirAsFile).substringBefore(".$ext")
        print(relativeFilenameWithoutExtension)
        if (IGNORE_LIST.contains(it.nameWithoutExtension)) {
            println(" -> IGNORED")
        } else {
            println()
            sources += SEPARATION + relativeFilenameWithoutExtension + "\n"
            sources += it.readText()
            sources += "\n"
            countSources++
        }
    }
    println("$countSources files read.")
    return sources
}

fun writeCombinedSourcesToFile(sources: String) {
    val file = File("combinedSources.txt")
    if (file.exists()) {
        println("Could not write combined sources to file. File already exists.")
    } else {
        file.writeText(sources)
        println("Combined sources written to '${file}'.")
    }
}

fun sendPostRequest(sourcesLanguage: Language, text: String) : Map<String, String> {
    val targets = mutableMapOf<String, String>()
    var currentKey: String? = null
    var currentValue: StringBuffer? = null
    val mURL = URL("https://transpile.iem.thm.de/sek/?input=$sourcesLanguage")

    with(mURL.openConnection() as HttpURLConnection) {
        requestMethod = "POST"
        setRequestProperty("Content-Type", "text/plain; charset=utf-8")
        doOutput = true

        elapsedTimeMs()
        val wr = OutputStreamWriter(getOutputStream(), StandardCharsets.UTF_8)
        wr.write(text)
        wr.flush()

        println("URL : $url")
        println("Response Code : $responseCode")
        println("Elapsed Time : ${elapsedTimeMs()} ms")

        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use {
            var inputLine = it.readLine()
            while (inputLine != null) {
                if (inputLine.startsWith(SEPARATION)) {
                    if (currentKey != null && currentValue?.isNotEmpty() == true) {
                        targets[currentKey!!] = currentValue.toString()
                    }
                    currentKey = inputLine.substringAfter(SEPARATION)
                    currentValue = StringBuffer()
                } else {
                    currentValue?.append(inputLine)?.append("\n")
                }
                inputLine = it.readLine()
            }
        }
    }

    if (currentKey != null && currentValue?.isNotEmpty() == true) {
        targets[currentKey!!] = currentValue.toString()
    }

    return targets
}

fun writeTargets(targetsDirectory: String, targetsLanguage: Language, targets: Map<String, String>) {
    println("${targets.size} transpiled files received.")
    var countOverwritten = 0
    var countNoChange = 0
    var countNew = 0
    targets.forEach {
        var targetCode = it.value
        while (targetCode.endsWith("\n\n")) targetCode = targetCode.removeSuffix("\n")
        val targetFile = File(targetsDirectory, it.key + ".${targetsLanguage.extension}")
        print(targetFile)
        if (targetFile.exists()) {
            val existingContents = targetFile.readText()
            if (targetCode != existingContents) {
                targetFile.writeText(targetCode)
                countOverwritten++
                println(" -> exists / OVERWRITTEN")
            } else {
                countNoChange++
                println(" -> exists / no change")
            }
        } else {
            val targetFileDir = targetFile.parentFile
            if (!targetFileDir.exists()) {
                targetFileDir.mkdirs()
                print(" -> DIRECTORY CREATED")
            }
            targetFile.writeText(targetCode)
            countNew++
            println(" -> WRITTEN")
        }
    }
    println("overwritten: $countOverwritten, no change: $countNoChange, newly created: $countNew")
}

var lastTimestampMs: Long? = null
fun elapsedTimeMs(): Long? {
    val currentTimestampMs = System.nanoTime() / 1_000_000
    var result: Long? = null
    if (lastTimestampMs != null) result = currentTimestampMs - lastTimestampMs!!
    lastTimestampMs = currentTimestampMs
    return result
}