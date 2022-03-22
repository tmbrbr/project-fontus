package com.sap.fontus.gdpr.metadata.simple;

import com.sap.fontus.gdpr.metadata.ExpiryDate;

import java.time.Instant;
import java.util.Objects;

public class SimpleExpiryDate implements ExpiryDate {

    private Instant expiry;

    public SimpleExpiryDate() {
        this.expiry = null;
    }

    public SimpleExpiryDate(Instant expiry) {
        this.expiry = expiry;
    }

    @Override
    public Instant getDate() {
        return expiry;
    }

    @Override
    public boolean hasExpiry() {
        return this.expiry != null;
    }

    @Override
    public int compareTo(ExpiryDate o) {
        // Instances are equal (also includes null pointers)
        if (this.getDate() == o.getDate()) {
            return 0;
        }
        if (this.hasExpiry() && o.hasExpiry()) {
            return expiry.compareTo(o.getDate());
        } else if (this.hasExpiry()) {
            return -1;
        } else {
            return 1;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleExpiryDate that = (SimpleExpiryDate) o;
        return Objects.equals(expiry, that.expiry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expiry);
    }

    @Override
    public String toString() {
        return "SimpleExpiryDate{" +
                "expiry=" + expiry +
                '}';
    }

}
