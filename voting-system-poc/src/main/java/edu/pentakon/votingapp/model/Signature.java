package edu.pentakon.votingapp.model;

import ch.bfh.unicrypt.math.algebra.general.classes.Tuple;

public class Signature {
  public final long first;
  public final long second;

  public Signature(long first, long second) {
    this.first = first;
    this.second = second;
  }

  public Signature(Tuple tuple) {
    this(tuple.getFirst().convertToBigInteger().longValueExact(),
        tuple.getLast().convertToBigInteger().longValueExact());
  }
}
