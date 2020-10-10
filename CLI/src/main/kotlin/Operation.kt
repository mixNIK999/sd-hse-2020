package com.sd.hw

import java.io.File
import java.lang.Exception

/**
 * Parent class for all bash commands.
 * @param environment environment for command execution
 */
abstract class Operation(val environment: Environment) {
    var args = mutableListOf<String>()

    /**
     * Add execution arguments.
     */
    open fun withArgs(values: List<String>): Operation {
        args.addAll(values)
        return this
    }

    /**
     * Run operation with saved arguments and optional input from pipe.
     */
    abstract fun run(additionalInput: String? = null): ExecutionResult
}

/**
 * Class for wc bash command partial simulation.
 */
class WC(environment: Environment) : Operation(environment) {
    /**
     * Returns number of lines, words and bytes.
     */
    override fun run(additionalInput: String?): ExecutionResult {
        val fileText = (if (args.isNotEmpty()) environment.resolveFile(args[0]) else additionalInput)
            ?: return ExecutionResult(true, "Error: invalid wc args")
        val lines = fileText.lines().size
        val words = fileText.trim().split("\\s".toRegex()).size
        val bytes = fileText.length
        return ExecutionResult(false, "$lines $words $bytes")
    }
}

/**
 * Class for echo bash command partial simulation.
 */
class Echo(environment: Environment) : Operation(environment) {
    /**
     * Returns given arguments with spaces and new line in the end.
     */
    override fun run(additionalInput: String?): ExecutionResult {
        return ExecutionResult(false, args.joinToString(" ") + '\n')
    }
}

/**
 * Class for pwd bash command partial simulation.
 */
class Pwd(environment: Environment) : Operation(environment) {
    /**
     * Returns absolute path for . directory.
     */
    override fun run(additionalInput: String?): ExecutionResult {
        return ExecutionResult(false, environment.getFullFilePath("."))
    }
}

/**
 * Class for cat bash command partial simulation.
 */
class Cat(environment: Environment) : Operation(environment) {
    /**
     * Returns text of file given in the arguments or additional input.
     */
    override fun run(additionalInput: String?): ExecutionResult {
        if (args.isNotEmpty()) {
            val filename = args[0]
            val result = environment.resolveFile(filename)
                ?: return ExecutionResult(true, "No file named $filename found")
            return ExecutionResult(false, result)
        }
        if (additionalInput != null) {
            return ExecutionResult(false, additionalInput)
        }
        return ExecutionResult(true, "too few arguments for cat")
    }
}

/**
 * Class for cd bash command partial simulation.
 */
class CD(environment: Environment) : Operation(environment) {
    /**
     * Change working directory.
     */
    override fun run(additionalInput: String?): ExecutionResult {
        if (args.isNotEmpty()) {
            val filename = args[0]
            val newPath = environment.workingDirectory.resolve(filename)
            if (!newPath.exists() || !newPath.isDirectory) {
                return ExecutionResult(true, "No directory named $filename found")
            }
            environment.workingDirectory = environment.workingDirectory.resolve(filename)
        }
        return ExecutionResult(false, "")
    }
}

/**
 * Class for ls bash command partial simulation.
 */
class LS(environment: Environment) : Operation(environment) {
    /**
     * Returns list of files in the given directory (the current is default).
     */
    override fun run(additionalInput: String?): ExecutionResult {
        val path = if (args.isNotEmpty()) args[0] else "."

        val pathToTarget = environment.workingDirectory.resolve(path)
        if (!pathToTarget.exists() || !pathToTarget.isDirectory) {
            return ExecutionResult(true, "No directory named $path found")
        }
        val filesList = pathToTarget.listFiles() ?: return ExecutionResult(true, "Can not read from $path")
        val result = filesList.joinToString("\n") { it.name }
        return ExecutionResult(false, result)
    }
}

/**
 * Class for exit bash command partial simulation.
 */
class Exit(environment: Environment) : Operation(environment) {
    /**
     * Returns interrupting ExecutionResult.
     */
    override fun run(additionalInput: String?): ExecutionResult {
        return ExecutionResult(true, "", true)
    }
}

/**
 * Class for inner process run.
 */
class RunProcess(private  val name: String, environment: Environment) : Operation(environment) {
    /**
     * Delegate all given arguments to process builder and translate process output to ExecutionResult.
     */
    override fun run(additionalInput: String?): ExecutionResult {
        args.add(0, name)
//        println(args)
        try {
            val process = ProcessBuilder(args)
                .directory(environment.workingDirectory)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()
            val output = process.inputStream
            val error = process.errorStream
            process.waitFor()
            val outputString = output.bufferedReader().readText().trim()
            val errorString = error.bufferedReader().readText().trim()

            process.destroy()
            return if (errorString.isNotEmpty()) {
                ExecutionResult(true, errorString)
            }
            else {
                ExecutionResult(false, outputString)
            }
        } catch (e: Exception) {
            return ExecutionResult(true, e.toString())
        }
    }
}

/**
 * Class for environmental variable association command.
 */
class Association(environment: Environment) : Operation(environment) {
    /**
     * Add new value to the environment.
     */
    override fun run(additionalInput: String?): ExecutionResult {
        if (args.size != 2) {
            return ExecutionResult(true)
        }
        val variableName = args[0]
        val variableValue = args[1]
        environment.addVariable(variableName, variableValue)
        return ExecutionResult(false)
    }

}

/**
 * Class for Operation instances creation.
 */
class OperationFactory(private val environment: Environment) {
    /**
     * Returns new instance of particular Operation child class.
     * In case of incorrect name returns null.
     */
    fun getOperationByName(name: String): Operation? {
        return when (name) {
            "wc" -> WC(environment)
            "echo" -> Echo(environment)
            "pwd" -> Pwd(environment)
            "cat" -> Cat(environment)
            "exit" -> Exit(environment)
            "=" -> Association(environment)
            "cd" -> CD(environment)
            "ls" -> LS(environment)
            else -> RunProcess(name, environment)
        }
    }
}