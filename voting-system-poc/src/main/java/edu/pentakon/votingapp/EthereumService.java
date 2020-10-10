package edu.pentakon.votingapp;

import contract.VotingContract;
import edu.pentakon.votingapp.model.Election;
import edu.pentakon.votingapp.model.VotePayload;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tuples.generated.Tuple4;
import org.web3j.tx.gas.DefaultGasProvider;

import java.io.File;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Logger;

public class EthereumService {

  private static final Logger logger = Logger.getLogger(EthereumService.class.getName());

  public static final String chainUrl = "http://localhost:8543/";
  public static final String walletPassword = "seed";
  public static final String walletFilePath = "E:\\Temp\\geth\\blockchain\\keystore\\UTC--2020-05-18T20-27-54.470574600Z--842529e14bd005d0eb2642a53c27d87bf8289a0a";
  public static final String votingContractAddress = "0x06508c1A17aE4fC42D3163E698bd6Af2B29e79B4";

  private final Web3j web3j;
  private final VotingContract contract;

  public EthereumService() throws Exception {
    web3j = Web3j.build(new HttpService(chainUrl));
    Credentials creds = WalletUtils.loadCredentials(walletPassword, new File(walletFilePath));
    contract = VotingContract.load(votingContractAddress, web3j, creds, new DefaultGasProvider());
    String testResult = contract.testDeployment().send();
    if (!"Success!".equalsIgnoreCase(testResult)) {
      throw new Exception("Either contract wrapper creation failed or the contract is not deployed in the chain.");
    }
  }

  public Optional<Election> getElection() throws Exception {
    String title = contract.electionTitle().send();
    if(title == null || title.isEmpty()) {
      return Optional.empty();
    }
    long start = contract.starTimestamp().send().longValue();
    long voteEnd = contract.voteSubmitEndTS().send().longValue();
    BigInteger prime = new BigInteger(contract.cyclicGroupPrime().send());
    String[] allowedUIDs = contract.allowedUIDs().send().split(";");
    return Optional.of(new Election(title, start, voteEnd, prime).setAllowedVoterIds(allowedUIDs));
  }

  public String[] getParticipantPublicKeys() throws Exception {
    return contract.allowedUIDs().send().split(";");
  }

  public void setupElection(Election election) throws Exception {
    try {
      TransactionReceipt receipt = contract.updateElectionParameters(
        BigInteger.valueOf(election.getVotingStart()),
        BigInteger.valueOf(election.getVotingEnd()),
        election.getTitle(),
        election.getCyclicGroupPrime().toString()
      ).send();
      if (!receipt.isStatusOK()) {
        throw new Exception(String.format("Transaction status %s", receipt.getStatus()));
      }
    } catch (Exception e) {
      throw new Exception("Could not update election parameters.", e);
    }
  }

  public void submitVote(VotePayload payload, Map<String, String> mpcShares) throws Exception {
    List<String> uids = new ArrayList<>(mpcShares.keySet());
    List<String> shares = new ArrayList<>(mpcShares.values());
    try {
      TransactionReceipt receipt = contract.submitVote(payload.uid, payload.ballot, payload.signature, uids, shares).send();
      if (!receipt.isStatusOK()) {
        throw new Exception("Transaction status " + receipt.getStatus());
      }
    } catch (Exception e) {
      throw new Exception("Could not submit vote.", e);
    }
  }

  public void submitMPCSum(String publicKey, String value) throws Exception {
    try {
      TransactionReceipt receipt = contract.submitMPCShareSum(publicKey, value).send();
      if(!receipt.isStatusOK()) {
        throw new Exception("Transaction status " + receipt.getStatus());
      }
    } catch (Exception e) {
      throw new Exception("Could not submit MPC sum.", e);
    }
  }

  public boolean tallyVotes() throws Exception {
    return contract.tallyVotes().send();
  }

  public List<String> getMPCShares(String forUid) throws Exception {
    List<String> shares = new ArrayList<>();
    final String[] uids = getParticipantPublicKeys();
    for (int i = 0; i < uids.length; i++) {
      shares.add(contract.mpcShares(forUid, BigInteger.valueOf(i)).send());
    }
    return shares;
  }

  public Map<String, String> getMPCSums() throws Exception {
    Map<String, String> sums = new HashMap<>();
    String[] uids = getParticipantPublicKeys();
    for (String uid : uids) {
      sums.put(uid, contract.mpcSums(uid).send());
    }
    return sums;
  }

  public VotePayload[] getSubmittedVotes() throws Exception {
    final String[] uids = getParticipantPublicKeys();
    VotePayload[] votes = new VotePayload[uids.length];
    for (int i = 0; i < uids.length; i++) {
      final Tuple4<String, String, BigInteger, String> result = contract.votes(uids[i]).send();
      VotePayload payload = new VotePayload(result.component1(), result.component2(), result.component4());
      payload.submissionTimestamp = result.component3().longValue();
      votes[i] = payload;
    }
    return votes;
  }

  public BigInteger getVoteEndTS() throws Exception {
    return contract.voteSubmitEndTS().send();
  }

  public void endElection() throws Exception {
      contract.endElection().send();
  }

}
