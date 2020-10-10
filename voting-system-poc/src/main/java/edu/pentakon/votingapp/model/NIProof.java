package edu.pentakon.votingapp.model;

import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModElement;
import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModPrime;

import java.math.BigInteger;

/**
 * Value object class that contains all the values required for the Non-Interactive proof of validity.
 */
public class NIProof {
  public String y;
  public String b1;
  public String b2;
  public String r1;
  public String r2;
  public String d1;
  public String d2;
  public String c;

  public boolean verify(GStarModPrime group, GStarModElement g, GStarModElement G) { // TODO verify c validity with SHA256 and pub key
    final BigInteger Y = new BigInteger(y);
    final BigInteger B1 = new BigInteger(b1);
    final BigInteger B2 = new BigInteger(b2);
    final BigInteger C = new BigInteger(c);
    final BigInteger D1 = new BigInteger(d1);
    final BigInteger D2 = new BigInteger(d2);
    final BigInteger R1 = new BigInteger(r1);
    final BigInteger R2 = new BigInteger(r2);
    GStarModElement y = group.getElementFrom(Y);
    boolean cOk = D1.add(D2).equals(C);
//    boolean b1Ok = b1 == g.power(r1).multiply(G.power(d1).multiply(y.power(d1))).getValue().longValueExact();
    boolean b1Ok = g.power(R1).multiply(G.power(D1).multiply(y.power(D1))).convertToBigInteger().equals(B1);
//    boolean b2Ok = b2 == g.power(r2).multiply(y.divide(G).power(d2)).getValue().longValueExact();
    boolean b2Ok = g.power(R2).multiply(y.divide(G).power(D2)).convertToBigInteger().equals(B2);
    return cOk && b1Ok && b2Ok;
  }

  public NIProof setY(String y) {
    this.y = y;
    return this;
  }

  public NIProof setB1(String b1) {
    this.b1 = b1;
    return this;
  }

  public NIProof setB2(String b2) {
    this.b2 = b2;
    return this;
  }

  public NIProof setR1(String r1) {
    this.r1 = r1;
    return this;
  }

  public NIProof setR2(String r2) {
    this.r2 = r2;
    return this;
  }

  public NIProof setD1(String d1) {
    this.d1 = d1;
    return this;
  }

  public NIProof setD2(String d2) {
    this.d2 = d2;
    return this;
  }

  public NIProof setC(String c) {
    this.c = c;
    return this;
  }
}
