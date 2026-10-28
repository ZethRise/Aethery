package studio.cluvex.aethery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountryCodeTest {
    @Test fun aTwoLetterCodeIsUpperCased() {
        assertEquals("DE", CountryCode.of("de"))
        assertEquals("PL", CountryCode.of(" PL "))
    }

    @Test fun cloudflaresCodesForTorAndUnknownAreNotCountries() {
        assertNull(CountryCode.of("T1"))
        assertNull(CountryCode.of("XX"))
        assertNull(CountryCode.of("ZZ"))
        assertNull(CountryCode.of(""))
        assertNull(CountryCode.of(null))
        assertNull(CountryCode.of("DEU"))
    }

    @Test fun aGeolocationAnswerGivesItsCountry() {
        assertEquals("DE", CountryCode.fromBody("""{"ip":"185.220.101.15","country":"DE"}"""))
        assertEquals("NL", CountryCode.fromBody("""{"ip":"1.2.3.4","success":true,"country_code":"NL","country":"Netherlands"}"""))
        assertEquals("FR", CountryCode.fromBody("fr\n"))
    }

    @Test fun anAnswerWithoutACountryGivesNone() {
        assertNull(CountryCode.fromBody("""{"error":"rate limited"}"""))
        assertNull(CountryCode.fromBody("<html>captcha</html>"))
        assertNull(CountryCode.fromBody(""))
    }
}
