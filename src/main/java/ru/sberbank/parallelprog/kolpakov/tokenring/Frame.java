package ru.sberbank.parallelprog.kolpakov.tokenring;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;


public class Frame {
    private static final AtomicInteger idGenerator = new AtomicInteger(0);

    private final int id;
    private final MacAddress destinationAddress;
    private final MacAddress sourceAddress;
    private final byte[] data;
    private final int fcs;
    private final long creationNanoTime;
    private final int distanceBetweenAddresses;

    public Frame(MacAddress destinationAddress, MacAddress sourceAddress, byte[] data, int distanceBetweenAddresses) {
        this.distanceBetweenAddresses = distanceBetweenAddresses;
        this.id = idGenerator.getAndIncrement();
        this.destinationAddress = destinationAddress;
        this.sourceAddress = sourceAddress;
        this.data = data;
        this.fcs = this.hashCode();
        this.creationNanoTime = System.nanoTime();
    }

    public long getCreationNanoTime() {
        return creationNanoTime;
    }

    public MacAddress getDestinationAddress() {
        return destinationAddress;
    }

    public MacAddress getSourceAddress() {
        return sourceAddress;
    }

    public byte[] getData() {
        return data;
    }

    public boolean isValid() {
        return this.fcs == this.hashCode();
    }

    public int getDistanceBetweenAddresses() {
        return distanceBetweenAddresses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Frame frame = (Frame) o;

        return id == frame.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return "Frame{" +
                "id=" + id +
                ", destinationAddress=" + destinationAddress +
                ", sourceAddress=" + sourceAddress +
                ", data=" + Arrays.toString(data) +
                ", creationNanoTime=" + creationNanoTime +
                '}';
    }
}
