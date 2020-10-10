package edu.pentakon.votingapp.controllers;

import ch.bfh.unicrypt.helper.factorization.SafePrime;
import edu.pentakon.votingapp.EthereumService;
import edu.pentakon.votingapp.ServicesContext;
import edu.pentakon.votingapp.VotingApplication;
import edu.pentakon.votingapp.model.Election;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

public class AdminController {

  private EthereumService ethereumService;

  public GridPane gridPane;
  public ProgressIndicator progressIndicator;
  public Label contractAddressLabel;
  public TextField titleFld;
  public DatePicker startDateFld;
  public DatePicker endDateFld;
  public TextField safePrimeFld;
  public Button safePrimeGen;

  public void init(String contractAddress) {
    ethereumService = ServicesContext.get(EthereumService.class);
    contractAddressLabel.setText(contractAddressLabel.getText() + " " + contractAddress);
    safePrimeFld.setText("598363995809807");
    VotingApplication.get().getElection().ifPresent(election -> {
      titleFld.setText(election.getTitle());
      startDateFld.setValue(LocalDate.from(LocalDateTime.ofEpochSecond(election.getVotingStart(), 0, ZoneOffset.UTC)));
      endDateFld.setValue(LocalDate.from(LocalDateTime.ofEpochSecond(election.getVotingEnd(), 0, ZoneOffset.UTC)));
      safePrimeFld.setText(election.getCyclicGroupPrime().toString());
    });
  }

  public void updateElectionParams(ActionEvent event) throws Exception {
    // validation of admin user happens based on his wallet address which for this implementation is common for everyone
    String title = titleFld.getText();
    long start = startDateFld.getValue().atStartOfDay().toEpochSecond(ZoneOffset.UTC);
    long end = endDateFld.getValue().atStartOfDay().toEpochSecond(ZoneOffset.UTC);
    String safePrimeModQ = safePrimeFld.getText();
    String[] allowedVoterUids = ethereumService.getParticipantPublicKeys();
    Optional<String> maybeProblem = isElectionDataValid(start, end, safePrimeModQ, allowedVoterUids.length);
    if(maybeProblem.isPresent()) {
      new Alert(Alert.AlertType.WARNING, maybeProblem.get(), ButtonType.CLOSE).show();
      return;
    }

    Election election = new Election(title, start, end, new BigInteger(safePrimeModQ));
    Task setupElectionTask = new Task() {
      @Override
      protected Object call() throws Exception {
        ethereumService.setupElection(election);
        return true;
      }
    };
    progressIndicator.setVisible(true);
    new Thread(setupElectionTask).start();
    setupElectionTask.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, event1 -> {
      VotingApplication.get().setElection(election);
      progressIndicator.setVisible(false);
      new Alert(Alert.AlertType.INFORMATION, "Η ανανέωση των παραμέτρων ψηφοφορίας ήταν επιτυχής.", ButtonType.CLOSE).show();
    });
    setupElectionTask.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, event1 -> {
      progressIndicator.setVisible(false);
      new ExceptionDialogController("Η ανανέωση των παραμέτρων ψηφοφορίας απέτυχε.", setupElectionTask.getException());
    });
  }

  public void endElection(ActionEvent event) throws Exception {
    Task setupElectionTask = new Task() {
      @Override
      protected Object call() throws Exception {
        ethereumService.endElection();
        return true;
      }
    };
    progressIndicator.setVisible(true);
    new Thread(setupElectionTask).start();
    setupElectionTask.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, event1 -> {
      progressIndicator.setVisible(false);
      new Alert(Alert.AlertType.INFORMATION, "Η ψηφοφορία έκλεισε επιτυχώς.", ButtonType.CLOSE).show();
    });
    setupElectionTask.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, event1 -> {
      progressIndicator.setVisible(false);
      new ExceptionDialogController("Το κλείσιμο της ψηφοφορίας απέτυχε.", setupElectionTask.getException());
    });
  }

  public void generateSafePrime() {
    safePrimeFld.setText(SafePrime.getRandomInstance(52).getValue().toString());
  }

  public void onCancel(ActionEvent event) throws Exception {
    Platform.exit();
  }

  private Optional<String> isElectionDataValid(long start, long end, String prime, int userCount) {
    boolean datesOk = start < end;
    if(!datesOk) {
      return Optional.of("Ημερομηνία έναρξης πρέπει να είναι μικρότερη από αυτή της λήξης.");
    }
    boolean primeOk = true;
    try {
      SafePrime safePrime = SafePrime.getInstance(new BigInteger(prime));
      if(safePrime.getValue().bitLength() < 48) { // don't accept low bitlength primes
        primeOk = false;
      } else if (safePrime.getValue().compareTo(BigInteger.valueOf((long) Math.floor(userCount / (float) 2))) != 1) {
        primeOk = false;
      }
    } catch (Exception e) {
      primeOk = false;
    }
    if(!primeOk) {
      return Optional.of("Ο κρυπτογραφικός αριθμός δεν είναι ασφαλής πρώτος και μεγαλύτερος του " + userCount);
    }
    return Optional.empty();
  }
}
