package edu.pentakon.votingapp.model;

import java.math.BigInteger;

public class Election {

  private String title;
  private long votingStart;
  private long votingEnd;
  private BigInteger cyclicGroupPrime;
  private String[] allowedVoterIds;

  public Election(String title, long votingStart, long votingEnd, BigInteger cyclicGroupPrime) {
    this.title = title;
    this.votingStart = votingStart;
    this.votingEnd = votingEnd;
    this.cyclicGroupPrime = cyclicGroupPrime;
  }

  public String getTitle() {
    return title;
  }

  public long getVotingStart() {
    return votingStart;
  }

  public long getVotingEnd() {
    return votingEnd;
  }

  public BigInteger getCyclicGroupPrime() {
    return cyclicGroupPrime;
  }

  public Election setAllowedVoterIds(String[] allowedVoterIds) {
    this.allowedVoterIds = allowedVoterIds;
    return this;
  }

  public String[] getAllowedVoterIds() {
    return allowedVoterIds;
  }
}
