package edu.pentakon.votingapp;

import ch.bfh.unicrypt.helper.factorization.SafePrime;
import ch.bfh.unicrypt.math.algebra.general.interfaces.Element;
import edu.pentakon.votingapp.model.AppMode;
import edu.pentakon.votingapp.model.KeyPair;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;

public class VotingCryptoServiceTest {

  private VotingCryptoService service;
  private String electionTitle = VotingCryptoServiceTest.class.getName();

  @BeforeEach
  public void setUp() throws Exception {
    VotingApplication.initialize(AppMode.TEST, "a");
    service = new VotingCryptoService(SafePrime.getRandomInstance(5), "randomseed", electionTitle);
  }

  @Test
  public void signMessageShouldNotThrowException() {
    String message = "asdf";
    KeyPair pair = service.generateKeyPair();
    String signature = service.signMessage(message, pair.privateKey);
    Assertions.assertThat(service.verifySignature(message, signature, pair.publicKey.convertToString())).isTrue();
  }

  @Test
  public void verifyOverkeyGenerationForThreeParticipants() {
    final SafePrime prime = SafePrime.getRandomInstance(5);
    VotingCryptoService userA = new VotingCryptoService(prime, "randomseedA", electionTitle);
    VotingCryptoService userB = new VotingCryptoService(prime, "randomseedB", electionTitle);
    VotingCryptoService userC = new VotingCryptoService(prime, "randomseedC", electionTitle);

    KeyPair pairA = userA.generateKeyPair();
    KeyPair pairB = userB.generateKeyPair();
    KeyPair pairC = userC.generateKeyPair();

    Element[] pubKeys = new Element[]{pairA.publicKey, pairB.publicKey, pairC.publicKey};
    Map<Element, Element> userAShares = userA.generateSecretKeyShares(pubKeys);
    Map<Element, Element> userBShares = userB.generateSecretKeyShares(pubKeys);
    Map<Element, Element> userCShares = userC.generateSecretKeyShares(pubKeys);

    Element[] userAOwn = new Element[]{
      userAShares.get(pairA.publicKey),
      userBShares.get(pairA.publicKey),
      userCShares.get(pairA.publicKey)
    };
    Element sumA = userA.addOwnKeyShares(Arrays.asList(userAOwn), pairA.privateKey);

    Element[] userBOwn = new Element[]{
      userAShares.get(pairB.publicKey),
      userBShares.get(pairB.publicKey),
      userCShares.get(pairB.publicKey)
    };
    Element sumB = userB.addOwnKeyShares(Arrays.asList(userBOwn), pairB.privateKey);

    Element[] userCOwn = new Element[]{
      userAShares.get(pairC.publicKey),
      userBShares.get(pairC.publicKey),
      userCShares.get(pairC.publicKey)
    };
    Element sumC = userC.addOwnKeyShares(Arrays.asList(userCOwn), pairC.privateKey);

    Element overkey = userA.recoverSharedOverkey(new Element[]{sumA, sumB, sumC});
    Assertions.assertThat(overkey.convertToBigInteger()).isEqualTo(BigInteger.valueOf(36));
  }
}
