package edu.pentakon.votingapp.controllers;

import edu.pentakon.votingapp.EthereumService;
import edu.pentakon.votingapp.VotingApplication;
import edu.pentakon.votingapp.model.AppMode;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class LoginController {

  @FXML private GridPane gridPane;
  public ProgressIndicator progressIndicator;
  public TextField ssnFld;
  public PasswordField pwdFld;
  public CheckBox adminChkbx;
  public Button loginbtn;
  private Task loginTask;

  public void loadCredentials(String ssn, String password) {
    if(ssn != null)
      ssnFld.setText(ssn);
    if(password != null)
      pwdFld.setText(password);
  }

  public void loginAction(ActionEvent actionEvent) throws Exception {
    String ssn = this.ssnFld.getText();
    String password = pwdFld.getText();
    if(ssn == null || ssn.isEmpty() || password == null || password.isEmpty()) {
      Alert alert = new Alert(Alert.AlertType.NONE, "Εισάγετε αναγνωριστικά χρήστη.", ButtonType.CLOSE);
      alert.setTitle("Σφάλμα");
      alert.show();
      return;
    }

    progressIndicator.setVisible(true);
    loginTask = loginTask(ssn);
    Thread loginThread = new Thread(loginTask);
    loginThread.start();

    loginTask.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, event -> {
      try {
        Stage primaryStage = (Stage) gridPane.getScene().getWindow();
        Parent parent;
        if(adminChkbx.isSelected()) {
          FXMLLoader loader = new FXMLLoader(getClass().getResource("/edu/pentakon/votingapp/views/admin.fxml"));
          parent = loader.load();
          AdminController controller = loader.getController();
          controller.init(EthereumService.votingContractAddress);
        } else {
          FXMLLoader loader = new FXMLLoader(getClass().getResource("/edu/pentakon/votingapp/views/voting.fxml"));
          parent = loader.load();
          VotingController controller = loader.getController();
          controller.init(ssn + password); // we use both SSN and password for PKI to make it unique even if the same password is used by 2 individuals
        }
        primaryStage.setScene(new Scene(parent));
      } catch (Exception e) {
        new ExceptionDialogController(e);
      }
    });

    loginTask.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, event1 -> {
      progressIndicator.setVisible(false);
      new ExceptionDialogController(loginTask.getException());
    });
  }

  public void onEnterLogin(KeyEvent keyEvent) throws Exception {
    if(keyEvent.getCode() == KeyCode.ENTER) {
      loginAction(new ActionEvent());
    }
  }

  private Task loginTask(String ssn) {
    return new Task() {
      @Override
      protected Object call() throws Exception {
        if(adminChkbx.isSelected()) {
          VotingApplication.initialize(AppMode.ADMINISTER, ssn);
        } else {
          VotingApplication.initialize(AppMode.VOTE, ssn);
        }
        return true;
      }
    };
  }
}
