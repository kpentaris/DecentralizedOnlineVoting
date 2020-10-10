package edu.pentakon.votingapp.model;

public class VotePayload {

  public String uid;
  public String ballot;
  public String signature;
  public long submissionTimestamp;

  public VotePayload(String uid, String ballot, String signature) {
    this.uid = uid;
    this.ballot = ballot;
    this.signature = signature;
  }
}
