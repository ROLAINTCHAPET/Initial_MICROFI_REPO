package com.microfi.notifications.service;

/** Spells out an XAF amount for the receipt template's "(Twenty-Five Thousand CFA Francs)" line. */
final class NumberToWordsConverter {

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    /** French ones/teens 0-19 — reused directly as the 10-19 "teens" set too (onze..dix-neuf), since French has no separate teens vocabulary the way English does. */
    private static final String[] ONES_FR = {
            "", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf", "dix",
            "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf"
    };
    private static final String[] TENS_FR = {
            "", "", "vingt", "trente", "quarante", "cinquante", "soixante"
    };

    private NumberToWordsConverter() {
    }

    static String toWords(long amount) {
        if (amount == 0) {
            return "Zero";
        }
        StringBuilder words = new StringBuilder();
        long remaining = amount;
        for (long unit : new long[]{1_000_000_000L, 1_000_000L, 1_000L}) {
            if (remaining >= unit) {
                words.append(threeDigitsToWords(remaining / unit)).append(" ").append(scaleName(unit)).append(" ");
                remaining %= unit;
            }
        }
        if (remaining > 0) {
            words.append(threeDigitsToWords(remaining));
        }
        return words.toString().trim();
    }

    private static String scaleName(long unit) {
        if (unit == 1_000_000_000L) return "Billion";
        if (unit == 1_000_000L) return "Million";
        return "Thousand";
    }

    private static String threeDigitsToWords(long n) {
        StringBuilder sb = new StringBuilder();
        if (n >= 100) {
            sb.append(ONES[(int) (n / 100)]).append(" Hundred ");
            n %= 100;
        }
        if (n >= 20) {
            sb.append(TENS[(int) (n / 10)]);
            if (n % 10 > 0) {
                sb.append("-").append(ONES[(int) (n % 10)]);
            }
        } else if (n > 0) {
            sb.append(ONES[(int) n]);
        }
        return sb.toString().trim();
    }

    /**
     * French cardinal numbers, traditional (non-1990-reform) spelling — the style used throughout
     * Francophone Africa's official/financial documents: "vingt et un" (space, not hyphenated
     * "vingt-et-un"), "soixante-dix"/"quatre-vingts"/"quatre-vingt-dix" (the base-20 forms for
     * 70/80/90, not the Belgian/Swiss "septante/huitante/nonante"), "mille" invariable (never "un
     * mille", never pluralizes), "cent"/"million"/"milliard" pluralize with -s only when an exact
     * multiple with nothing following.
     */
    static String toWordsFrench(long amount) {
        if (amount == 0) {
            return "zéro";
        }
        StringBuilder words = new StringBuilder();
        long remaining = amount;

        if (remaining >= 1_000_000_000L) {
            long count = remaining / 1_000_000_000L;
            remaining %= 1_000_000_000L;
            words.append(threeDigitsToWordsFrench(count)).append(count > 1 ? " milliards " : " milliard ");
        }
        if (remaining >= 1_000_000L) {
            long count = remaining / 1_000_000L;
            remaining %= 1_000_000L;
            words.append(threeDigitsToWordsFrench(count)).append(count > 1 ? " millions " : " million ");
        }
        if (remaining >= 1_000L) {
            long count = remaining / 1_000L;
            remaining %= 1_000L;
            // "mille" is invariable: never "un mille", never "milles" — omit the leading "un".
            words.append(count == 1 ? "mille " : threeDigitsToWordsFrench(count) + " mille ");
        }
        if (remaining > 0) {
            words.append(threeDigitsToWordsFrench(remaining));
        }
        return words.toString().trim();
    }

    private static String threeDigitsToWordsFrench(long n) {
        StringBuilder sb = new StringBuilder();
        if (n >= 100) {
            long hundreds = n / 100;
            long rest = n % 100;
            if (hundreds > 1) {
                sb.append(ONES_FR[(int) hundreds]).append(" ");
            }
            // "cent" only pluralizes when it's an exact multiple of 100 with nothing after it.
            sb.append("cent").append(hundreds > 1 && rest == 0 ? "s" : "");
            n = rest;
        }
        if (n > 0) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(twoDigitsToWordsFrench((int) n));
        }
        return sb.toString().trim();
    }

    private static String twoDigitsToWordsFrench(int n) {
        if (n < 20) {
            return ONES_FR[n];
        }
        if (n < 70) {
            int tens = n / 10;
            int ones = n % 10;
            String tensWord = TENS_FR[tens];
            if (ones == 0) return tensWord;
            if (ones == 1) return tensWord + " et un";
            return tensWord + "-" + ONES_FR[ones];
        }
        if (n < 80) {
            // 70-79 = "soixante" + (10-19), e.g. 70 -> soixante-dix, 72 -> soixante-douze.
            int teen = n - 60;
            if (teen == 11) return "soixante et onze";
            return "soixante-" + ONES_FR[teen];
        }
        // 80-99 = "quatre-vingt(s)" + (0-19) — the trailing "s" on "vingts" only appears at
        // exactly 80 (nothing follows); 81-99 never use "et" (unlike the 20-69 range).
        int rest = n - 80;
        if (rest == 0) return "quatre-vingts";
        return "quatre-vingt-" + ONES_FR[rest];
    }
}
