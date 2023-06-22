/*
 * Copyright (C) 2007 The Android Open Source Project
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
package com.openmobl.pttDriver.bt.hfp

import android.util.Log

/**
 * An AT (Hayes command) Parser based on (a subset of) the ITU-T V.250 standard.
 *
 *
 *
 * Conformant with the subset of V.250 required for implementation of the
 * Bluetooth Headset and Handsfree Profiles, as per Bluetooth SIP
 * specifications. Also implements some V.250 features not required by
 * Bluetooth - such as chained commands.
 *
 *
 *
 * Command handlers are registered with an AtParser object. These handlers are
 * invoked when command lines are processed by AtParser's process() method.
 *
 *
 *
 * The AtParser object accepts a new command line to parse via its process()
 * method. It breaks each command line into one or more commands. Each command
 * is parsed for name, type, and (optional) arguments, and an appropriate
 * external handler method is called through the AtCommandHandler interface.
 *
 * The command types are
 *  * Basic Command. For example "ATDT1234567890". Basic command names are a
 * single character (e.g. "D"), and everything following this character is
 * passed to the handler as a string argument (e.g. "T1234567890").
 *  * Action Command. For example "AT+CIMI". The command name is "CIMI", and
 * there are no arguments for action commands.
 *  * Read Command. For example "AT+VGM?". The command name is "VGM", and there
 * are no arguments for get commands.
 *  * Set Command. For example "AT+VGM=14". The command name is "VGM", and
 * there is a single integer argument in this case. In the general case then
 * can be zero or more arguments (comma delimited) each of integer or string
 * form.
 *  * Test Command. For example "AT+VGM=?. No arguments.
 *
 *
 * In V.250 the last four command types are known as Extended Commands, and
 * they are used heavily in Bluetooth.
 *
 *
 *
 * Basic commands cannot be chained in this implementation. For Bluetooth
 * headset/handsfree use this is acceptable, because they only use the basic
 * commands ATA and ATD, which are not allowed to be chained. For general V.250
 * use we would need to improve this class to allow Basic command chaining -
 * however it's tricky to get right because there is no delimiter for Basic
 * command chaining.
 *
 *
 *
 * Extended commands can be chained. For example:
 *
 *
 * AT+VGM?;+VGM=14;+CIMI
 *
 *
 * This is equivalent to:
 *
 *
 * AT+VGM?
 * AT+VGM=14
 * AT+CIMI
 * Except that only one final result code is return (although several
 * intermediate responses may be returned), and as soon as one command in the
 * chain fails the rest are abandoned.
 *
 *
 *
 * Handlers are registered by there command name via register(Char c, ...) or
 * register(String s, ...). Handlers for Basic command should be registered by
 * the basic command character, and handlers for Extended commands should be
 * registered by String.
 *
 *
 *
 * Refer to:
 *  * ITU-T Recommendation V.250
 *  * ETSI TS 127.007  (AT Command set for User Equipment, 3GPP TS 27.007)
 *  * Bluetooth Headset Profile Spec (K6)
 *  * Bluetooth Handsfree Profile Spec (HFP 1.5)
 *
 * @hide
 */
class AtParser {
    private val mExtHandlers: HashMap<String, AtCommandHandler>
    private val mBasicHandlers: HashMap<Char, AtCommandHandler>
    private var mLastInput // for "A/" (repeat last command) support
            : String

    /**
     * Create a new AtParser.
     *
     *
     * No handlers are registered.
     */
    init {
        mBasicHandlers = HashMap()
        mExtHandlers = HashMap()
        mLastInput = ""
    }

    /**
     * Register a Basic command handler.
     *
     *
     * Basic command handlers are later called via their
     * `handleBasicCommand(String args)` method.
     * @param  command Command name - a single character
     * @param  handler Handler to register
     */
    fun register(command: Char, handler: AtCommandHandler) {
        mBasicHandlers[command] = handler
    }

    /**
     * Register an Extended command handler.
     *
     *
     * Extended command handlers are later called via:
     *  * `handleActionCommand()`
     *  * `handleGetCommand()`
     *  * `handleSetCommand()`
     *  * `handleTestCommand()`
     *
     * Only one method will be called for each command processed.
     * @param  command Command name - can be multiple characters
     * @param  handler Handler to register
     */
    fun register(command: String, handler: AtCommandHandler) {
        mExtHandlers[command] = handler
    }

    /**
     * Processes an incoming AT command line.
     *
     *
     * This method will invoke zero or one command handler methods for each
     * command in the command line.
     *
     *
     * @param raw_input The AT input, without EOL delimiter (e.g. <CR>).
     * @return          Result object for this command line. This can be
     * converted to a String[] response with toStrings().
    </CR> */
    fun process(raw_input: String): AtCommandResult {
        var input = clean(raw_input)
        // Handle "A/" (repeat previous line)
        if (input.regionMatches(0, "A/", 0, 2)) {
            input = String(mLastInput)
        } else {
            mLastInput = String(input)
        }
        // Handle empty line - no response necessary
        if (input == "") {
            // Return []
            return AtCommandResult(AtCommandResult.Companion.UNSOLICITED)
        }
        // Anything else deserves an error
        if (!input.regionMatches(0, "AT", 0, 2)) {
            // Return ["ERROR"]
            return AtCommandResult(AtCommandResult.Companion.ERROR)
        }
        // Ok we have a command that starts with AT. Process it
        var index = 2
        val result = AtCommandResult(AtCommandResult.Companion.UNSOLICITED)
        while (index < input.length) {
            val c = input[index]
            if (isAtoZ(c)) {
                // Option 1: Basic Command
                // Pass the rest of the line as is to the handler. Do not
                // look for any more commands on this line.
                val args = input.substring(index + 1)
                return if (mBasicHandlers.containsKey(c)) {
                    result.addResult(
                        mBasicHandlers[c]!!.handleBasicCommand(args)
                    )
                    result
                } else {
                    // no handler
                    result.addResult(
                        AtCommandResult(AtCommandResult.Companion.ERROR)
                    )
                    result
                }
                // control never reaches here
            }
            if (c == '+') {
                // Option 2: Extended Command
                // Search for first non-name character. Short-circuit if
                // we don't handle this command name or have the catch-all registered.
                val i = findEndExtendedName(input, index + 1)
                var commandName = input.substring(index, i)
                val realCommandName = commandName
                if (!mExtHandlers.containsKey(commandName)) {
                    commandName = if (!mExtHandlers.containsKey("")) {
                        // no handler
                        result.addResult(
                            AtCommandResult(AtCommandResult.Companion.ERROR)
                        )
                        return result
                    } else {
                        ""
                    }
                }
                val handler = mExtHandlers[commandName]
                // Search for end of this command - this is usually the end of
                // line
                val endIndex = findChar(';', input, index)
                // Determine what type of command this is.
                // Default to TYPE_ACTION if we can't find anything else
                // obvious.
                var type: Int
                type = if (i >= endIndex) {
                    TYPE_ACTION
                } else if (input[i] == '?') {
                    TYPE_READ
                } else if (input[i] == '=') {
                    if (i + 1 < endIndex) {
                        if (input[i + 1] == '?') {
                            TYPE_TEST
                        } else {
                            TYPE_SET
                        }
                    } else {
                        TYPE_SET
                    }
                } else {
                    TYPE_ACTION
                }
                Log.v(TAG, "process commandName: $realCommandName type: $type")
                when (type) {
                    TYPE_ACTION -> result.addResult(handler!!.handleActionCommand())
                    TYPE_READ -> result.addResult(handler!!.handleReadCommand())
                    TYPE_TEST -> result.addResult(handler!!.handleTestCommand())
                    TYPE_SET -> {
                        val args = generateArgs(input.substring(i + 1, endIndex))
                        result.addResult(handler!!.handleSetCommand(args))
                    }
                }
                if (result.resultCode != AtCommandResult.Companion.OK) {
                    return result // short-circuit
                }
                index = endIndex
            } else {
                // Can't tell if this is a basic or extended command.
                // Push forwards and hope we hit something.
                index++
            }
        }
        // Finished processing (and all results were ok)
        return result
    }

    companion object {
        private val TAG = AtParser::class.java.name

        // Extended command type enumeration, only used internally
        private const val TYPE_ACTION = 0 // AT+FOO
        private const val TYPE_READ = 1 // AT+FOO?
        private const val TYPE_SET = 2 // AT+FOO=
        private const val TYPE_TEST = 3 // AT+FOO=?

        /**
         * Strip input of whitespace and force Uppercase - except sections inside
         * quotes. Also fixes unmatched quotes (by appending a quote). Double
         * quotes " are the only quotes allowed by V.250
         */
        private fun clean(input: String): String {
            val out = StringBuilder(input.length)
            var i = 0
            while (i < input.length) {
                val c = input[i]
                if (c == '"') {
                    val j = input.indexOf('"', i + 1) // search for closing "
                    if (j == -1) {  // unmatched ", insert one.
                        out.append(input.substring(i, input.length))
                        out.append('"')
                        break
                    }
                    out.append(input.substring(i, j + 1))
                    i = j
                } else if (c != ' ') {
                    out.append(c.uppercaseChar())
                }
                i++
            }
            return out.toString()
        }

        private fun isAtoZ(c: Char): Boolean {
            return c >= 'A' && c <= 'Z'
        }

        /**
         * Find a character ch, ignoring quoted sections.
         * Return input.length() if not found.
         */
        private fun findChar(ch: Char, input: String, fromIndex: Int): Int {
            var i = fromIndex
            while (i < input.length) {
                val c = input[i]
                if (c == '"') {
                    i = input.indexOf('"', i + 1)
                    if (i == -1) {
                        return input.length
                    }
                } else if (c == ch) {
                    return i
                }
                i++
            }
            return input.length
        }

        /**
         * Break an argument string into individual arguments (comma delimited).
         * Integer arguments are turned into Integer objects. Otherwise a String
         * object is used.
         */
        private fun generateArgs(input: String): Array<Any> {
            var i = 0
            var j: Int
            val out = ArrayList<Any>()
            while (i <= input.length) {
                j = findChar(',', input, i)
                val arg = input.substring(i, j)
                try {
                    out.add(Integer.valueOf(arg))
                } catch (e: NumberFormatException) {
                    Log.d(TAG, "Error parsing arg $arg: $e")
                    e.printStackTrace()
                    out.add(arg)
                }
                i = j + 1 // move past comma
            }
            return out.toTypedArray()
        }

        /**
         * Return the index of the end of character after the last character in
         * the extended command name. Uses the V.250 spec for allowed command
         * names.
         */
        private fun findEndExtendedName(input: String, index: Int): Int {
            for (i in index until input.length) {
                val c = input[i]
                // V.250 defines the following chars as legal extended command
                // names
                if (isAtoZ(c)) continue
                if (c >= '0' && c <= '9') continue
                return when (c) {
                    '!', '%', '-', '.', '/', ':', '_' -> continue
                    else -> i
                }
            }
            return input.length
        }
    }
}