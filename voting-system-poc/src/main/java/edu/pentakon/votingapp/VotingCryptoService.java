package edu.pentakon.votingapp;

import ch.bfh.unicrypt.crypto.schemes.encryption.classes.RSAEncryptionScheme;
import ch.bfh.unicrypt.crypto.schemes.encryption.interfaces.AsymmetricEncryptionScheme;
import ch.bfh.unicrypt.crypto.schemes.sharing.classes.ShamirSecretSharingScheme;
import ch.bfh.unicrypt.crypto.schemes.sharing.interfaces.SecretSharingScheme;
import ch.bfh.unicrypt.helper.array.classes.ByteArray;
import ch.bfh.unicrypt.helper.factorization.Prime;
import ch.bfh.unicrypt.helper.factorization.SafePrime;
import ch.bfh.unicrypt.helper.hash.HashAlgorithm;
import ch.bfh.unicrypt.math.algebra.dualistic.classes.ZModPrime;
import ch.bfh.unicrypt.math.algebra.dualistic.classes.ZModPrimePair;
import ch.bfh.unicrypt.math.algebra.general.classes.Pair;
import ch.bfh.unicrypt.math.algebra.general.classes.Tuple;
import ch.bfh.unicrypt.math.algebra.general.interfaces.Element;
import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModElement;
import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModPrime;
import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModSafePrime;
import edu.pentakon.votingapp.model.Choice;
import edu.pentakon.votingapp.model.KeyPair;
import edu.pentakon.votingapp.model.NIProof;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class VotingCryptoService {
  private Logger logger = Logger.getLogger(VotingCryptoService.class.getName());

  private final MessageDigest SHA256;
  private final String keyPairSeed;
  private final ByteArray votingSecret;
  private final GStarModPrime cyclicGroup;
  private final GStarModElement generator_g;
  private final GStarModElement generator_G;

  private static final AsymmetricEncryptionScheme RSA;
  static {
    ZModPrimePair zMod = ZModPrimePair.getInstance(Prime.getFirstInstance(512), Prime.getFirstInstance(511));
//    ZModPrimePair zMod = ZModPrimePair.getInstance(Prime.getRandomInstance(32), Prime.getRandomInstance(31));
    RSA = RSAEncryptionScheme.getInstance(zMod);
  }

  public VotingCryptoService(SafePrime modQ, String password, String salt) {
    logger.log(Level.INFO, "Initializing crypto service with secret {0}, salt {1}, prime {2}", new Object[]{password, salt, modQ.toString()});
    cyclicGroup = GStarModSafePrime.getInstance(modQ);
    generator_g = cyclicGroup.getIndependentGenerator(0);
    generator_G = cyclicGroup.getIndependentGenerator(1);
    logger.log(Level.INFO, "Generator G: {0}, g: {1}", new Object[]{generator_G.toString(), generator_g.toString()});

    if (generator_G.equals(generator_g)) {
      throw new RuntimeException(String.format("Provided modQ group %s does not have 2 distinct generators.", modQ.toString()));
    }

    try {
      SHA256 = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Java implementation is missing SHA256 algorithm. Please change to a normal implementation.");
    }

    votingSecret = generateSecret(password, salt);
    keyPairSeed = new String(HashAlgorithm.SHA256.getHashValue(password.getBytes()));
    logger.log(Level.INFO, "Generated secret: " + votingSecret.toString());
  }

  /**
   * Based on a predetermined password, the user can generate different but deterministic
   * secret values for each election if the password is applied along with a  salt value
   * in order to generate a SHA256 hash which will play the role of the secret for the
   * specific election.
   *
   * TODO Find a better and most importantly unique/user salt value that changes per election
   *  but cannot be guessed by an attacker. Using the election title is unique per election
   *  but is known to the attackers for people that use the open source version of the voting client.
   *
   * @param password
   * @return
   */
  private ByteArray generateSecret(String password, String salt) {
    String seed = password + salt;
    byte[] hash = HashAlgorithm.SHA256.getHashValue(seed.getBytes());
    logger.log(Level.INFO, "Seed hash " + Hex.toHexString(hash));
    final BigInteger secretInt = new BigInteger(hash).mod(cyclicGroup.getModulus()); // arbitrary secret must be transformed to one of the group's elements
    logger.log(Level.INFO, "Secret integer " + secretInt.toString());
    return generator_g.power(generator_g.power(secretInt)).convertToByteArray();
  }

  private AsymmetricEncryptionScheme getRSAScheme() {
    return RSA;
  }

  /**
   * Generates a deterministic public/private key pair based on the provided password.
   * This Pub/Priv pair is to be used as the user identifier as well as for payload signatures.
   *
   * @return the generated keypair object that contains the private and public keys
   */
  public KeyPair generateKeyPair() {
    AsymmetricEncryptionScheme rsa = getRSAScheme();
    // provide seed for deterministic signature generation for the cyclicGroup
    Pair keyPair = rsa.getKeyPairGenerator().generateKeyPair(keyPairSeed);
    // Can recreate the private and public keys by providing the "convertToString" values of these elements.
    Element privateKey = keyPair.getFirst();
    Element publicKey = keyPair.getSecond();
    return new KeyPair(privateKey, publicKey);
  }

  public String signMessage(String message, Element privateKey) {
    // TODO could change this to use the RSA pub key as a password to generate Schnorr signature
    AsymmetricEncryptionScheme rsa = getRSAScheme();
    String[] encryptedChunks = new String[(int) Math.ceil(message.length() / (float) 100)];
    for (int i = 0; i < encryptedChunks.length; i++) {
      String chunk = message.substring(100 * i, Math.min(i * 100 + 99, message.length()));
      Element msg = rsa.getMessageSpace().getElementFrom(ByteArray.getInstance(chunk.getBytes()));
      encryptedChunks[i] = rsa.encrypt(privateKey, msg).convertToString();
    }
    return String.join("|", encryptedChunks);
  }

  public boolean verifySignature(String message, String msgSignature, String publicKey) {
    String[] encryptedChunks = msgSignature.split("\\|");
    AsymmetricEncryptionScheme rsa = getRSAScheme();
    Element pubKeyEl = rsa.getDecryptionKeySpace().getElementFrom(publicKey);
    StringBuilder decryptedMsg = new StringBuilder();
    for (String chunk : encryptedChunks) {
      Element msg = rsa.getEncryptionSpace().getElementFrom(chunk);
      Element verification = rsa.decrypt(pubKeyEl, msg);
      decryptedMsg.append(verification.convertToString());
    }

    String[] verificationChunks = new String[(int) Math.ceil(message.length() / (float) 100)];
    for (int i = 0; i < verificationChunks.length; i++) {
      String chunk = message.substring(100 * i, Math.min(i * 100 + 99, message.length()));
      Element msg = rsa.getMessageSpace().getElementFrom(ByteArray.getInstance(chunk.getBytes()));
      encryptedChunks[i] = msg.convertToString();
    }
    message = String.join("", encryptedChunks);
    return message.equals(decryptedMsg.toString());
  }

  public BigInteger getCombinedKey(Collection<BigInteger> keys) {
    Element result = null;
    for (BigInteger key : keys) {
      if (result == null)
        result = cyclicGroup.getElementFrom(key);
      else
        result = result.apply(cyclicGroup.getElementFrom(key));
    }
    return result.convertToBigInteger();
  }

  public BigInteger encryptChoice(Choice choice) {
    return encryptChoice(choice.getValue());
  }

  /**
   * Method produces the encrypted ciphertext of the voter's choice based on his provided password.
   * The encryption scheme in this case must be additively homomorphic.
   * In our case we use a scheme inspired by ElGamal, based on the Discrete Log assumption.
   *
   * @param choice
   * @return
   */
  public BigInteger encryptChoice(int choice) {
    Element h = generator_g.power(cyclicGroup.getElementFrom(votingSecret));
    Element vote = h.apply(generator_G.power(choice));
    return vote.convertToBigInteger();
  }

  public BigInteger tallyVotes(Set<BigInteger> votes) {
    if (votes.size() == 0) {
      return BigInteger.ZERO;
    }
    Set<Element> elVotes = votes.stream().map(cyclicGroup::getElementFrom).collect(Collectors.toSet());
    Element tally = null;
    for (Element vote : elVotes) {
      if (tally == null)
        tally = vote;
      else
        tally = tally.apply(vote);
    }
    return tally.convertToBigInteger();
  }

  public long decryptTally(BigInteger tally, BigInteger overkey, long voterCount) {
    Element tallyEl = cyclicGroup.getElementFrom(tally);
    Element decrypted = generator_g.selfApply(overkey).invert().multiply(tallyEl);
    long result = 0;
    for (long i = -5; i <= voterCount; i++) {
      if (decrypted.equals(generator_G.selfApply(i))) {
        result = i;
        break;
      }
    }
    return result;
  }

  public NIProof createProofOfValidityYES(Element publicKey) {
    final long w = cyclicGroup.getRandomElement().getValue().longValueExact();
    final long r1 = cyclicGroup.getRandomElement().getValue().longValueExact();
    final long d1 = cyclicGroup.getRandomElement().getValue().longValueExact();

    final GStarModElement secretEl = cyclicGroup.getElementFrom(votingSecret);
    final GStarModElement h = generator_g.power(secretEl); // g^s
    final GStarModElement G_exp_v = generator_G.power(1); // G^v
    final GStarModElement y = h.multiply(G_exp_v);

    final GStarModElement g_exp_r1 = generator_g.power(r1); // g^r1
    final GStarModElement y_times_G_exp_d1 = y.multiply(generator_G).power(d1); // (y*G)^d1
    final GStarModElement b1 = g_exp_r1.multiply(y_times_G_exp_d1); // g^r1 * (y * G)^d1
    final GStarModElement b2 = generator_g.power(w); // g^w

    // c = SHA256(PubKey, y, b1, b2)
    SHA256.update(publicKey.convertToString().getBytes());
    SHA256.update(y.convertToByteArray().getBytes());
    SHA256.update(b1.convertToByteArray().getBytes());
    SHA256.update(b2.convertToByteArray().getBytes());
    final BigInteger c = new BigInteger(SHA256.digest()).abs().mod(cyclicGroup.getModulus());

    final BigInteger d2 = c.subtract(BigInteger.valueOf(d1)); // not part of the Group, no modp needed
    final BigInteger r2 = BigInteger.valueOf(w).subtract(secretEl.convertToBigInteger().multiply(d2)); // not part of the Group, no modp needed

    return new NIProof()
      .setY(y.convertToString())
      .setB1(b1.convertToString())
      .setB2(b2.convertToString())
      .setC(c.toString())
      .setR1(r1 + "")
      .setR2(r2.toString())
      .setD1(d1 + "")
      .setD2(d2.toString());
  }

  public NIProof createProofOfValidityNO(Element publicKey) {
    final long w = cyclicGroup.getRandomElement().getValue().longValueExact();
    final long r2 = cyclicGroup.getRandomElement().getValue().longValueExact();
    final long d2 = cyclicGroup.getRandomElement().getValue().longValueExact();

    final GStarModElement secretEl = cyclicGroup.getElementFrom(votingSecret);
    final GStarModElement h = generator_g.power(secretEl); // g^s
    final GStarModElement G_exp_v = generator_G.invert(); // G^v
    final GStarModElement y = h.multiply(G_exp_v);

    final GStarModElement g_exp_r2 = generator_g.power(r2); // g^r2
    final GStarModElement y_div_G_exp_d2 = y.divide(generator_G).power(d2); // (y/G)^d2
    final GStarModElement b1 = generator_g.power(w); // g^w
    final GStarModElement b2 = g_exp_r2.multiply(y_div_G_exp_d2); // g^r2 * (y/G)^d2

    // c = SHA256(PubKey, y, b1, b2)
    SHA256.update(publicKey.convertToString().getBytes());
    SHA256.update(y.convertToByteArray().getBytes());
    SHA256.update(b1.convertToByteArray().getBytes());
    SHA256.update(b2.convertToByteArray().getBytes());
    final BigInteger c = new BigInteger(SHA256.digest()).abs().mod(cyclicGroup.getModulus());

    final BigInteger d1 = c.subtract(BigInteger.valueOf(d2)); // not part of the Group, no modp needed
    final BigInteger r1 = BigInteger.valueOf(w).subtract(secretEl.convertToBigInteger().multiply(d1)); // not part of the Group, no modp needed

    return new NIProof()
      .setY(y.convertToString())
      .setB1(b1.convertToString())
      .setB2(b2.convertToString())
      .setC(c.toString())
      .setR1(r1.toString())
      .setR2(r2 + "")
      .setD1(d1.toString())
      .setD2(d2 + "");
  }

  public boolean verifyProof(NIProof proof) {
    return proof.verify(cyclicGroup, generator_g, generator_G);
  }

  private SecretSharingScheme getSharingScheme(int size, int threshold) {
    ZModPrime z = ZModPrime.getFirstInstance(60);
    SecretSharingScheme sss = ShamirSecretSharingScheme.getInstance(z, size, threshold);
    return sss;
  }

  public Map<String, String> generateSecretKeyShares(String[] recipientPublicKeys) {
    Element[] keyEls = new Element[recipientPublicKeys.length];
    AsymmetricEncryptionScheme rsa = getRSAScheme();
    for (int i = 0; i < recipientPublicKeys.length; i++) {
      keyEls[i] = rsa.getKeyPairGenerator().getPublicKeySpace().getElementFrom(recipientPublicKeys[i]);
    }
    Map<Element, Element> shares = generateSecretKeyShares(keyEls);
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<Element, Element> entry : shares.entrySet()) {
      result.put(entry.getKey().convertToString(), entry.getValue().convertToString());
    }
    return result;
  }

  /**
   * The Shamir shares cannot be given randomly.
   * For a set of Shamir polynomials fi(x) where i in [1,5] the same user must receive all
   * shares of fi(1) while another user must receive all shares of fi(2).
   * If a user receives f1(1) and f2(2) then the sharing will not work properly.
   * To that end, the recipient keys are naturally sorted so that all participants
   * provide the proper keys to the proper recipients.
   *
   * @param recipientPublicKeys
   * @return
   */
  public Map<Element, Element> generateSecretKeyShares(Element[] recipientPublicKeys) {
    AsymmetricEncryptionScheme rsa = getRSAScheme();
    Map<Element, Element> encryptedShares = new HashMap<>();
    SecretSharingScheme sss = getSharingScheme(recipientPublicKeys.length, recipientPublicKeys.length);
    Element message = sss.getMessageSpace().getElementFrom(votingSecret);
    Tuple shares = sss.share(message);
    Arrays.sort(recipientPublicKeys, Comparator.comparing(Element::convertToBigInteger));
    for (int i = 0; i < recipientPublicKeys.length; i++) {
      Element pubKey = recipientPublicKeys[i];
      Element share = shares.getAt(i);
      Element rsaShare = rsa.getMessageSpace().getElementFrom(share.convertToBigInteger());
      encryptedShares.put(pubKey, rsa.encrypt(pubKey, rsaShare));
    }
    return encryptedShares;
  }

  public Element addOwnKeyShares(List<Map<String, String>> allUserShares) {
    Collection<String> shares = allUserShares.stream()
      .map(map -> map.get(generateKeyPair().publicKey.convertToString()))
      .collect(Collectors.toList());
    return addOwnKeyShares(shares);
  }

  public Element addOwnKeyShares(Collection<String> encryptedShares) {
    AsymmetricEncryptionScheme rsa = getRSAScheme();
    List<Element> shares = encryptedShares.stream()
      .map(share -> rsa.getEncryptionSpace().getElementFrom(share))
      .collect(Collectors.toList());
    return addOwnKeyShares(shares, generateKeyPair().privateKey);
  }

  public Element addOwnKeyShares(Collection<Element> encryptedShares, Element decryptionKey) {
    AsymmetricEncryptionScheme rsa = getRSAScheme();
    SecretSharingScheme sss = getSharingScheme(encryptedShares.size(), encryptedShares.size());
    Set<Element> shares = encryptedShares.stream()
      .map(share -> rsa.decrypt(decryptionKey, share))
      .map(share -> sss.getShareSpace().getElementFrom(share.convertToBigInteger()))
      .collect(Collectors.toSet());

    Element sum = null;
    for (Element share : shares) {
      if(sum == null) {
        sum = share;
        continue;
      }
      sum = sum.apply(share);
    }
    return sum;
  }

  public Element recoverSharedOverkey(Collection<String> shares) {
    return recoverSharedOverkey(shares.toArray(new String[0]));
  }

  public Element recoverSharedOverkey(String[] shares) {
    SecretSharingScheme sss = getSharingScheme(shares.length, shares.length);
    Element[] sharesArr = Arrays.stream(shares).map(share -> sss.getShareSpace().getElementFrom(share)).toArray(Element[]::new);
    return recoverSharedOverkey(sharesArr);
  }

  public Element recoverSharedOverkey(Element[] shares) {
    SecretSharingScheme sss = getSharingScheme(shares.length, shares.length);
    return sss.recover(shares);
  }

}
