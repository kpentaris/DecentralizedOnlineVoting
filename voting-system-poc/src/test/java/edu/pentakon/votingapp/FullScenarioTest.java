package edu.pentakon.votingapp;

import ch.bfh.unicrypt.helper.array.classes.ByteArray;
import ch.bfh.unicrypt.helper.factorization.SafePrime;
import ch.bfh.unicrypt.math.algebra.general.interfaces.Element;
import com.owlike.genson.Genson;
import com.owlike.genson.GensonBuilder;
import com.owlike.genson.reflect.VisibilityFilter;
import edu.pentakon.votingapp.model.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Runs a full voting scenario with 5 voters.
 * Uses mocked EthereumService.
 */
@TestMethodOrder(value = MethodOrderer.OrderAnnotation.class)
@TestInstance(value = TestInstance.Lifecycle.PER_CLASS)
@MockitoSettings(strictness = Strictness.LENIENT)
public class FullScenarioTest {

  private static Genson genson = new GensonBuilder()
    .setConstructorFilter(VisibilityFilter.ALL)
    .create();

  private SafePrime modQ = SafePrime.getRandomInstance(50); // random 50 bit safe prime
  private String[] voterPasswords = new String[]{
    "4libVkvJt4qyU4EW039Q",
    "zVDDeFaCObXtTK9Rj8YA",
    "RE1SolFDaEhM8OhYLUxT",
    "kTvIFJpS6vjenc3MONdg",
    "MJMEzsEbiZDzgIzHxPGT"
  };
  private Choice[] votes = new Choice[]{
    Choice.NO,
    Choice.NO,
    Choice.NO,
    Choice.YES,
    Choice.YES
  };

  private Election election = new Election(FullScenarioTest.class.getName(), 0,0,null);
  private BigInteger sharedKey = null;
  private BigInteger overKey = null;

  @Mock
  EthereumService ethereumService;
  private VotingService[] votingServices = new VotingService[5];
  private VotingCryptoService[] cryptoServices = new VotingCryptoService[5];
  private List<VotePayload> submittedVotes = new ArrayList<>();
  private List<Map<String, String>> allShares = new ArrayList<>();

  @BeforeAll
  static void setUpAll() throws Exception {
    VotingApplication.initialize(AppMode.TEST, "ssn");
  }

  @BeforeEach
  void setUpEach() throws Exception {
    if (cryptoServices[0] != null) {
      when(ethereumService.getParticipantPublicKeys())
        .thenReturn(
          Arrays.stream(cryptoServices)
            .map(crypto -> crypto.generateKeyPair().publicKey.convertToString())
            .toArray(String[]::new)
        );

      doAnswer(invocationOnMock -> submittedVotes.add(invocationOnMock.getArgument(0, VotePayload.class)))
        .when(ethereumService)
        .submitVote(any(VotePayload.class), any(Map.class));
    }
  }

  @Test
  @Order(1)
  void testSetupVoterCryptoService() {
    for (int i = 0; i < voterPasswords.length; i++) {
      String pwd = voterPasswords[i];
      cryptoServices[i] = new VotingCryptoService(modQ, pwd, election.getTitle());

      // save shared key here so we can compare when generating overkey
      BigInteger secretKey = new BigInteger(accessSecret(cryptoServices[i]).getBytes());
      if (sharedKey == null)
        sharedKey = secretKey;
      else
        sharedKey = sharedKey.add(secretKey);
    }
  }

  @Test
  @Order(2)
  void testSetupVoterService() throws Exception {
    for (int i = 0; i < voterPasswords.length; i++) {
      votingServices[i] = new VotingService(election, cryptoServices[i], ethereumService);
    }
  }

  @Test
  @Order(3)
  void testVoteSubmission() throws Exception {
    for (int i = 0; i < votingServices.length; i++) {
      VotingService service = votingServices[i];
      service.submitVote(votes[i]);
    }
  }

  @Test
  @Order(4)
  void testVoteValidity() {
    boolean[] proofs = new boolean[cryptoServices.length];
    for (int i = 0; i < submittedVotes.size(); i++) {
      VotePayload votePayload = submittedVotes.get(i);
      Ballot ballot = genson.deserialize(votePayload.ballot, Ballot.class);
      NIProof proof = ballot.proof;
      proofs[i] = cryptoServices[0].verifyProof(proof);
    }
    Assertions.assertThat(proofs).doesNotContain(false);
  }

  @Test
  @Order(5)
  void testOverkeyGeneration() throws Exception {
    Element[] shareSums = new Element[cryptoServices.length];
    final String[] uids = ethereumService.getParticipantPublicKeys();
    for (int i = 0; i < cryptoServices.length; i++) {
      shareSums[i] = cryptoServices[i].addOwnKeyShares(allShares);
    }
    overKey = cryptoServices[0].recoverSharedOverkey(shareSums).convertToBigInteger();
    Assertions.assertThat(overKey.equals(sharedKey)).isTrue();
  }

  @Test
  @Order(6)
  void testVoteSignatures() {
    boolean[] verifications = new boolean[submittedVotes.size()];
    for (int i = 0; i < submittedVotes.size(); i++) {
      VotePayload vote = submittedVotes.get(i);
      verifications[i] = cryptoServices[0].verifySignature(vote.ballot, vote.signature, vote.uid);
    }
    Assertions.assertThat(verifications).doesNotContain(false);
  }

  @Test
  @Order(7)
  void testTallyCalculation() {
    Set<BigInteger> votes = new HashSet<>();
    for (int i = 0; i < submittedVotes.size(); i++) {
      VotePayload votePayload = submittedVotes.get(i);
      Ballot ballot = genson.deserialize(votePayload.ballot, Ballot.class);
      votes.add(BigInteger.valueOf(ballot.choice));
    }
    BigInteger tally = cryptoServices[0].tallyVotes(votes);
    long calculatedResult = cryptoServices[0].decryptTally(tally, overKey, votes.size());
    long finalResult = Arrays.stream(this.votes).map(Choice::getValue).reduce(Integer::sum).get();
    Assertions.assertThat(calculatedResult == finalResult).isTrue();
  }

  private static ByteArray accessSecret(VotingCryptoService service) {
    try {
      Field secretF = VotingCryptoService.class.getDeclaredField("secret");
      secretF.setAccessible(true);
      return (ByteArray) secretF.get(service);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
