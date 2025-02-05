package org.wanaku.server.quarkus;

import java.util.Random;

public final class Pagination {
    public static String nextPage() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            int charCode = random.nextInt(26);
            sb.append((char) ('a' + charCode));
        }
        return sb.toString();
    }
}

