package edu.pentakon.votingapp;

import ch.bfh.unicrypt.helper.factorization.SafePrime;
import ch.bfh.unicrypt.math.algebra.general.interfaces.Element;
import edu.pentakon.votingapp.model.AppMode;
import edu.pentakon.votingapp.model.Choice;
import edu.pentakon.votingapp.model.Election;
import edu.pentakon.votingapp.model.KeyPair;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VotingApplication {

  private static Logger logger = Logger.getLogger(VotingApplication.class.getName());

  public final AppMode appMode;
  public final String ssn;
  public final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
  private Election election; // could be optional but Java optionals were made for being return values and not really anything else...

  private static volatile VotingApplication instance;

  private VotingApplication(AppMode appMode, String ssn) {
    // private constructor for singleton
    this.appMode = appMode;
    this.ssn = ssn;
  }

  public static VotingApplication initialize(AppMode appMode, String ssn) throws Exception {
    instance = new VotingApplication(appMode, ssn);
    try {
      ServicesContext.initialize(EthereumService.class);
    } catch (Exception e) {
      throw new Exception("Αδυναμία σύνδεσης με Ethereum Blockchain.", e);
    }
    EthereumService ethereumService = ServicesContext.get(EthereumService.class);
    instance.election = ethereumService.getElection().orElse(null);
    return instance;
  }

  public static VotingApplication get() {
    if(instance == null)
      throw new IllegalStateException("Attempt to retrieve singleton instance before instantiation.");
    return instance;
  }

  public void stopApp() {
    service.shutdown();
  }

  public Optional<Election> getElection() {
    return Optional.ofNullable(election);
  }

  public VotingApplication setElection(Election election) {
    this.election = election;
    return this;
  }

  /**
   * The application can be called in multiple modes, depending on the first argument.
   * <p>
   * VOTE:
   * <p>
   * By launching the application with the VOTE command we emulate a single voter.
   * This application will essentially create the blockchain payload and
   * post it to the Ethereum smart contract via a local Geth client.
   * <p>
   * Two arguments are required:
   * 1. A user password from which his public and private keys can be derived.
   * 2. The user's selected choice in the vote, either YES (1) or NO (-1).
   * <p>
   * MPC:
   * <p>
   * By launching the application with the MPC command we generate for the specified
   * user (depending on the password provided) the partial Lagrange point from
   * the provided Shamir Secret Sharing values that have been encrypted with
   * the respective password's public key. These partial points will be posted
   * in the Ethereum smart contract via a local Geth client.
   * This mode should be used only after all the users have posted their votes.
   * <p>
   * One argument is required:
   * 1. The user's password.
   * <p>
   * TALLY:
   * <p>
   * By launching the application with the TALLY command we emulate a single user
   * that will start tallying the votes and retrieve the final result.
   * This mode should be used only after all users have posted their votes and the
   * global decryption key has been calculated by running the MPC mode for all the
   * users.
   * <p>
   * No arguments are required.
   */

  private void modeVote(String password, String choice) throws Exception {
    ServicesContext.initializeAll(SafePrime.getInstance(election.getCyclicGroupPrime()), password, election, election.getTitle());
    ServicesContext.get(VotingService.class).submitVote(Choice.valueOf(choice));
  }

  private void modeMPC(String password) throws Exception {
    EthereumService ethereumService = ServicesContext.get(EthereumService.class);

    ServicesContext.initializeAll(SafePrime.getInstance(election.getCyclicGroupPrime()), password, election, election.getTitle());
    VotingCryptoService cryptoService = ServicesContext.get(VotingCryptoService.class);
    String myUid = cryptoService.generateKeyPair().publicKey.convertToString();
    List<String> shares = ethereumService.getMPCShares(myUid);
    Element shareSum = cryptoService.addOwnKeyShares(shares);
    ethereumService.submitMPCSum(myUid, shareSum.convertToString());
  }

  private void modeTally(String password) throws Exception {
    ServicesContext.initializeAll(SafePrime.getInstance(election.getCyclicGroupPrime()), password, election, election.getTitle());
    VotingService votingService = ServicesContext.get(VotingService.class);
    int[] yes_no = votingService.tallyVotes();
    logger.log(Level.INFO, "Final tally amounts to YES: {} and NO: {}", yes_no);
  }

  private void modePrintUserId(String password) throws Exception {
    ServicesContext.initializeAll(SafePrime.getInstance(election.getCyclicGroupPrime()), password, election, election.getTitle());
    VotingCryptoService service = ServicesContext.get(VotingCryptoService.class);
    KeyPair pair = service.generateKeyPair();
    logger.log(Level.INFO, "Unique identifier is {}", pair.publicKey.convertToString());
  }

}
