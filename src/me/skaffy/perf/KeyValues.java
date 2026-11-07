package me.skaffy.perf;

import java.util.LinkedHashMap;
import java.util.Map;

/** "key=value;key=value" text for small maps kept in one config property. Keys and values must not contain ';' or '='. */
final class KeyValues {
   private KeyValues() {
   }

   static String encode(Map<String, String> map) {
      StringBuilder b = new StringBuilder();
      for (Map.Entry<String, String> e : map.entrySet()) {
         if (b.length() > 0) {
            b.append(';');
         }

         b.append(e.getKey()).append('=').append(e.getValue());
      }

      return b.toString();
   }

   static Map<String, String> decode(String s) {
      Map<String, String> map = new LinkedHashMap<>();
      if (s == null) {
         return map;
      }

      for (String part : s.split(";")) {
         int eq = part.indexOf('=');
         if (eq > 0) {
            map.put(part.substring(0, eq), part.substring(eq + 1));
         }
      }

      return map;
   }
}
