package org.unicode.text.utility;

import org.unicode.cldr.util.CldrUtility;

public class Settings {
    
    public static final boolean BUILD_FOR_COMPARE = org.unicode.cldr.util.CldrUtility.getProperty("BUILD_FOR_COMPARE", "false").startsWith("t");

    public static final String BASE_DIRECTORY = Utility.fixFileName(
    		CldrUtility.getProperty("BASE_DIRECTORY", "/Users/markdavis")) + "/";
    public static final String SVN_WORKSPACE_DIRECTORY = Utility.fixFileName(
    		CldrUtility.getProperty("SVN_WORKSPACE", Settings.BASE_DIRECTORY + "Documents/workspace")) + "/";
    public static final String OTHER_WORKSPACE_DIRECTORY = Utility.fixFileName(
            CldrUtility.getProperty("OTHER_WORKSPACE", Settings.BASE_DIRECTORY + "Google Drive/workspace")) + "/";

    /**
     * Used for the default version.
     */
    public static final String latestVersion = "11.0.0";
    public static final String lastVersion = "10.0.0"; // last released version

    public static final boolean SKIP_COPYRIGHT = "skip".equalsIgnoreCase(CldrUtility.getProperty("copyright", "skip"));

    public static final String DATA_DIR = Utility.fixFileName(CldrUtility.getProperty("UCD_DIR", "data/")) + "/";
    public static final String UCD_DIR = DATA_DIR + "ucd/";
    public static final String IDN_DIR = DATA_DIR + "IDN/";
    public static final String DICT_DIR = DATA_DIR + "dict/";
    
    public static final String UNICODETOOLS_DIRECTORY = SVN_WORKSPACE_DIRECTORY + "unicodetools/";
    public static final String UNICODEJSPS_DIRECTORY = SVN_WORKSPACE_DIRECTORY + "UnicodeJsps/";
    public static final String UNICODE_DRAFT_DIRECTORY = SVN_WORKSPACE_DIRECTORY + "unicode-draft/";
    public static final String UNICODE_DRAFT_PUBLIC = SVN_WORKSPACE_DIRECTORY + "unicode-draft/Public/";

    public static final String GEN_DIR_OLD = Utility.fixFileName(CldrUtility.getProperty("GEN_DIR", OTHER_WORKSPACE_DIRECTORY+"Generated")) + "/";
    public static final String GEN_DIR = BUILD_FOR_COMPARE ? UNICODE_DRAFT_PUBLIC : GEN_DIR_OLD;
    public static final String BIN_DIR = GEN_DIR_OLD + "BIN/";
    public static final String GEN_UCD_DIR = GEN_DIR + "ucd/";
    
    public static final String CHARTS_GEN_DIR = UNICODE_DRAFT_DIRECTORY + "charts/";

    public static final String SRC_DIR = Utility.fixFileName("org/unicode/text") + "/";
    public static final String SRC_UCA_DIR = SRC_DIR + "UCA/";
    public static final String SRC_UCD_DIR = SRC_DIR + "UCD/";
}
