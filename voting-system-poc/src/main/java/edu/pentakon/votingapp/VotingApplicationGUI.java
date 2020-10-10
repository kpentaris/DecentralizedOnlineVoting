package edu.pentakon.votingapp;

import edu.pentakon.votingapp.controllers.ExceptionDialogController;
import edu.pentakon.votingapp.controllers.LoginController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VotingApplicationGUI extends Application {

  private static Logger logger = Logger.getLogger(VotingApplicationGUI.class.getName());

  @Override
  public void start(Stage primaryStage) throws Exception {
    Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
      if(Platform.isFxApplicationThread())
        new ExceptionDialogController(throwable);
      logger.log(Level.SEVERE, throwable.getMessage(), throwable);
    });
    FXMLLoader loader = new FXMLLoader(getClass().getResource("/edu/pentakon/votingapp/views/login.fxml"));
    Parent root = loader.load();
    primaryStage.setTitle("Voting Application");
    Scene loginScene = new Scene(root);
    primaryStage.setScene(loginScene);
    LoginController loginController = loader.getController();
    List<String> initParams = getParameters().getRaw();
    String ssn = null, password = null;
    for (int i = 0; i < initParams.size(); i++) {
      if(i == 0)
        ssn = initParams.get(i);
      else if (i == 1)
        password = initParams.get(i);
    }
    loginController.loadCredentials(ssn, password);
    primaryStage.show();
  }

  @Override
  public void stop() throws Exception {
    super.stop();
    try {
      VotingApplication.get().stopApp();
    } catch (Exception e) {
      // couldn't run on close, don't care
    }
  }

  public static void main(String[] args) throws Exception {
    launch(args);
  }

}
