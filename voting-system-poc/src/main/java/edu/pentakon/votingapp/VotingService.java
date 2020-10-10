package edu.pentakon.votingapp;

import ch.bfh.unicrypt.math.algebra.general.interfaces.Element;
import com.owlike.genson.Genson;
import com.owlike.genson.GensonBuilder;
import com.owlike.genson.reflect.VisibilityFilter;
import edu.pentakon.votingapp.model.*;

import java.math.BigInteger;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class VotingService {

  private Logger logger = Logger.getLogger(VotingService.class.getName());

  private static final Genson genson = new GensonBuilder().setConstructorFilter(VisibilityFilter.ALL).create();

  // Services
  private final VotingCryptoService cryptoService;
  private final EthereumService ethereumService;

  private final Election election;
  private final KeyPair keyPair;

  public VotingService(Election election, VotingCryptoService cryptoService, EthereumService ethereumService) throws Exception {
    this.cryptoService = cryptoService;
    this.ethereumService = ethereumService;
    keyPair = cryptoService.generateKeyPair();
    this.election = election;
  }

  public String getUID() {
    return keyPair.publicKey.convertToString();
  }

  /**
   * Creates a voter ballot based on his public/private key pair and the provided {@link Choice}.
   */
  private Ballot createBallot(Choice choice) {
    BigInteger encryptedChoice = cryptoService.encryptChoice(choice);
    NIProof proof = choice == Choice.YES
      ? cryptoService.createProofOfValidityYES(keyPair.publicKey)
      : cryptoService.createProofOfValidityNO(keyPair.publicKey);
    return new Ballot(encryptedChoice.longValue(), proof);
  }

  public void submitVote(Choice choice) throws Exception {
    Genson genson = new GensonBuilder().create();
    logger.info("Submitting vote for public key " + keyPair.publicKey.convertToString());
    String pubKey = keyPair.publicKey.convertToString();

    // create a ballot with the vote
    Ballot ballot = createBallot(choice);
    String serializedBallot = genson.serialize(ballot);

    // sign ballot payload
    String signature = cryptoService.signMessage(serializedBallot, keyPair.privateKey);

    // generate MPC shares for vote overkey generation
    String[] participantKeys = election.getAllowedVoterIds();
    Map<String, String> shares = cryptoService.generateSecretKeyShares(participantKeys);
    ethereumService.submitVote(new VotePayload(pubKey, serializedBallot, signature), shares);
    logger.info("Vote was successfully submitted");
  }

  public void submitMPCSum() throws Exception {
    String myUid = getUID();
    logger.log(Level.INFO, "Submitting MPC Shares for user {0}", new Object[]{myUid});
    List<String> shares = ethereumService.getMPCShares(myUid);
    Element shareSum = cryptoService.addOwnKeyShares(shares);
    ethereumService.submitMPCSum(myUid, shareSum.convertToString());
    logger.log(Level.INFO, "Shares submitted for user {0}", new Object[]{myUid});
  }

  public int[] tallyVotes() throws Exception {
    List<VotePayload> payloads = filterValidVotes(ethereumService.getSubmittedVotes());
    BigInteger overkey = generateOverkey();
    Set<BigInteger> votes = payloads.stream()
      .map(payload -> genson.deserialize(payload.ballot, Ballot.class))
      .map(ballot -> BigInteger.valueOf(ballot.choice))
      .collect(Collectors.toSet());
    BigInteger encryptedTally = cryptoService.tallyVotes(votes);
    long tallyResult = cryptoService.decryptTally(encryptedTally, overkey, payloads.size());
    int[] yesNo = new int[2];
    if (votes.size() % 2 == 0) { // even number of votes
      int halfVoteCount = votes.size() / 2;
      if (tallyResult == 0) {
        yesNo[0] = yesNo[1] = halfVoteCount;
        return yesNo;
      }

      int winningSideDiff = Math.abs((int) (tallyResult / 2));
      if (tallyResult < 0) {
        yesNo[0] = halfVoteCount - winningSideDiff;
        yesNo[1] = halfVoteCount + winningSideDiff;
      } else {
        yesNo[0] = halfVoteCount + winningSideDiff;
        yesNo[1] = halfVoteCount - winningSideDiff;
      }
    } else { // odd number of votes
      int halfVoteCount = (votes.size() - 1) / 2;
      int winningSideDiff = (int) ((Math.abs(tallyResult) - 1) / 2);
      if (tallyResult < 0) {
        yesNo[0] = halfVoteCount - winningSideDiff;
        yesNo[1] = halfVoteCount + winningSideDiff;
        yesNo[1]++;
      } else {
        yesNo[0] = halfVoteCount + winningSideDiff;
        yesNo[0]++;
        yesNo[1] = halfVoteCount - winningSideDiff;
      }
    }
    return yesNo;
  }

  public boolean checkElectionEnded() throws Exception {
    BigInteger voteEndTS = ethereumService.getVoteEndTS(); // we must poll the voting end because the administrator might end the election early
    // Ethereum timestamps are Epoch Seconds, not millis like Java
    return new Date().getTime() / 1000 > voteEndTS.longValueExact();
  }

  private List<VotePayload> filterValidVotes(VotePayload[] submittedVotes) {
    final List<VotePayload> acceptedVotes = new ArrayList<>();
    for (int i = 0; i < submittedVotes.length; i++) {
      VotePayload payload = submittedVotes[i];
      Ballot ballot = genson.deserialize(payload.ballot, Ballot.class);
      NIProof proof = ballot.proof;
      if (cryptoService.verifyProof(proof) && payload.submissionTimestamp <= election.getVotingEnd()) {
        // TODO Add verifySignature check! Also Vote timestamp validity, allowed voter id etc
        acceptedVotes.add(payload);
      }
    }
    return acceptedVotes;
  }

  private BigInteger generateOverkey() throws Exception {
    Map<String, String> mpcSums = ethereumService.getMPCSums();
    return cryptoService.recoverSharedOverkey(mpcSums.values()).convertToBigInteger();
  }

}
