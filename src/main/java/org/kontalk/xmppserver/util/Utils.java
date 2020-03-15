/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.xmppserver.util;


public class Utils {

    public static String generateRandomNumericString(int length) {
        StringBuilder buffer = new StringBuilder();
        final String characters = "1234567890";

        int charactersLength = characters.length();

        for (int i = 0; i < length; i++) {
            double index = Math.random() * charactersLength;
            buffer.append(characters.charAt((int) index));
        }
        return buffer.toString();
    }

    // directly from OpenJDK 8 DatatypeConverterImpl
    public static byte[] parseHexBinary(String s) {
        int len = s.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("hexBinary needs to be even-length: " + s);
        }
        else {
            byte[] out = new byte[len / 2];

            for(int i = 0; i < len; i += 2) {
                int h = hexToBin(s.charAt(i));
                int l = hexToBin(s.charAt(i + 1));
                if (h == -1 || l == -1) {
                    throw new IllegalArgumentException("contains illegal character for hexBinary: " + s);
                }

                out[i / 2] = (byte)(h * 16 + l);
            }

            return out;
        }
    }

    // directly from OpenJDK 8 DatatypeConverterImpl
    private static int hexToBin(char ch) {
        if ('0' <= ch && ch <= '9') {
            return ch - 48;
        }
        else if ('A' <= ch && ch <= 'F') {
            return ch - 65 + 10;
        }
        else {
            return 'a' <= ch && ch <= 'f' ? ch - 97 + 10 : -1;
        }
    }

}
