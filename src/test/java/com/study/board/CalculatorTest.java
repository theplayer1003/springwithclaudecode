package com.study.board;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CalculatorTest {

    @Test
    void division_DividedZero_ThrowsArithmeticException() {
        assertThatThrownBy(() -> division(10, 0))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void plus_TwoPlusThree_ReturnFive() {
        assertThat(plus(2, 3)).isEqualTo(5);
    }

    @Test
    void minus_TenMinusFour_ReturnSix() {
        assertThat(minus(10, 4)).isEqualTo(6);
    }

    private static int division(int dividend, int divisor) {
        return dividend / divisor;
    }

    private static int plus(int a, int b) {
        return a + b;
    }

    private static int minus(int a, int b) {
        return a - b;
    }
}
