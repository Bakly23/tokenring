package ru.sberbank.parallelprog.kolpakov.tokenring;

import java.util.Arrays;
import java.util.stream.Collectors;

class MacAddress {
    private final byte[] address;

    MacAddress(byte[] address) {
        this.address = address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MacAddress that = (MacAddress) o;

        return Arrays.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(address);
    }

    @Override
    public String toString() {
        return Arrays.stream(Arrays.toString(address)
                .replaceAll("\\[", "").replaceAll("\\]", "")
                .split(", "))
                .map(Integer::parseInt)
                .map(i -> Integer.toString(i, 16))
                .collect(Collectors.joining("."));
    }
}
