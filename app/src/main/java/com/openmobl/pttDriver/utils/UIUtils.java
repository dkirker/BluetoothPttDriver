package com.openmobl.pttDriver.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.text.Html;
import android.text.Spanned;

import androidx.appcompat.app.AlertDialog;

import com.openmobl.pttDriver.R;

import java.util.List;
import java.util.Map;

public class UIUtils {
    public static void showErrorDialog(Context context, String title, String message, String positiveButtonText) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveButtonText, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Do nothing
                    }
                })
                .create();
        dialog.show();
    }

    public static void showErrorDialog(Context context, String title, Spanned message, String positiveButtonText) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveButtonText, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Do nothing
                    }
                })
                .create();
        dialog.show();
    }

    public static void showDriverValidationError(Context context, String errorMessage, Map<String, List<String>> errors) {
        StringBuilder message = new StringBuilder();
        boolean first = true;

        message.append("<p>");
        message.append(errorMessage);
        message.append("</p>");

        message.append("<p>");
        for (Map.Entry<String, List<String>> errorEntry: errors.entrySet()) {
            if (!first) {
                message.append("<br/>");
            }
            message.append("<h2>");
            message.append(errorEntry.getKey());
            message.append("</h2>");

            message.append("<ul>");
            for (String error : errorEntry.getValue()) {
                message.append("<li>");
                message.append(error);
                message.append("</li>");
            }
            message.append("</ul>");

            first = false;
        }
        message.append("</p>");

        showErrorDialog(context, context.getString(R.string.driver_validation_error),
                Html.fromHtml(message.toString()), context.getString(R.string.ok));
    }
}
