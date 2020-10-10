package edu.pentakon.votingapp.controllers;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionDialogController {

  public ExceptionDialogController(String errorMessage, Throwable throwable) {
    Alert exception = new Alert(Alert.AlertType.ERROR, errorMessage, ButtonType.CLOSE);
    exception.setTitle("Σφάλμα");
    TextArea area = new TextArea();
    area.setWrapText(true);
    area.setEditable(false);
    exception.getDialogPane().setContent(area);
    exception.setResizable(true);
    exception.getDialogPane().setMinWidth(1000);
    exception.getDialogPane().setMinHeight(800);

    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    throwable.printStackTrace(pw);
    area.setText(sw.toString());
    pw.close();
    Platform.runLater(exception::show);
  }

  public ExceptionDialogController(Throwable throwable) {
    this(throwable.getMessage(), throwable);
  }

}
