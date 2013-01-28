package org.unicode.draft;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.SupplementalDataInfo.OfficialStatus;
import org.unicode.cldr.util.SupplementalDataInfo.PopulationData;

import com.ibm.icu.impl.Row;
import com.ibm.icu.impl.Row.R2;
import com.ibm.icu.impl.Row.R3;
import com.ibm.icu.text.NumberFormat;

public class ListTopLanguages {
	static SupplementalDataInfo sdata = SupplementalDataInfo.getInstance(CldrUtility.SUPPLEMENTAL_DIRECTORY);
	static Map<String, Map<String, R2<List<String>, String>>> localeAliasInfo = sdata.getLocaleAliasInfo();
	static Map<String, String> likelySubtags = sdata.getLikelySubtags();
	static Factory cldrFactory = Factory.make(CldrUtility.MAIN_DIRECTORY, ".*");
	static CLDRFile english = cldrFactory.make("en", true);

	public static void main(String[] args) {
		final Counter<R3<OfficialStatus, String, String>> gathered = new Counter<R3<OfficialStatus, String, String>>();

		for (final String territory : sdata.getTerritoriesWithPopulationData()) {
			for (final String language : sdata.getLanguagesForTerritoryWithPopulationData(territory)) {
				if (language.equals("und")) {
					continue;
				}
				final PopulationData data = sdata.getLanguageAndTerritoryPopulationData(language, territory);
				final long pop = (long) data.getPopulation();
				final OfficialStatus status = data.getOfficialStatus();
				final String locale = language + "_" + territory;

				String googled = (language + "_" + territory).replace("_", "-");
				String name = english.getName(locale);
				final String lname = english.getName(language);

				final Set<String> alternates = getAlternates(territory, language, locale);
				for (final String minFrom : alternates) {
					if (minFrom != null && !locale.equals(minFrom)) {
						System.out.println(locale + "\t=>\t" + minFrom);
						googled = googled + "/" + minFrom;
						name = name + "/" + english.getName(minFrom);
					}
				}

				gathered.add(Row.of(status, googled, name), pop);
				gathered.add(Row.of(OfficialStatus.unknown, language, lname), pop);
			}
		}
		int rank = 0;
		final NumberFormat format = NumberFormat.getInstance();
		format.setGroupingUsed(true);
		for (final R3<OfficialStatus, String, String> row : gathered.getKeysetSortedByCount(false)) {
			final long pop = gathered.get(row);
			final OfficialStatus status = row.get0();
			System.out.println(++rank + "\t" + format.format(pop) + "\t" + row.get1() + "\t" + row.get2() + (status == OfficialStatus.unknown ? "" : "\t" + status));
		}
	}

	private static Set<String> getAlternates(String language, String script, String territory) {
		final Set<String> languages = new TreeSet<String>();
		final Set<String> territories = new TreeSet<String>();
		for (final String tag : localeAliasInfo.keySet()) {
			final Map<String, R2<List<String>, String>> replacements = localeAliasInfo.get(tag);
			if (tag.equals("language")) {
				addAlternates(language, replacements, languages);
			} else if (tag.equals("territory")) {
				addAlternates(territory, replacements, territories);
			} else {
				System.out.println("Unknown tag: " + tag);
			}
		}
		final Set<R3<String,String,String>> alternates = new TreeSet<R3<String,String,String>>();
		for (final String language2 : languages) {
			for (final String territory2 : territories) {
				final R3<String, String, String> row = Row.of(language2, script, territory2);
				alternates.add(row);
				System.out.println(row);
			}
		}
		// we now have a set of rows that we can try maximizing
		final Set<String> result = new TreeSet<String>();
		//    String minFrom = GenerateLikelySubtagTests.minimize(locale, likelySubtags, true);
		//    if (minFrom != null) {
		//      if (language.equals(minFrom)) {
		//        minFrom = minFrom + "_" + territory;
		//      }
		//      result.add(minFrom);
		//    }
		return result;
	}

	private static void addAlternates(String language, Map<String, R2<List<String>, String>> replacements, Set<String> languages) {
		languages.add(language);
		for (final String source : replacements.keySet()) {
			final List<String> set = replacements.get(source).get0();
			if (set.contains(language)) {
				languages.add(source);
			}
		}
	}
}