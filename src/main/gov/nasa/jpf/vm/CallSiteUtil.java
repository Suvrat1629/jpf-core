package gov.nasa.jpf.vm;

import java.security.MessageDigest;

public class CallSiteUtil {
  public static String sha256Hex(String input) {
    if (input == null) return "";
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes("UTF-8"));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(String.format("%02x", b & 0xff));
      }
      return sb.toString();
    } catch (Exception e) {
      return Integer.toHexString(input.hashCode());
    }
  }

  public static String shortFingerprint(String input, int len) {
    String h = sha256Hex(input);
    if (h.length() <= len) return h;
    return h.substring(0, len);
  }
}
