import util.Fraction;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * test를 위한 임시 파일, src/test/java 생성이 안되는 문제가 있어 문제 찾아 고칠 동안 사용.
 */
public class Test {
    public static void main(String[] args) {
        addInteger();
        addFraction();
        create();

        System.out.println("done");
    }

    static void addInteger() {
        Fraction f1 = new Fraction(-38);
        Fraction f2 = new Fraction(30, 2);

        assertThat(f1.isNegative()).isEqualTo(true);
        assertThat(f2.getDenominator()).isEqualTo(1);

        f1.add(f2);
        assertThat(f1.getNumerator()).isEqualTo(-38 + 15);
        assertThat(f1.getDenominator()).isEqualTo(1);
    }

    static void addFraction() {
        Fraction f1 = new Fraction(7, 26);
        Fraction f2 = new Fraction(2, -3);

        f1.add(f2);
        assertThat(f1.getNumerator()).isEqualTo(21 - 52);
        assertThat(f1.getDenominator()).isEqualTo(26 * 3);
        assertThat(f1.isNegative()).isEqualTo(true);
    }

    static void create() {
        assertThatThrownBy(() -> new Fraction(3, 0)); // 분모로 0이 들어오는 경우 test.
        Fraction f1 = new Fraction(-7, -26);
        Fraction f2 = new Fraction(7, -74);

        assertThat(f1.isNegative()).isEqualTo(false);
        assertThat(f2.isNegative()).isEqualTo(true);

        assertThat(f1.getNumerator() > 0).isEqualTo(true);
        assertThat(f2.getDenominator() > 0).isEqualTo(true);
    }
}
