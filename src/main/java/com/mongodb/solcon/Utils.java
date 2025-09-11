package com.mongodb.solcon;

import java.util.Random;

public class Utils {

  public static String BigRandomText(int size) {

    String lettersByFrequency =
        "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
            + // E
            "ttttttttttttttttttttttttt"
            + // T
            "aaaaaaaaaaaaaaaaaaaaaa"
            + // A
            "ooooooooooooooooooo"
            + // O
            "iiiiiiiiiiiiiiiiiiiii"
            + // I
            "nnnnnnnnnnnnnnnnnnnn"
            + // N
            "ssssssssssssssssssss"
            + // S
            "hhhhhhhhhhhhhhh"
            + // H
            "rrrrrrrrrrrrrrrrrr"
            + // R
            "ddddddddddddddd"
            + // D
            "lllllllllll"
            + // L
            "cccccccccc"
            + // C
            "uuuuuuuuu"
            + // U
            "mmmmmmmm"
            + // M
            "wwwwwwww"
            + // W
            "fffffff"
            + // F
            "ggggggg"
            + // G
            "yyyyyyyy"
            + // Y
            "ppppppp"
            + // P
            "bbbbbb"
            + // B
            "vvvvv"
            + // V
            "kkk"
            + // K
            "jj"
            + // J
            "xx"
            + // X
            "q"
            + // Q
            "z"; // Z

    String digits = "0123456789";
    String chars = lettersByFrequency + digits;

    Random rand = new Random();
    StringBuilder sb = new StringBuilder(size);

    boolean capitalizeNext = true;

    for (int i = 0; i < size; i++) {
      if (i > 0 && i % 60 == 0) {
        sb.append('.');
        capitalizeNext = true;
      } else if (i > 0 && i % 6 == 0) {
        sb.append(' ');
      } else {
        char nextChar;
        if (capitalizeNext) {
          // Pick a letter from lettersByFrequency and make it uppercase
          nextChar =
              Character.toUpperCase(
                  lettersByFrequency.charAt(rand.nextInt(lettersByFrequency.length())));
          capitalizeNext = false;
        } else {
          int r = rand.nextInt(chars.length());
          nextChar = chars.charAt(r);
        }
        sb.append(nextChar);
      }
    }

    return sb.toString();
  }
}
