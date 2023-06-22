package com.openmobl.pttDriver.utils

import android.content.Context
import android.text.Html
import android.text.Spanned
import androidx.appcompat.app.AlertDialog
import com.openmobl.pttDriver.R

object UIUtils {
    fun showErrorDialog(
        context: Context?,
        title: String?,
        message: String?,
        positiveButtonText: String?
    ) {
        val dialog = AlertDialog.Builder(
            context!!
        )
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { dialog, whichButton ->
                // Do nothing
            }
            .create()
        dialog.show()
    }

    fun showErrorDialog(
        context: Context?,
        title: String?,
        message: Spanned?,
        positiveButtonText: String?
    ) {
        val dialog = AlertDialog.Builder(
            context!!
        )
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { dialog, whichButton ->
                // Do nothing
            }
            .create()
        dialog.show()
    }

    fun showDriverValidationError(
        context: Context,
        errorMessage: String?,
        errors: Map<String?, List<String?>?>?
    ) {
        val message = StringBuilder()
        var first = true
        message.append("<p>")
        message.append(errorMessage)
        message.append("</p>")
        message.append("<p>")
        for ((key, value) in errors!!) {
            if (!first) {
                message.append("<br/>")
            }
            message.append("<h2>")
            message.append(key)
            message.append("</h2>")
            message.append("<ul>")
            for (error in value!!) {
                message.append("<li>")
                message.append(error)
                message.append("</li>")
            }
            message.append("</ul>")
            first = false
        }
        message.append("</p>")
        showErrorDialog(
            context, context.getString(R.string.driver_validation_error),
            Html.fromHtml(message.toString()), context.getString(R.string.ok)
        )
    }
}