package edu.pentakon.votingapp.model;

import ch.bfh.unicrypt.math.algebra.general.interfaces.Element;

public class KeyPair {

  public Element privateKey;
  public Element publicKey;

  public KeyPair(Element privateKey, Element publicKey) {
    this.privateKey = privateKey;
    this.publicKey = publicKey;
  }
}
