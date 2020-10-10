package edu.pentakon.votingapp.model;

public final class Ballot {

  public long choice;
  public NIProof proof;

  private Ballot() {
    // genson
  }

  public Ballot(long choice, NIProof proof) {
    this.choice = choice;
    this.proof = proof;
  }
}
