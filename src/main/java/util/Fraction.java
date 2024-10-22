package util;

import lombok.Getter;

@Getter
public class Fraction {
    private long numerator;
    private long denominator;

    public Fraction(long number) {
        this(number, 1);
    }

    public Fraction(long numerator, long denominator) {
        if (denominator == 0) throw new IllegalArgumentException();

        if (denominator < 0) numerator = -numerator;

        this.numerator = numerator;
        this.denominator = Math.abs(denominator);

        normalize();
    }

    public boolean isNegative() {
        return numerator < 0;
    }

    private void normalize() {
        long gcd = Utils.gcd(numerator, denominator);

        numerator /= gcd;
        denominator /= gcd;
    }

    public void add(Fraction fraction) {
        long gcd = Utils.gcd(denominator, fraction.denominator);
        long n = fraction.denominator / gcd;
        long m = denominator / gcd;

        numerator = (numerator * n) + (fraction.numerator * m);
        denominator = gcd * n * m;

        normalize();
    }

    public void multiply(Fraction fraction) {
        // 만약 로직에서 오버플로우 발생시 모듈러 연산으로 수정해야함.
        numerator *= fraction.numerator;
        denominator *= fraction.denominator;

        normalize();
    }
}
