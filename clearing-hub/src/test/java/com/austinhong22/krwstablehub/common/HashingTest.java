package com.austinhong22.krwstablehub.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HashingTest {

    @Test
    void shouldReturnSha256Hex() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Hashing.sha256Hex("abc")
        );
    }

    @Test
    void shouldRejectNullInput() {
        assertThrows(NullPointerException.class, () -> Hashing.sha256Hex(null));
    }
}
