package edu.pentakon.votingapp.controllers;

import ch.bfh.unicrypt.helper.factorization.SafePrime;
import edu.pentakon.votingapp.EthereumService;
import edu.pentakon.votingapp.ServicesContext;
import edu.pentakon.votingapp.VotingApplication;
import edu.pentakon.votingapp.VotingService;
import edu.pentakon.votingapp.model.Choice;
import edu.pentakon.votingapp.model.Election;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.*;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VotingController {

  private Logger logger = Logger.getLogger(VotingController.class.getName());

  public ProgressIndicator progressIndicator;
  public RadioButton radioYes;
  public RadioButton radioNo;
  public Label titleFld;
  public Label periodFld;
  public Label resultFld;
  public ToggleGroup choiceRadioGroup;
  public Label uidFld;
  private VotingApplication application;
  private Election election;
  private EthereumService ethereumService;
  private VotingService votingService;
  private boolean electionEnded = false;
  private int[] tallyingResult = null;

  public void init(String password) throws Exception {
    application = VotingApplication.get();
    ethereumService = ServicesContext.get(EthereumService.class);
    election = application.getElection().orElseThrow(() -> new IllegalStateException("Μη διαθέσιμη ψηφοφορία.")); // TODO no exception if there is no election on login
    ServicesContext.initializeAll(SafePrime.getInstance(election.getCyclicGroupPrime()), password, election, election.getTitle());
    votingService = ServicesContext.get(VotingService.class);
    uidFld.setText(votingService.getUID());
    titleFld.setText(election.getTitle());
    periodFld.setText(generateVotingPeriodStr(election.getVotingStart(), election.getVotingEnd()));
    startPollingForEndVote();
  }

  private static String generateVotingPeriodStr(long start, long end) {
    Date startD = new Date(start * 1000);
    Date endD = new Date(end * 1000);
    final ZonedDateTime startZDT = startD.toInstant().atZone(ZoneId.systemDefault());
    final int startDay = startZDT.get(ChronoField.DAY_OF_MONTH);
    final int startMonth = startZDT.get(ChronoField.MONTH_OF_YEAR);
    final int startYear = startZDT.get(ChronoField.YEAR);
    final ZonedDateTime endZDT = endD.toInstant().atZone(ZoneId.systemDefault());
    final int endDay = endZDT.get(ChronoField.DAY_OF_MONTH);
    final int endMonth = endZDT.get(ChronoField.MONTH_OF_YEAR);
    final int endYear = endZDT.get(ChronoField.YEAR);

    return startDay + "/" + startMonth + "/" + startYear + " - " + endDay + "/" + endMonth + "/" + endYear;
  }

  public void submitVote(ActionEvent event) throws Exception {
    // TODO add user feedback after successful submission
    Choice choice;
    if (radioYes.isSelected()) {
      choice = Choice.YES;
    } else if (radioNo.isSelected()) {
      choice = Choice.NO;
    } else {
      new Alert(Alert.AlertType.NONE, "Ελλιπής ψήφος.", ButtonType.CLOSE).show();
      return;
    }

    Task setupElectionTask = new Task() {
      @Override
      protected Object call() throws Exception {
        votingService.submitVote(choice);
        return true;
      }
    };
    progressIndicator.setVisible(true);
    new Thread(setupElectionTask).start();
    setupElectionTask.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, event1 -> {
      progressIndicator.setVisible(false);
      new Alert(Alert.AlertType.INFORMATION, "Η ψήφος καταχωρήθηκε επιτυχώς.", ButtonType.CLOSE).show();
    });
    setupElectionTask.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, event1 -> {
      progressIndicator.setVisible(false);
      new ExceptionDialogController("Η καταχώρηση ψήφου απέτυχε.", setupElectionTask.getException());
    });
  }

  private void endElection() throws Exception {
    electionEnded = true;
    votingService.submitMPCSum();
  }

  private void tallyVotes() throws Exception {
    tallyingResult = votingService.tallyVotes();
    Platform.runLater(() -> {
      // this is used because from inside another thread (since this is run by the executor) it is not allowed to update
      // the GUI directly in JavaFX. This gives the command to the JavaFX thread to run the provided runnable at a later update frame
      resultFld.setText(String.format("%s: %d, %s: %d", "ΝΑΙ", tallyingResult[0], "ΟΧΙ", tallyingResult[1]));
    });
  }

  private void startPollingForEndVote() {
    application.service.scheduleAtFixedRate(() -> {
      try {
        logger.log(Level.INFO, "User {0}: Polling", new Object[]{application.ssn});
        if (electionEnded && tallyingResult == null && ethereumService.tallyVotes()) {
          logger.log(Level.INFO, "User {0}: Tallying votes", new Object[]{application.ssn});
          tallyVotes();
        } else if (!electionEnded && votingService.checkElectionEnded()) {
          logger.log(Level.INFO, "User {0}: Election ended", new Object[]{application.ssn});
          endElection();
        } else {
          logger.log(Level.INFO, "User {0}: Election not ended", new Object[]{application.ssn});
        }
      } catch (Exception e) {
        logger.log(Level.SEVERE, "Error during polling  : " + e.getMessage(), e);
        new ExceptionDialogController(e);
      }
    }, 10, 10, TimeUnit.SECONDS);
  }

  public void copyUid(ActionEvent event) {
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(uidFld.getText()), null);
  }

  public void onCancel(ActionEvent event) throws Exception {
    Platform.exit();
  }

}
