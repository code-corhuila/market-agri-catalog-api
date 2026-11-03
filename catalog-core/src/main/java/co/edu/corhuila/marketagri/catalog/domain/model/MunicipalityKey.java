package co.edu.corhuila.marketagri.catalog.domain.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The municipality as the filter of E-10 compares it (D-16): lower case, without accents, words of
 * {@code a-z} and {@code 0-9} with one separator between them — {@code "San Agustín"} is
 * {@code "san agustin"}. The API writes it because the database has no {@code unaccent}; the
 * database only refuses a key that is not normalized ({@code chk_product_municipality_key}).
 */
public record MunicipalityKey(String value) {

    private static final Pattern VALID = Pattern.compile("[a-z0-9]+([ .'-][a-z0-9]+)*");
    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Pattern ONE_SEPARATOR = Pattern.compile("[.'-]");

    public MunicipalityKey {
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("not a normalized municipality key: " + value);
        }
    }

    /**
     * Keeps the words and one separator between them: a gap of only {@code .}, {@code '} or
     * {@code -} keeps that character; any other gap (spaces, commas, parentheses…) becomes one space.
     */
    public static MunicipalityKey of(String municipality) {
        String plain = ACCENTS.matcher(Normalizer.normalize(municipality, Normalizer.Form.NFD))
                .replaceAll("").replace('’', '\'').toLowerCase(Locale.ROOT);
        StringBuilder key = new StringBuilder();
        Matcher word = WORD.matcher(plain);
        int previousEnd = -1;
        while (word.find()) {
            if (previousEnd >= 0) {
                String gap = plain.substring(previousEnd, word.start());
                key.append(ONE_SEPARATOR.matcher(gap).matches() ? gap : " ");
            }
            key.append(word.group());
            previousEnd = word.end();
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException("municipality without letters or digits: " + municipality);
        }
        return new MunicipalityKey(key.toString());
    }
}
