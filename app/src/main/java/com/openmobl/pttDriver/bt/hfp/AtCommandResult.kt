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

/**
 * The result of execution of a single AT command.
 *
 *
 *
 *
 * This class can represent the final response to an AT command line, and also
 * intermediate responses to a single command within a chained AT command
 * line.
 *
 *
 *
 * The actual responses that are intended to be send in reply to the AT command
 * line are stored in a string array. The final response is stored as an
 * int enum, converted to a string when toString() is called. Only a single
 * final response is sent from multiple commands chained into a single command
 * line.
 *
 *
 * @hide
 */
class AtCommandResult(  // Result code
    var resultCode: Int
) {
    private val mResponse // Response with CRLF line breaks
            : StringBuilder

    /**
     * Construct a new AtCommandResult with given result code, and an empty
     * response array.
     * @param resultCode One of OK, ERROR or UNSOLICITED.
     */
    init {
        mResponse = StringBuilder()
    }

    /**
     * Construct a new AtCommandResult with result code OK, and the specified
     * single line response.
     * @param response The single line response.
     */
    constructor(response: String) : this(OK) {
        addResponse(response)
    }

    /**
     * Add another line to the response.
     */
    fun addResponse(response: String) {
        appendWithCrlf(mResponse, response)
    }

    /**
     * Add the given result into this AtCommandResult object.
     *
     *
     * Used to combine results from multiple commands in a single command line
     * (command chaining).
     * @param result The AtCommandResult to add to this result.
     */
    fun addResult(result: AtCommandResult?) {
        if (result != null) {
            appendWithCrlf(mResponse, result.mResponse.toString())
            resultCode = result.resultCode
        }
    }

    /**
     * Generate the string response ready to send
     */
    override fun toString(): String {
        val result = StringBuilder(mResponse.toString())
        when (resultCode) {
            OK -> appendWithCrlf(result, OK_STRING)
            ERROR -> appendWithCrlf(result, ERROR_STRING)
        }
        return result.toString()
    }

    companion object {
        // Result code enumerations
        const val OK = 0
        const val ERROR = 1
        const val UNSOLICITED = 2
        private const val OK_STRING = "OK"
        private const val ERROR_STRING = "ERROR"
        private const val CRLF = "\r\n"

        /** Append a string to a string builder, joining with a double
         * CRLF. Used to create multi-line AT command replies
         */
        fun appendWithCrlf(str1: StringBuilder, str2: String) {
            /*if (str1.length() > 0 && str2.length() > 0) {
            str1.append(CRLF + CRLF);
        }
        str1.append(str2);*/
            if (str2.length > 0) {
                if (!str2.startsWith(CRLF)) str1.append(CRLF)
                str1.append(str2)
                if (!str2.endsWith(CRLF)) str1.append(CRLF)
            }
        }
    }
}