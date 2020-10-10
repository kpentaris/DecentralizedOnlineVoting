package edu.pentakon.votingapp.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public enum Choice {
  YES(1), NO(-1);

  private Integer value;
  private static Map<Integer, Choice> lookup = new HashMap<>();

  Choice(Integer value) {
    this.value = value;
  }

  public Integer getValue() {
    return this.value;
  }

  public static Choice get(Integer value) {
    return lookup.get(value);
  }

  public static Collection<Choice> get() {
    return lookup.values();
  }

  public static Collection<Integer> getKeys() {
    return lookup.keySet();
  }

  static {
    for (Choice value : Choice.values()) {
      lookup.put(value.getValue(), value);
    }
  }
}
