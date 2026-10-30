package mx.artemisa.artemisaapi.packageplan;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OvertimeCalculatorTest {
    private static final BigDecimal RATE = new BigDecimal("100.00");
    @Test void toleranceIsNotBillableAndMinutePastItChargesFullHour() {
        assertEquals(BigDecimal.ZERO.setScale(2), OvertimeCalculator.calculate(495,480,15,RATE).extraCharge());
        assertEquals(new BigDecimal("100.00"), OvertimeCalculator.calculate(496,480,15,RATE).extraCharge());
    }
    @Test void eachAdditionalStartedBillableHourIsChargedInFull() {
        assertEquals(new BigDecimal("100.00"), OvertimeCalculator.calculate(555,480,15,RATE).extraCharge());
        assertEquals(new BigDecimal("200.00"), OvertimeCalculator.calculate(556,480,15,RATE).extraCharge());
    }
}
