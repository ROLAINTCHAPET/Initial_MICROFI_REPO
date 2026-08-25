package com.microfi.notifications.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NumberToWordsConverterTest {

    @Test
    void englishZero() {
        assertThat(NumberToWordsConverter.toWords(0)).isEqualTo("Zero");
    }

    @ParameterizedTest
    @CsvSource({
            "0, zéro",
            "1, un",
            "5, cinq",
            "10, dix",
            "16, seize",
            "17, dix-sept",
            "19, dix-neuf",
            "20, vingt",
            "21, vingt et un",
            "22, vingt-deux",
            "29, vingt-neuf",
            "30, trente",
            "31, trente et un",
            "60, soixante",
            "61, soixante et un",
            "69, soixante-neuf",
            "70, soixante-dix",
            "71, soixante et onze",
            "72, soixante-douze",
            "79, soixante-dix-neuf",
            "80, quatre-vingts",
            "81, quatre-vingt-un",
            "89, quatre-vingt-neuf",
            "90, quatre-vingt-dix",
            "91, quatre-vingt-onze",
            "99, quatre-vingt-dix-neuf",
            "100, cent",
            "101, cent un",
            "199, cent quatre-vingt-dix-neuf",
            "200, deux cents",
            "201, deux cent un",
            "999, neuf cent quatre-vingt-dix-neuf",
            "1000, mille",
            "1001, mille un",
            "1100, mille cent",
            "2000, deux mille",
            "21000, vingt et un mille",
            "100000, cent mille",
            "200000, deux cents mille",
            "999000, neuf cent quatre-vingt-dix-neuf mille",
            "1000000, un million",
            "2000000, deux millions",
            "1000000000, un milliard",
            "2000000000, deux milliards",
    })
    void frenchCardinal(long amount, String expected) {
        assertThat(NumberToWordsConverter.toWordsFrench(amount)).isEqualTo(expected);
    }

    @Test
    void frenchRealisticCollectionAmount() {
        // A typical field collection: 25 000 XAF.
        assertThat(NumberToWordsConverter.toWordsFrench(25_000)).isEqualTo("vingt-cinq mille");
        // 42 300 XAF (seen elsewhere in this session's test data).
        assertThat(NumberToWordsConverter.toWordsFrench(42_300)).isEqualTo("quarante-deux mille trois cents");
    }
}
