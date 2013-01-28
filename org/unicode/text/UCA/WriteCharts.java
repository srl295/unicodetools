/**
 *******************************************************************************
 * Copyright (C) 1996-2001, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 *
 * $Source: /home/cvsroot/unicodetools/org/unicode/text/UCA/WriteCharts.java,v $
 *
 *******************************************************************************
 */

package org.unicode.text.UCA;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.text.UCD.Default;
import org.unicode.text.UCD.ToolUnicodePropertySource;
import org.unicode.text.UCD.UCD;
import org.unicode.text.UCD.UCD_Names;
import org.unicode.text.UCD.UCD_Types;
import org.unicode.text.utility.Pair;
import org.unicode.text.utility.Utility;

import com.ibm.icu.dev.util.UnicodeProperty;
import com.ibm.icu.text.Transliterator;
import com.ibm.icu.text.UTF16;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;

public class WriteCharts implements UCD_Types {

	public static String WORKING_DIR = UCD_Types.SRC_DIR + "UCA/";
	static boolean HACK_KANA = false;

	static public void special() {

		for (int i = 0xE000; i < 0x10000; ++i) {
			if (!Default.ucd().isRepresented(i)) {
				continue;
			}
			if (!Default.nfkc().isNormalized(i)) {
				continue;
			}
			System.out.println(Default.ucd().getCodeAndName(i));
		}
	}

	static public void collationChart(UCA uca) throws IOException {
		Default.setUCD(uca.getUCDVersion());
		HACK_KANA = true;

		uca.setAlternate(UCA_Types.NON_IGNORABLE);

		//Normalizer nfd = new Normalizer(Normalizer.NFD);
		//Normalizer nfc = new Normalizer(Normalizer.NFC);

		final UCA.UCAContents cc = uca.getContents(null); // nfd instead of null if skipping decomps
		cc.setDoEnableSamples(true);

		final Set set = new TreeSet();

		while (true) {
			final String x = cc.next();
			if (x == null) {
				break;
			}
			if (x.equals("\u2F00")) {
				System.out.println("debug");
			}

			set.add(new Pair(uca.getSortKey(x), x));
		}

		PrintWriter output = null;

		final Iterator it = set.iterator();

		byte oldScript = -127;

		final int[] scriptCount = new int[255];

		final int counter = 0;

		String lastSortKey = "\u0000";

		final int high = uca.getSortKey("a").charAt(0);
		final int variable = UCA.getPrimary(uca.getVariableHighCE());

		int columnCount = 0;

		final String[] replacement = new String[] {"%%%", "Collation Charts", "$initialPage$", "chart_Latin.html"};
		final String folder = "charts/collation/";

		Utility.copyTextFile(WORKING_DIR + "index.html", Utility.UTF8, folder + "index.html", replacement);
		Utility.copyTextFile(WORKING_DIR + "charts.css", Utility.LATIN1, folder + "charts.css");
		Utility.copyTextFile(WORKING_DIR + "help.html", Utility.UTF8, folder + "help.html");

		indexFile = Utility.openPrintWriter(folder + "index_list.html", Utility.UTF8_WINDOWS);
		Utility.appendFile(WORKING_DIR + "index_header.html", Utility.UTF8, indexFile, replacement);

		/*
        indexFile.println("<html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>");
        indexFile.println("<title>UCA Default Collation Table</title>");
        indexFile.println("<base target='main'>");
        indexFile.println("<style type='text/css'><!-- p { font-size: 90% } --></style>");
        indexFile.println("</head><body><h2 align='center'>UCA Default Collation Table</h2>");
        indexFile.println("<p align='center'><a href = 'help.html'>Help</a>");
		 */

		final int LEAST_PUNCT_PRIMARY = UCA.getPrimary(uca.getCEList("\u203E", true).at(0));
		final int LEAST_SYMBOL_PRIMARY = UCA.getPrimary(uca.getCEList("`", true).at(0));
		final int LEAST_CURRENCY_PRIMARY = UCA.getPrimary(uca.getCEList("¤", true).at(0));
		final int LEAST_DIGIT_PRIMARY = UCA.getPrimary(uca.getCEList("0", true).at(0));

		int lastCp = -1;
		while (it.hasNext()) {
			Utility.dot(counter);

			final Pair p = (Pair) it.next();
			final String sortKey = (String) p.first;
			final String s = (String) p.second;

			final int cp = UTF16.charAt(s,0);

			byte script = Default.ucd().getScript(cp);
			if (cp == 0x1DBF)
			{
				script = UCD_Types.GREEK_SCRIPT; // 4.1.0 hack
			}

			// get first non-zero primary
			final int currentPrimary = getFirstPrimary(sortKey);
			final int primary = currentPrimary >>> 16;

			if (sortKey.length() < 4) {
				script = NULL_ORDER;
			} else if (primary == 0) {
				script = IGNORABLE_ORDER;
			} else if (primary < LEAST_PUNCT_PRIMARY) {
				script = SPACE;
			} else if (primary < LEAST_SYMBOL_PRIMARY) {
				script = PUNCT;
			} else if (primary < LEAST_CURRENCY_PRIMARY) {
				script = SYMBOL;
			} else if (primary < LEAST_DIGIT_PRIMARY) {
				script = CURRENCY;
			} else if (primary < high) {
				script = DIGIT;
			} else if (UCA.isImplicitLeadPrimary(primary)) {
				if (primary < UCA_Types.UNSUPPORTED_CJK_AB_BASE) {
					script = CJK;
				} else if (primary < UCA_Types.UNSUPPORTED_OTHER_BASE) {
					script = CJK_AB;
				} else {
					script = UNSUPPORTED;
				}
			}

			if (script == KATAKANA_SCRIPT) {
				script = HIRAGANA_SCRIPT;
			} else if (script == Meroitic_Cursive) {
				script = Meroitic_Hieroglyphs;
			} else if ((script == INHERITED_SCRIPT || script == COMMON_SCRIPT) && oldScript >= 0) {
				script = oldScript;
			}

			final int veryOldScript = oldScript;
			if (script != oldScript
					// && (script != COMMON_SCRIPT && script != INHERITED_SCRIPT)
					) {
				closeFile(output);
				output = null;
				oldScript = script;
			}

			if (output == null) {
				++scriptCount[script-NULL_ORDER];
				if (scriptCount[script-NULL_ORDER] > 1) {
					System.out.println("\t\tFAIL: " + scriptCount[script-NULL_ORDER] + ", " +
							getChunkName(script, LONG) + ", " + Default.ucd().getCodeAndName(s)
							+ " - last char: "
							+ getChunkName(veryOldScript, LONG) + ", " + Default.ucd().getCodeAndName(lastCp));
				}
				output = openFile(scriptCount[script-NULL_ORDER], folder, script);
			}

			final boolean firstPrimaryEquals = currentPrimary == getFirstPrimary(lastSortKey);

			int strength = UCA.strengthDifference(sortKey, lastSortKey);
			if (strength < 0) {
				strength = -strength;
			}
			lastSortKey = sortKey;

			// find out if this is an expansion: more than one primary weight

			int primaryCount = 0;
			for (int i = 0; i < sortKey.length(); ++i) {
				final char w = sortKey.charAt(i);
				if (w == 0) {
					break;
				}
				if (UCA.isImplicitLeadPrimary(w)) {
					++i; // skip next
				}
				++ primaryCount;
			}

			String breaker = "";
			if (columnCount > 10 || !firstPrimaryEquals) {
				columnCount = 0;
				if (!firstPrimaryEquals || script == UNSUPPORTED) {
					breaker = "</tr><tr>";
				} else {
					breaker = "</tr><tr><td></td>"; // indent 1 cell
					++columnCount;
				}
			}

			final String classname = primaryCount > 1 ? XCLASSNAME[strength] : CLASSNAME[strength];

			final String outline = showCell2(sortKey, s, script, classname);

			output.println(breaker + outline);
			++columnCount;
			lastCp = cp;
		}

		closeFile(output);
		closeIndexFile(indexFile, "<br>UCA: " + uca.getDataVersion(), COLLATION, true);
	}

	private static String showCell2(
			String sortKey,
			String s,
			byte script,
			String classname) {
		final String name = Default.ucd().getName(s);


		if (s.equals("\u1eaf")) {
			System.out.println("debug");
		}

		String comp = Default.nfc().normalize(s);
		final int cat = Default.ucd().getCategory(UTF16.charAt(comp,0));
		if (cat == Mn || cat == Mc || cat == Me) {
			comp = '\u25CC' + comp;
			if (s.equals("\u0300")) {
				System.out.println(Default.ucd().getCodeAndName(comp));
			}
		}
		// TODO: merge with showCell

		final String outline = classname
				+ " title='"
				+ (script != UNSUPPORTED
				? Utility.quoteXML(name, true) + ": "
						: "")
						+ UCA.toString(sortKey) + "'>"
						+ Utility.quoteXML(comp, true)
						+ "<br><tt>"
						+ Utility.hex(s)
						//+ "<br>" + script
						+ "</tt></td>"
						+ (script == UNSUPPORTED
						? "<td class='name'><tt>" + Utility.quoteXML(name, true) + "</td>"
								: "")
								;
		return outline;
	}

	static public void normalizationChart() throws IOException {
		HACK_KANA = false;

		final Set set = new TreeSet();

		for (int i = 0; i <= 0x10FFFF; ++i) {
			if (!Default.ucd().isRepresented(i)) {
				if (i < 0xAC00) {
					continue;
				}
				if (i > 0xD7A3) {
					continue;
				}
				if (i > 0xACFF && i < 0xD700) {
					continue;
				}
			}
			final byte cat = Default.ucd().getCategory(i);
			if (cat == Cs || cat == Co) {
				continue;
			}

			if (Default.nfkd().isNormalized(i)) {
				continue;
			}
			final String decomp = Default.nfkd().normalize(i);

			final byte script = getBestScript(decomp);

			set.add(new Pair(new Integer(script == COMMON_SCRIPT ? cat + CAT_OFFSET : script),
					new Pair(Default.ucd().getCase(decomp, FULL, FOLD),
							new Integer(i))));
		}

		PrintWriter output = null;

		final Iterator it = set.iterator();

		int oldScript = -127;

		final int counter = 0;

		final String[] replacement = new String[] {"%%%", "Normalization Charts", "$initialPage$", "chart_Latin.html"};
		final String folder = "charts/normalization/";

		//System.out.println("File: " + new File(".").getCanonicalPath());

		Utility.copyTextFile("org/unicode/text/UCA/index.html", Utility.UTF8, folder + "index.html", replacement);
		Utility.copyTextFile("org/unicode/text/UCA/charts.css", Utility.LATIN1, folder + "charts.css");
		Utility.copyTextFile("org/unicode/text/UCA/norm_help.html", Utility.UTF8, folder + "help.html");

		indexFile = Utility.openPrintWriter(folder + "index_list.html", Utility.UTF8_WINDOWS);
		Utility.appendFile(WORKING_DIR + "index_header.html", Utility.UTF8, indexFile, replacement);

		/*
        indexFile.println("<html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>");
        indexFile.println("<title>UCA Default Collation Table</title>");
        indexFile.println("<base target='main'>");
        indexFile.println("<style type='text/css'><!-- p { font-size: 90% } --></style>");
        indexFile.println("</head><body><h2 align='center'>UCA Default Collation Table</h2>");
        indexFile.println("<p align='center'><a href = 'help.html'>Help</a>");
		 */

		while (it.hasNext()) {
			Utility.dot(counter);

			final Pair p = (Pair) it.next();
			final int script = ((Integer) p.first).intValue();
			final int cp = ((Integer)((Pair) p.second).second).intValue();

			if (script != oldScript
					// && (script != COMMON_SCRIPT && script != INHERITED_SCRIPT)
					) {
				closeFile(output);
				output = null;
				oldScript = script;
			}

			if (output == null) {
				output = openFile(0, folder, script);
				output.println("<tr><td class='z'>Code</td><td class='z'>C</td><td class='z'>D</td><td class='z'>KC</td><td class='z'>KD</td></tr>");

			}

			output.println("<tr>");

			String prefix;
			final String code = UTF16.valueOf(cp);
			final String c = Default.nfc().normalize(cp);
			final String d = Default.nfd().normalize(cp);
			final String kc = Default.nfkc().normalize(cp);
			final String kd = Default.nfkd().normalize(cp);

			showCell(output, code, "<td class='z' ", "", false);

			prefix = c.equals(code) ? "<td class='g' " : "<td class='n' ";
			showCell(output, c, prefix, "", c.equals(code));

			prefix = d.equals(c) ? "<td class='g' " : "<td class='n' ";
			showCell(output, d, prefix, "", d.equals(c));

			prefix = kc.equals(c) ? "<td class='g' " : "<td class='n' ";
			showCell(output, kc, prefix, "", kc.equals(c));

			prefix = (kd.equals(d) || kd.equals(kc)) ? "<td class='g' " : "<td class='n' ";
			showCell(output, kd, prefix, "", (kd.equals(d) || kd.equals(kc)));

			output.println("</tr>");

		}

		closeFile(output);
		closeIndexFile(indexFile, "", NORMALIZATION, true);
	}

	static public void caseChart() throws IOException {
		HACK_KANA = false;

		final Set set = new TreeSet();

		for (int i = 0; i <= 0x10FFFF; ++i) {
			if (!Default.ucd().isRepresented(i)) {
				continue;
			}
			final byte cat = Default.ucd().getCategory(i);
			if (cat == Cs || cat == Co) {
				continue;
			}

			final String code = UTF16.valueOf(i);
			final String lower = Default.ucd().getCase(i, FULL, LOWER);
			final String title = Default.ucd().getCase(i, FULL, TITLE);
			final String upper = Default.ucd().getCase(i, FULL, UPPER);
			final String fold = Default.ucd().getCase(i, FULL, FOLD);

			final String decomp = Default.nfkd().normalize(i);
			int script = 0;
			if (lower.equals(code) && upper.equals(code) && fold.equals(code) && title.equals(code)) {
				if (!containsCase(decomp)) {
					continue;
				}
				script = NO_CASE_MAPPING;
			}

			if (script == 0) {
				script = getBestScript(decomp);
			}

			set.add(new Pair(new Integer(script == COMMON_SCRIPT ? cat + CAT_OFFSET : script),
					new Pair(Default.ucd().getCase(decomp, FULL, FOLD),
							new Integer(i))));
		}

		PrintWriter output = null;

		final Iterator it = set.iterator();

		int oldScript = -127;

		final int counter = 0;
		final String[] replacement = new String[] {"%%%", "Case Charts", "$initialPage$", "chart_Latin.html"};
		final String folder = "charts/case/";

		Utility.copyTextFile("org/unicode/text/UCA/index.html", Utility.UTF8, folder + "index.html", replacement);
		Utility.copyTextFile("org/unicode/text/UCA/charts.css", Utility.LATIN1, folder + "charts.css");
		Utility.copyTextFile("org/unicode/text/UCA/case_help.html", Utility.UTF8, folder + "help.html");

		indexFile = Utility.openPrintWriter(folder + "index_list.html", Utility.UTF8_WINDOWS);
		Utility.appendFile(WORKING_DIR + "index_header.html", Utility.UTF8, indexFile, replacement);

		/*
        indexFile.println("<html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>");
        indexFile.println("<title>UCA Default Collation Table</title>");
        indexFile.println("<base target='main'>");
        indexFile.println("<style type='text/css'><!-- p { font-size: 90% } --></style>");
        indexFile.println("</head><body><h2 align='center'>UCA Default Collation Table</h2>");
        indexFile.println("<p align='center'><a href = 'help.html'>Help</a>");
		 */

		int columnCount = 0;

		while (it.hasNext()) {
			Utility.dot(counter);

			final Pair p = (Pair) it.next();
			final int script = ((Integer) p.first).intValue();
			final int cp = ((Integer)((Pair) p.second).second).intValue();

			if (script != oldScript
					// && (script != COMMON_SCRIPT && script != INHERITED_SCRIPT)
					) {
				closeFile(output);
				output = null;
				oldScript = script;
			}

			if (output == null) {
				output = openFile(0, folder, script);
				if (script == NO_CASE_MAPPING) {
					output.println("<tr>");
				} else {
					output.println("<tr><td class='z'>Code</td><td class='z'>Lower</td><td class='z'>Title</td>"
							+"<td class='z'>Upper</td><td class='z'>Fold</td></tr>");
				}

			}

			if (script == NO_CASE_MAPPING) {
				if (columnCount > 10) {
					output.println("</tr><tr>");
					columnCount = 0;
				}
				showCell(output, UTF16.valueOf(cp), "<td ", "", false);
				++columnCount;
				continue;
			}

			output.println("<tr>");

			String prefix;
			final String code = UTF16.valueOf(cp);
			final String lower = Default.ucd().getCase(cp, FULL, LOWER);
			final String title = Default.ucd().getCase(cp, FULL, TITLE);
			final String upper = Default.ucd().getCase(cp, FULL, UPPER);
			final String fold = Default.ucd().getCase(cp, FULL, FOLD);

			showCell(output, code, "<td class='z' ", "", false);

			prefix = lower.equals(code) ? "<td class='g' " : "<td class='n' ";
			showCell(output, lower, prefix, "", lower.equals(code));

			prefix = title.equals(upper) ? "<td class='g' " : "<td class='n' ";
			showCell(output, title, prefix, "", title.equals(upper));

			prefix = upper.equals(code) ? "<td class='g' " : "<td class='n' ";
			showCell(output, upper, prefix, "", upper.equals(code));

			prefix = fold.equals(lower) ? "<td class='g' " : "<td class='n' ";
			showCell(output, fold, prefix, "", fold.equals(lower));

			output.println("</tr>");

		}

		closeFile(output);
		closeIndexFile(indexFile, "", CASE, true);
	}

	static public void scriptChart() throws IOException {
		HACK_KANA = false;

		final Set set = new TreeSet();
		final BitSet toReturn = new BitSet();

		for (int i = 0; i <= 0x10FFFF; ++i) {
			if (i == 0x0342) {
				System.out.println("?");
			}
			if (!Default.ucd().isRepresented(i)) {
				continue;
			}
			final byte cat = Default.ucd().getCategory(i);
			if (cat == Cs || cat == Co || cat == Cn) {
				continue;
			}

			final String code = UTF16.valueOf(i);

			final String decomp = Default.nfkd().normalize(i);
			getBestScript(i, decomp.equals(code) ? null : decomp, toReturn);
			for (int script = toReturn.nextSetBit(0); script >= 0; script = toReturn.nextSetBit(script+1)) {
				set.add(new Pair(script == COMMON_SCRIPT ? cat + CAT_OFFSET : script, new Pair(decomp, i)));
			}
		}

		PrintWriter output = null;

		final Iterator it = set.iterator();

		int oldScript = -127;

		final int counter = 0;
		final String[] replacement = new String[] {"%%%", "Script Charts", "$initialPage$", "chart_Latin.html"};
		final String folder = "charts//script/";

		Utility.copyTextFile("org/unicode/text/UCA/index.html", Utility.UTF8, folder + "index.html", replacement);
		Utility.copyTextFile("org/unicode/text/UCA/charts.css", Utility.LATIN1, folder + "charts.css");
		Utility.copyTextFile("org/unicode/text/UCA/script_help.html", Utility.UTF8, folder + "help.html");

		indexFile = Utility.openPrintWriter(folder + "index_list.html", Utility.UTF8_WINDOWS);
		Utility.appendFile(WORKING_DIR + "script_index_header.html", Utility.UTF8, indexFile, replacement);

		/*
			indexFile.println("<html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>");
			indexFile.println("<title>UCA Default Collation Table</title>");
			indexFile.println("<base target='main'>");
			indexFile.println("<style type='text/css'><!-- p { font-size: 90% } --></style>");
			indexFile.println("</head><body><h2 align='center'>UCA Default Collation Table</h2>");
			indexFile.println("<p align='center'><a href = 'help.html'>Help</a>");
		 */

		int columnCount = 0;

		while (it.hasNext()) {
			Utility.dot(counter);

			final Pair p = (Pair) it.next();
			final int script = ((Integer) p.first).intValue();
			final int cp = ((Integer)((Pair)p.second).second).intValue();

			if (script != oldScript
					// && (script != COMMON_SCRIPT && script != INHERITED_SCRIPT)
					) {
				closeFile(output);
				output = null;
				oldScript = script;
				columnCount = 0;
			}

			if (output == null) {
				output = openFile(0, folder, script);
			}

			if (columnCount > 10) {
				output.println("</tr><tr>");
				columnCount = 0;
			}
			showCell(output, UTF16.valueOf(cp), "<td ", "", false);
			++columnCount;
		}

		closeFile(output);
		closeIndexFile(indexFile, "", SCRIPT, true);
	}

	static public void addMapChar(Map m, Set stoplist, String key, String ch) {
		if (stoplist.contains(key)) {
			return;
		}
		for (int i = 0; i < key.length(); ++i) {
			final char c = key.charAt(i);
			if ('0' <= c && c <= '9') {
				return;
			}
		}
		Set result = (Set)m.get(key);
		if (result == null) {
			result = new TreeSet();
			m.put(key, result);
		}
		result.add(ch);
	}

	static public void indexChart() throws IOException {
		HACK_KANA = false;

		final Map map = new TreeMap();
		final Set stoplist = new TreeSet();

		final String[] stops = {"LETTER", "CHARACTER", "AND", "CAPITAL", "SMALL", "COMPATIBILITY", "WITH"};
		stoplist.addAll(Arrays.asList(stops));
		System.out.println("Stop-list: " + stoplist);

		for (int i = 0; i < LIMIT_SCRIPT; ++i) {
			stoplist.add(Default.ucd().getScriptID_fromIndex((byte)i));
		}
		System.out.println("Stop-list: " + stoplist);

		for (int i = 0; i <= 0x10FFFF; ++i) {
			if (!Default.ucd().isRepresented(i)) {
				continue;
			}
			if (!Default.ucd().isAssigned(i)) {
				continue;
			}
			if (0xAC00 <= i && i <= 0xD7A3) {
				continue;
			}
			if (Default.ucd().hasComputableName(i)) {
				continue;
			}

			final String s = Default.ucd().getName(i);
			if (s == null) {
				continue;
			}

			if (s.startsWith("<")) {
				System.out.println("Weird character at " + Default.ucd().getCodeAndName(i));
			}
			final String ch = UTF16.valueOf(i);
			int last = -1;
			int j;
			for (j = 0; j < s.length(); ++j) {
				final char c = s.charAt(j);
				if ('A' <= c && c <= 'Z' || '0' <= c && c <= '9') {
					if (last == -1) {
						last = j;
					}
				} else {
					if (last != -1) {
						final String word = s.substring(last, j);
						addMapChar(map, stoplist, word, ch);
						last = -1;
					}
				}
			}
			if (last != -1) {
				final String word = s.substring(last, j);
				addMapChar(map, stoplist, word, ch);
			}
		}

		PrintWriter output = null;

		final Iterator it = map.keySet().iterator();

		final int oldScript = -127;

		final int counter = 0;
		final String[] replacement = new String[] {"%%%", "Name Index Charts", "$initialPage$", "chart_X.html"};
		final String folder = "charts//name/";

		Utility.copyTextFile("org/unicode/text/UCA/index.html", Utility.UTF8, folder + "index.html", replacement);
		Utility.copyTextFile("org/unicode/text/UCA/charts.css", Utility.LATIN1, folder + "charts.css");
		Utility.copyTextFile("org/unicode/text/UCA/name_help.html", Utility.UTF8, folder + "help.html");

		indexFile = Utility.openPrintWriter(folder + "index_list.html", Utility.UTF8_WINDOWS);
		Utility.appendFile(WORKING_DIR + "index_header.html", Utility.UTF8, indexFile, replacement);

		int columnCount = 0;
		char lastInitial = 0;

		while (it.hasNext()) {
			Utility.dot(counter);

			final String key = (String) it.next();

			final Set chars = (Set) map.get(key);

			final char initial = key.charAt(0);

			if (initial != lastInitial) {
				closeFile(output);
				output = null;
				lastInitial = initial;
			}

			if (output == null) {
				output = openFile2(0, folder, String.valueOf(initial));
			}

			output.println("<tr><td class='h'>" + key + "</td>");
			columnCount = 1;

			final Iterator sublist = chars.iterator();
			while (sublist.hasNext()) {

				final String ch = (String) sublist.next();
				if (columnCount > 10) {
					output.println("</tr><tr><td></td>");
					columnCount = 1;
				}
				showCell(output, ch, "<td ", "", false);
				++columnCount;
				continue;
			}

			output.println("</tr>");

		}

		closeFile(output);
		closeIndexFile(indexFile, "", NAME, true);
	}

	static void showCell(PrintWriter output, String s,
			String prefix, String extra, boolean skipName) {
		if (s.equals("\u0300")) {
			System.out.println();
		}
		final String name = Default.ucd().getName(s);
		String comp = Default.nfc().normalize(s);
		final int cat = Default.ucd().getCategory(UTF16.charAt(comp,0));
		if (cat == Mn || cat == Mc || cat == Me) {
			comp = '\u25CC' + comp;
			if (s.equals("\u0300")) {
				System.out.println(Default.ucd().getCodeAndName(comp));
			}
		}

		final String outline = prefix
				+ (skipName ? "" : " title='" + Utility.quoteXML(name, true) + "'")
				+ extra + ">"
				+ Utility.quoteXML(comp, true)
				+ "<br><tt>"
				+ Utility.hex(s)
				//+ "<br>" + script
				+ "</tt></td>";

		output.println(outline);
	}

	static byte getBestScript(String s) {
		int cp;
		byte result = COMMON_SCRIPT;
		for (int i = 0; i < s.length(); i += UTF16.getCharCount(cp)) {
			cp = UTF16.charAt(s, i);
			result = Default.ucd().getScript(cp);
			if (result != COMMON_SCRIPT && result != INHERITED_SCRIPT) {
				return result;
			}
		}
		return COMMON_SCRIPT;
	}

	//static final IndexUnicodeProperties INDEX_UNICODE_PROPS = IndexUnicodeProperties.make(Default.ucd().getVersion());
	//static final UnicodeMap<String> SCRIPT_EXTENSIONS = INDEX_UNICODE_PROPS.load(UcdProperty.Script_Extensions);

	static BitSet getBestScript(int original, String transformed, BitSet toReturn) {
		toReturn.clear();
		addScript(original, toReturn);
		if (transformed != null) {
			int cp;
			for (int i = 0; i < transformed.length(); i += UTF16.getCharCount(cp)) {
				cp = UTF16.charAt(transformed, i);
				addScript(cp, toReturn);
			}
		}
		if (toReturn.isEmpty()) {
			toReturn.set(COMMON_SCRIPT);
		}
		return toReturn;
	}

	static ToolUnicodePropertySource properties = ToolUnicodePropertySource.make(Default.ucdVersion());
	static UnicodeProperty SCRIPT_EXTENSIONS = properties.getProperty("script extensions");

	private static void addScript(int cp, BitSet toReturn) {
		final byte script2 = Default.ucd().getScript(cp);
		if (script2 == COMMON_SCRIPT || script2 == INHERITED_SCRIPT) {
			final String scriptString = SCRIPT_EXTENSIONS.getValue(cp);
			if (scriptString == null) {
				return;
			}
			if (scriptString.equals("Zinh") || scriptString.equals("Zyyy")) {
				return;
			}
			if (scriptString.contains(" ")) {
				for (final String part : scriptString.split(" ")) {
					toReturn.set(findScriptCode(part));
				}
			} else {
				toReturn.set(findScriptCode(scriptString));
			}
			return;
		}
		toReturn.set(script2);
	}

	private static int findScriptCode(String part) {
		for (int i = 0; i < UCD_Names.SCRIPT.length; ++i) {
			if (part.equals(UCD_Names.SCRIPT[i])) {
				return i;
			}
		}
		return -1;
	}

	static int getFirstPrimary(String sortKey) {
		final int result = sortKey.charAt(0);
		if (UCA.isImplicitLeadPrimary(result)) {
			return (result << 16) | sortKey.charAt(1);
		}
		return (result << 16);
	}

	static final String[] CLASSNAME = {
		"<td class='q'",
		"<td class='q'",
		"<td class='q'",
		"<td class='t'",
		"<td class='s'",
	"<td class='p'"};

	static final String[] XCLASSNAME = {
		"<td class='eq'",
		"<td class='eq'",
		"<td class='eq'",
		"<td class='et'",
		"<td class='es'",
	"<td class='ep'"};


	static PrintWriter indexFile;

	static PrintWriter openFile(int count, String directory, int script) throws IOException {
		final String scriptName = getChunkName(script, LONG);
		final String shortScriptName = getChunkName(script, SHORT);
		final String hover = scriptName.equals(shortScriptName) ? "" : "' title='" + shortScriptName;

		final String fileName = "chart_" + scriptName.replace('/', '_').replace(' ', '_') + (count > 1 ? count + "" : "") + ".html";
		final PrintWriter output = Utility.openPrintWriter(directory + fileName, Utility.UTF8_WINDOWS);
		Utility.fixDot();
		System.out.println("Writing: " + scriptName);
		indexFile.println("<a href = '" + fileName + hover + "'>" + scriptName + "</a><br>\n");
		final String title = "UCA: " + scriptName;
		output.println("<!DOCTYPE HTML PUBLIC '-//W3C//DTD HTML 4.01 Transitional//EN' 'http://www.w3.org/TR/html4/loose.dtd'>\n"
				+ "<html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>");
		output.println("<title>" + title + "</title>");
		output.println("<link rel='stylesheet' href='charts.css' type='text/css'>");
		output.println("</head><body><h2>" + scriptName + "</h2>");
		output.println("<table>");
		return output;
	}

	static PrintWriter openFile2(int count, String directory, String name) throws IOException {
		final String fileName = "chart_" + name + (count > 1 ? count + "" : "") + ".html";
		final PrintWriter output = Utility.openPrintWriter(directory + fileName, Utility.UTF8_WINDOWS);
		Utility.fixDot();
		System.out.println("Writing: " + name);
		indexFile.println("<a href = '" + fileName + "'>" + name + "</a><br>\n");
		final String title = name;
		output.println("<!DOCTYPE HTML PUBLIC '-//W3C//DTD HTML 4.01 Transitional//EN' 'http://www.w3.org/TR/html4/loose.dtd'>\n" +
				"<html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>");
		output.println("<title>" + title + "</title>");
		output.println("<link rel='stylesheet' href='charts.css' type='text/css'>");
		output.println("</head><body>");
		output.println("<table>");
		return output;
	}

	static final int
	NULL_ORDER = -7,
	IGNORABLE_ORDER = -6,
	SPACE = -5,
	PUNCT = -4,
	SYMBOL = -3,
	CURRENCY = -2,
	DIGIT = -1,
	// scripts in here
	CJK = 120,
	CJK_AB = 121,
	UNSUPPORTED = 122,
	CAT_OFFSET = 128,
	// categories in here
	NO_CASE_MAPPING = 200;

	static final Matcher CAT_REMAP = Pattern.compile("([A-Z][a-z]*)([A-Z].+)").matcher("");

	static String getChunkName(int script, byte length) {
		switch(script) {
		case NO_CASE_MAPPING: return "NoCaseMapping";
		case NULL_ORDER: return "Ignored";
		case IGNORABLE_ORDER: return "Secondary";
		case SPACE: return "Whitespace";
		case PUNCT: return "Punctuation";
		case SYMBOL: return "General-Symbol";
		case CURRENCY: return "Currency-Symbol";
		case DIGIT: return "Digits";
		case CJK: return "CJK";
		case CJK_AB: return "CJK-Extensions";
		case UNSUPPORTED: return "Unsupported";
		default:
			if (script >= CAT_OFFSET) {
				Default.ucd();
				final String cat = UCD.getCategoryID_fromIndex((byte)(script - CAT_OFFSET), length);
				if (!CAT_REMAP.reset(cat).matches()) {
					return cat;
				} else {
					return CAT_REMAP.group(2) + "-" + CAT_REMAP.group(1);
				}
			} else if (script == HIRAGANA_SCRIPT && HACK_KANA) {
				return length == SHORT ? "Kata-Hira" : "Katakana/Hiragana";
			} else if (script == Meroitic_Hieroglyphs ) {
				return length == SHORT ? "Meroitic" : "Meroitic_Hieroglyphs/Cursive";
			} else {
				return Default.ucd().getCase(Default.ucd().getScriptID_fromIndex((byte)script, length), FULL, TITLE);
			}
		}
	}

	static void closeFile(PrintWriter output) {
		if (output == null) {
			return;
		}
		output.println("</table></body></html>");
		output.close();
	}


	static public final byte COLLATION = 0, NORMALIZATION = 1, CASE = 2, NAME = 3, SCRIPT = 4, NAMELIST = 5;

	static public void closeIndexFile(PrintWriter indexFile, String extra, byte choice, boolean doBreak) {
		final SimpleDateFormat df = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
		df.setTimeZone(TimeZone.getTimeZone("GMT"));

		indexFile.println("</p><hr width='50%'><p style='text-align: center;'>");
		boolean gotOne = false;
		gotOne = doIndexItem("Collation&nbsp;Charts", "collation", choice, COLLATION, gotOne, indexFile);
		gotOne = doIndexItem("Normalization&nbsp;Charts", "normalization", choice, NORMALIZATION, gotOne, indexFile);
		gotOne = doIndexItem("Case&nbsp;Charts", "case", choice, CASE, gotOne, indexFile);
		gotOne = doIndexItem("Script&nbsp;Charts", "script", choice, SCRIPT, gotOne, indexFile);
		gotOne = doIndexItem("Name&nbsp;Index&nbsp;Charts", "name", choice, NAME, gotOne, indexFile);
		gotOne = doIndexItem("Names&nbsp;List&nbsp;Charts", "nameslist", choice, NAMELIST, gotOne, indexFile);
		//        if (choice != NORMALIZATION) {
		//            if (gotOne && doBreak) indexFile.println("<br>");
		//            indexFile.println("<a href='../normalization/index.html' target='_top'>Normalization&nbsp;Charts</a><br>");
		//            gotOne = true;
		//        }
		//        if (choice != CASE) {
		//            if (gotOne && doBreak) indexFile.println("<br>");
		//            indexFile.println("<a href='../case/index.html' target='_top'>Case&nbsp;Charts</a><br>");
		//            gotOne = true;
		//        }
		//        if (choice != SCRIPT) {
		//            if (gotOne && doBreak) indexFile.println("<br>");
		//            indexFile.println("<a href='../script/index.html' target='_top'>Script&nbsp;Charts</a><br>");
		//            gotOne = true;
		//        }
		//        if (choice != NAME) {
		//            if (gotOne && doBreak) indexFile.println("<br>");
		//            indexFile.println("<a href='../name/index.html' target='_top'>Name&nbsp;Index&nbsp;Charts</a><br>");
		//            gotOne = true;
		//        }
		//        if (choice != NAMELIST) {
		//            if (gotOne && doBreak) indexFile.println("<br>");
		//            indexFile.println("<a href='../nameslist/index.html' target='_top'>Names&nbsp;List&nbsp;Charts</a><br>");
		//            gotOne = true;
		//        }
		indexFile.println("</p><hr width='50%'><p style='font-size: 70%; text-align: center;'>");
		indexFile.println("UCD: " + Default.ucd().getVersion() + extra);
		indexFile.println("<br>" + WriteCollationData.getNormalDate() + " <a href='http://www.macchiato.com/' target='_top'>MED</a>");
		indexFile.println("</p></div></body></html>");
		indexFile.close();
	}

	private static boolean doIndexItem(String htmlTitle, String folderName,
			byte choice, byte thisChoice, boolean gotOne, PrintWriter indexFile) {
		if (choice != thisChoice) {
			indexFile.println("<a href='../" +
					folderName +
					"/index.html' target='_top'>" +
					htmlTitle +
					"</a><br>");
			gotOne = true;
		} else {
			indexFile.println(htmlTitle + "<br>");
		}
		return gotOne;
	}

	static boolean containsCase(String s) {
		int cp;
		for (int i = 0; i < s.length(); i += UTF16.getCharCount(cp)) {
			cp = UTF16.charAt(s, i);
			// contains Lu, Lo, Lt, or Lowercase or Uppercase
			final byte cat = Default.ucd().getCategory(cp);
			if (cat == Lu || cat == Ll || cat == Lt) {
				return true;
			}
			if (Default.ucd().getBinaryProperty(cp, Other_Lowercase)) {
				return true;
			}
			if (Default.ucd().getBinaryProperty(cp, Other_Uppercase)) {
				return true;
			}
		}
		return false;
	}

	static final Transliterator addCircle = Transliterator.createFromRules(
			"any-addCircle", "([[:Mn:][:Me:]]) > \u25CC $1", Transliterator.FORWARD);

	public static void writeCompositionChart() throws IOException {
		final UCA uca = new UCA(null,"",null);

		final Set letters = new TreeSet();
		final Set marks = new TreeSet(uca);
		final Set totalMarks = new TreeSet(uca);
		final Map decomposes = new HashMap();
		final Set notPrinted = new TreeSet(new UTF16.StringComparator());
		final Set printed = new HashSet();

		// UnicodeSet latin = new UnicodeSet("[:latin:]");

		final PrintWriter out = Utility.openPrintWriter("log/composition_chart.html", Utility.UTF8_WINDOWS);
		try {
			out.println("<!DOCTYPE HTML PUBLIC '-//W3C//DTD HTML 4.01 Transitional//EN' 'http://www.w3.org/TR/html4/loose.dtd'>\n" +
					"<html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>");
			out.println("<style type='text/css'>");

			out.println("body { font-family: Arial Unicode MS }");
			out.println("td { text-align: Center ; vertical-align: top; width: 1%; background-color: #EEEEEE }");
			out.println("tt { font-size: 50% }");
			out.println("table { width='1%' }");
			out.println(".w { background-color: #FFFFFF }");
			out.println(".h { background-color: #EEEEFF }");
			out.println(".r { background-color: #FF0000 }");
			out.println("</style>");
			out.println("</head><body bgcolor='#FFFFFF'>");
			out.println("<h1>Composites</h1>");

			final UnicodeSetIterator it = new UnicodeSetIterator();

			for (byte script = 0; script < UCD_Types.LIMIT_SCRIPT; ++script) {

				String scriptName = "";
				try {
					Default.ucd();
					scriptName = UCD.getScriptID_fromIndex(script);
					Utility.fixDot();
					System.out.println(scriptName);
				} catch (final IllegalArgumentException e) {
					System.out.println("Failed to create transliterator for: " + scriptName + "(" + script + ")");
					continue;
				}


				letters.clear();
				letters.add(""); // header row
				marks.clear();
				notPrinted.clear();
				printed.clear();

				for (int cp = 0; cp < 0x10FFFF; ++cp) {
					final byte type = Default.ucd().getCategory(cp);
					;
					Default.ucd();
					;
					Default.ucd();
					if (type == UCD_Types.UNASSIGNED || type == UCD_Types.PRIVATE_USE)
					{
						continue; // skip chaff
					}
					Utility.dot(cp);

					final byte newScript = Default.ucd().getScript(cp);
					if (newScript != script) {
						continue;
					}

					final String source = UTF16.valueOf(cp);
					final String decomp = Default.nfd().normalize(source);
					if (decomp.equals(source)) {
						continue;
					}

					// pick up all decompositions
					int count = UTF16.getCharCount(UTF16.charAt(decomp, 0));

					if (count == decomp.length()) {
						notPrinted.add(source);
						continue; // skip unless marks
					}

					if (UCD.isHangulSyllable(cp)) {
						count = 2;
					}
					final String first = decomp.substring(0, count);
					final String second = decomp.substring(count);
					//if (!markSet.containsAll(second)) continue; // skip unless marks

					letters.add(first);
					marks.add(second);
					Utility.addToSet(decomposes, decomp, source);
					notPrinted.add(source);
					if (source.equals("\u212b")) {
						System.out.println("A-RING!");
					}
				}

				if (marks.size() != 0) {

					totalMarks.addAll(marks);


					out.println("<table border='1' cellspacing='0'>");
					out.println("<caption>" + scriptName + "<br>(" + letters.size() + " ? " + marks.size() + ")</caption>");

					final Iterator it2 = letters.iterator();
					while (it2.hasNext()) {
						final String let = (String)it2.next();
						out.println("<tr>" + showCell(Default.nfc().normalize(let), "class='h'"));
						final Iterator it3 = marks.iterator();
						while (it3.hasNext()) {
							final String mark = (String)it3.next();
							final String merge = let + mark;
							if (let.length() != 0 && decomposes.get(merge) == null) {
								out.println("<td>&nbsp;</td>");
								continue;
							}
							String comp;
							try {
								comp = Default.nfc().normalize(merge);
							} catch (final Exception e) {
								System.out.println("Failed when trying to compose <" + Utility.hex(e) + ">");
								continue;
							}
							// skip unless single char or header
							/*if (let.length() != 0
                                && (UTF16.countCodePoint(comp) != 1 || comp.equals(merge))) {
                                    out.println("<td class='x'>&nbsp;</td>");
                                    continue;
                            }
							 */
							final Set decomps = (Set) decomposes.get(merge);
							if (let.length() == 0) {
								printed.add(comp);
								out.println(showCell(comp, "class='h'"));
							} else if (decomps.contains(comp)) {
								printed.add(comp);
								out.println(showCell(comp, "class='w'"));
							} else {
								comp = (String) new ArrayList(decomps).get(0);
								printed.add(comp);
								out.println(showCell(comp, "class='r'"));
							}
						}
						out.println("</tr>");
					}
					out.println("</table><br>");

					//out.println("<table><tr><th>Other Letters</th><th>Other Marks</th></tr><tr><td>");
					//tabulate(out, atomics.iterator(),16);
					//out.println("</td><td>");
					//out.println("</td></tr></table>");

				}
				notPrinted.removeAll(printed);
				if (notPrinted.size() != 0) {
					tabulate(out, scriptName + " Excluded", notPrinted.iterator(), 24, "class='r'");
					out.println("<br>");
				}
			}

			final Set otherMarks = new TreeSet(uca);
			final UnicodeSet markSet = new UnicodeSet("[[:Me:][:Mn:]]");
			it.reset(markSet);
			while (it.next()) {
				final int cp = it.codepoint;
				final String source = UTF16.valueOf(cp);
				if (totalMarks.contains(source))
				{
					continue; // skip all that we have already
				}
				otherMarks.add(source);
			}
			tabulate(out, "Marks that never combine", otherMarks.iterator(), 24, "class='b'");

			out.println("</body></html>");

		} finally {
			out.close();
		}
	}

	public static void tabulate(PrintWriter out, String caption, Iterator it2, int limit, String classType) {
		int count = 0;
		out.println("<table border='1' cellspacing='0'><tr>");
		if (caption != null && caption.length() != 0) {
			out.println("<caption>" + caption + "</caption>");
		}
		while (it2.hasNext()) {
			if (++count > limit) {
				out.println("</tr><tr>");
				count = 1;
			}

			out.println(showCell((String)it2.next(), classType));
		}
		out.println("</tr></table>");
	}

	public static String showCell(String comp, String classType) {
		if (comp == null) {
			return "<td "
					+ classType + (classType.length() != 0 ? " " : "")
					+ ">&nbsp;</td>";
		}
		return "<td "
		+ classType + (classType.length() != 0 ? " " : "")
		+ "title='" + Utility.hex(comp) + " " + Default.ucd().getName(comp) + "'>" + addCircle.transliterate(comp)
		+ "<br><tt>" + Utility.hex(comp) + "</tt></td>";
	}



	public static void writeAllocation() throws IOException {
		final String[] names = new String[300]; // HACK, 300 is plenty for now. Fix if it ever gets larger
		final int[] starts = new int[names.length];
		final int[] ends = new int[names.length];

		final Iterator blockIterator = Default.ucd().getBlockNames().iterator();

		//UCD.BlockData blockData = new UCD.BlockData();

		int counter = 0;
		String currentName;
		//int blockId = 0;
		while (blockIterator.hasNext()) {
			//while (Default.ucd().getBlockData(blockId++, blockData)) {
			names[counter] = currentName = (String) blockIterator.next();
			if (currentName.equals("No_Block")) {
				continue;
			}
			final UnicodeSet s = Default.ucd().getBlockSet(currentName, null);
			if (s.getRangeCount() != 1) {
				throw new IllegalArgumentException("Failure with block set: " + currentName);
			}
			starts[counter] = s.getRangeStart(0);
			ends[counter] = s.getRangeEnd(0);
			//System.out.println(names[counter] + ", " + values[counter]);
			++counter;

			// HACK
			if (currentName.equals("Tags")) {
				names[counter] = "<i>reserved default ignorable</i>";
				starts[counter] = 0xE0080;
				ends[counter] = 0xE0FFF;
				++counter;
			}
		}

		/*
            Graphic
            Format
            Control
            Private Use
            Surrogate
            Noncharacter
            Reserved (default ignorable)
            Reserved (other)
		 */

		final PrintWriter out = Utility.openPrintWriter("log/allocation.html", Utility.UTF8_WINDOWS);
		try {
			out.println("<!DOCTYPE HTML PUBLIC '-//W3C//DTD HTML 4.01 Transitional//EN' 'http://www.w3.org/TR/html4/loose.dtd'>\n" +
					"<html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>");
			out.println("<title>Unicode Allocation</title></head>");
			out.println("<body bgcolor='#FFFFFF'><h1 align='center'><a href='#Notes'>Unicode Allocation</a></h1>");


			for (int textOnly = 0; textOnly < 2; ++textOnly) {
				out.println("<table border='1' cellspacing='0'>"); // width='100%'
				if (textOnly == 0) {
					out.println("<caption><b>Graphic Version</b></caption>");
					out.println("<tr><th>Start</th><th align='left'>Block Name</th><th align='left'>Size</th></tr>");
				} else {
					out.println("<caption><b>Textual Version (decimal)</b></caption>");
					out.println("<tr><th>Block Name</th><th>Start</th><th>Total</th><th>Assigned</th></tr>");
				}
				int lastEnd = -1;
				for (int i = 0; i < counter; ++i) {
					if (starts[i] != lastEnd + 1) {
						drawAllocation(out, lastEnd + 1, "<i>reserved</i>", starts[i] - lastEnd + 1, 0, "#000000", "#000000", textOnly);
					}
					final int total = ends[i] - starts[i] + 1;
					int alloc = 0;
					for (int j = starts[i]; j <= ends[i]; ++j) {
						if (Default.ucd().isAllocated(j)) {
							++alloc;
						}
					}
					//System.out.println(names[i] + "\t" + alloc + "\t" + total);
					final String color = names[i].indexOf("Surrogates") >= 0 ? "#FF0000"
							: names[i].indexOf("Private") >= 0 ? "#0000FF"
									: "#00FF00";
							final String colorReserved = names[i].indexOf("reserved default ignorable") >= 0 ? "#CCCCCC"
									: "#000000";
							drawAllocation(out, starts[i], names[i], total, alloc, color, colorReserved, textOnly);
							lastEnd = ends[i];
				}
				out.println("</table><p>&nbsp;</p>");
			}
			out.println("<h2>Key</h2><p><a name='Notes'></a>This chart lists all the Unicode blocks and their starting code points. "
					+ "The area of each bar is proportional to the total number of code points in each block. "
					+ "The colors have the following significance:<br>"
					+ "<table border='1' cellspacing='0' cellpadding='4'>"
					+ "<tr><td>Green</td><td>Graphic, Control, Format, Noncharacter* code points</td></tr>"
					+ "<tr><td>Red</td><td>Surrogate code points</td></tr>"
					+ "<tr><td>Blue</td><td>Private Use code points</td></tr>"
					+ "<tr><td>Gray</td><td>Reserved (default ignorable) code points</td></tr>"
					+ "<tr><td>Black</td><td>Reserved (other) code points</td></tr>"
					+ "</table><br>"
					+ "* Control, Format, and Noncharacter are not distinguished from Graphic characters by color, since they are mixed into other blocks. "
					+ "Tooltips on the bars show the total number of code points and the number assigned. "
					+ "(Remember that assigned <i>code points</i> are not necessarily assigned <i>characters</i>.)"
					+ "</p>");
			out.println("</body></html>");
		} finally {
			out.close();
		}
	}

	static double longestBar = 1000;
	static int longestBlock = 722402;
	static NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
	static {nf.setMaximumFractionDigits(0);}

	static void drawAllocation(PrintWriter out, int start, String title, int total, int alloc, String color, String colorReserved, int textOnly) {
		if (textOnly == 0) {
			final int unalloc = total - alloc;

			final double totalWidth = longestBar*(Math.sqrt(total) / Math.sqrt(longestBlock));
			final double allocWidth = alloc * totalWidth / total;
			final double unallocWidth = totalWidth - allocWidth;

			out.println("<tr><td  align='right'><code>" + Utility.hex(start)
					+ "</code></td><td>" + title
					+ "</td><td title='total: " + nf.format(total) + ", assigned: " + nf.format(alloc)
					+ "'><table border='0' cellspacing='0' cellpadding='0'><tr>");

			if (alloc != 0) {
				out.println("<td style='font-size:1;width:" + allocWidth + ";height:" + totalWidth
						+ "' bgcolor='" + color + "'>&nbsp;</td>");
			}
			if (unalloc != 0) {
				out.println("<td style='font-size:1;width:" + unallocWidth + ";height:" + totalWidth
						+ "' bgcolor='" + colorReserved + "'>&nbsp;</td>");
			}
			out.println("</tr></table></td></tr>");
		} else {
			out.println("<tr><td>" + title + "</td><td align='right'>" + start + "</td><td align='right'>" + total + "</td><td align='right'>" + alloc + "</td></tr>");
		}
	}

}



/*
    static final IntStack p1 = new IntStack(30);
    static final IntStack s1 = new IntStack(30);
    static final IntStack t1 = new IntStack(30);
    static final IntStack p2 = new IntStack(30);
    static final IntStack s2 = new IntStack(30);
    static final IntStack t2 = new IntStack(30);

    static int getStrengthDifference(CEList ceList, CEList lastCEList) {
        extractNonzeros(ceList, p1, s1, t1);
        extractNonzeros(lastCEList, p2, s2, t2);
        int temp = p1.compareTo(p2);
        if (temp != 0) return 3;
        temp = s1.compareTo(s2);
        if (temp != 0) return 2;
        temp = t1.compareTo(t2);
        if (temp != 0) return 1;
        return 0;
    }

    static void extractNonzeros(CEList ceList, IntStack primaries, IntStack secondaries, IntStack tertiaries) {
        primaries.clear();
        secondaries.clear();
        tertiaries.clear();

        for (int i = 0; i < ceList.length(); ++i) {
            int ce = ceList.at(i);
            int temp = UCA.getPrimary(ce);
            if (temp != 0) primaries.push(temp);
            temp = UCA.getSecondary(ce);
            if (temp != 0) secondaries.push(temp);
            temp = UCA.getTertiary(ce);
            if (temp != 0) tertiaries.push(temp);
        }
    }
 */