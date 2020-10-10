package edu.pentakon.votingapp;

import ch.bfh.unicrypt.helper.factorization.SafePrime;
import edu.pentakon.votingapp.model.Election;

/**
 * Class that provides application scoped access to all service classes,
 * akin to Spring Framework @Service annotation and injection.
 * Essentially attempts to emulate part of the IoC logic with application scoped services.
 */
public final class ServicesContext {

  private static VotingCryptoService votingCryptoService;
  private static EthereumService ethereumService;
  private static VotingService votingService;

  public static void initialize(Class serviceClass) throws Exception {
    if(EthereumService.class.equals(serviceClass)) {
      if(ethereumService == null) {
        ethereumService = new EthereumService();
      }
    } else {
      throw new UnsupportedOperationException(String.format("Cannot initialize class %s by itself.", serviceClass.getName()));
    }
  }

  public static void initializeAll(SafePrime modQ, String password, Election election, String salt) throws Exception {
    if(votingCryptoService != null)
      return;

    // Must be very careful of initialization order. VotingService for example requires the existence of both EthereumService and CryptoService.
    if(ethereumService == null)
      ethereumService = new EthereumService();

    votingCryptoService = new VotingCryptoService(modQ, password, salt);
    votingService = new VotingService(election, votingCryptoService, ethereumService);
  }

  public static <C> C get(Class<C> serviceClass) {
    C rv = null;
    if(VotingCryptoService.class.equals(serviceClass))
      rv = (C) votingCryptoService;
    else if(EthereumService.class.equals(serviceClass))
      rv = (C) ethereumService;
    else if(VotingService.class.equals(serviceClass))
      rv = (C) votingService;
    else
      throw new IllegalArgumentException(serviceClass.getName() + " does not exist.");

    if(rv == null) {
      throw new IllegalStateException(serviceClass.getName() + " has not been initialized yet.");
    } else {
      return rv;
    }
  }
}
