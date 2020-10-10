package com.sd.hw

import java.io.File
import java.security.InvalidParameterException

/**
 * Class for storing one command or pipeline execution result.
 * @param isInterrupted does command execution should be stopped after current
 * @param textResult text output of the command
 */
data class ExecutionResult(val isInterrupted: Boolean, val textResult: String = "", val isEnded: Boolean = false)

/**
 * Class for environment simulation. Manage variables and files.
 * Allows to run default OperationFactory operations in itself.
 */
class Environment {
    companion object {
        const val PARSING_ERROR_MESSAGE = "Error: failed to parse command sequence"
    }

    private val vars: MutableMap<String, String> = mutableMapOf()
    private val parser = Parser(OperationFactory(this), this)
    var workingDirectory: File = File(".").absoluteFile
        set(newDir) {
            if (!newDir.isDirectory)
                throw InvalidParameterException("$newDir not a directory")
            field = newDir
        }

    /**
     * Execute pipeline of bash commands.
     * @param input Pipeline given in one string
     */
    fun execute(input: String): ExecutionResult {
        val commandsSequence = parser.parse(input)
        var savedResult = ""
        for (command in commandsSequence) {
            if (command == null) {
                return ExecutionResult(true, PARSING_ERROR_MESSAGE)
            }
            val executionResult =  command.run(savedResult)
            if (executionResult.isInterrupted) {
                return executionResult
            }
            savedResult = executionResult.textResult
        }
        return ExecutionResult(false, savedResult)
    }

    /**
     * Add new value for variable in the environment.
     */
    fun addVariable(name: String, value: String) {
        vars[name] = value
    }

    /**
     * Get variable value.
     * If such variable does not exist returns empty string.
     */
    fun resolveVariable(name: String): String {
        return vars[name] ?: ""
    }

    /**
     * Get file text or null if such file does not exist.
     */
    fun resolveFile(filename: String): String? {
        val file = workingDirectory.resolve(filename)
        if (!file.exists()) {
            return null
        }
        return file.readText()
    }

    /**
     * Get file canonical path.
     */
    fun getFullFilePath(filename: String): String {
        val file = workingDirectory.resolve(filename)
        return file.canonicalPath
    }
}