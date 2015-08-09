package org.unicode.tools;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.unicode.props.GenerateEnums;
import org.unicode.props.IndexUnicodeProperties;
import org.unicode.props.UcdProperty;
import org.unicode.props.UcdPropertyValues;
import org.unicode.props.UcdPropertyValues.Script_Values;

import com.google.common.base.Joiner;
import com.ibm.icu.dev.util.UnicodeMap;
import com.ibm.icu.dev.util.UnicodeMap.EntryRange;
import com.ibm.icu.lang.CharSequences;
import com.ibm.icu.text.UnicodeSet;

public final class ScriptDetector {
    public static final Joiner JOINER_COMMA_SPACE = Joiner.on(", ");
    public static final IndexUnicodeProperties IUP = IndexUnicodeProperties.make(GenerateEnums.ENUM_VERSION);
    public static final Set<Script_Values> INHERITED_SET = Collections.singleton(Script_Values.Inherited);
    public static final Set<Script_Values> COMMON_SET = Collections.singleton(Script_Values.Common);
    
    private static final UnicodeMap<Set<Script_Values>> FIXED_CODEPOINT_TO_SCRIPTS = fix();
//    public static final UnicodeSet COMMON_OR_INHERITED
//    = new UnicodeSet(ScriptDetector._CODEPOINT_TO_SCRIPTS.getSet(ScriptDetector.COMMON_SET))
//    .addAll(ScriptDetector._CODEPOINT_TO_SCRIPTS.getSet(ScriptDetector.INHERITED_SET))
//    .freeze();


    private boolean isCommon;
    private final EnumSet<Script_Values> singleScripts = EnumSet.noneOf(Script_Values.class);
    private final HashSet<Set<Script_Values>> combinations = new HashSet<Set<Script_Values>>();
    private final HashSet<Set<Script_Values>> toRemove = new HashSet<Set<Script_Values>>();

    /**
     * Return the script of the string, (Common if all characters are Common or Inherited)
     * or null if there is not a unique one
     * Only uses Script property for now.
     * @param source
     * @return
     */
    @SuppressWarnings("deprecation")
    public ScriptDetector set(String source) {
        singleScripts.clear();
        getCombinations().clear();
        isCommon = false;

        boolean haveCommon = false;
        for (int codepoint : CharSequences.codePoints(source)) {
            Set<Script_Values> current = ScriptDetector.getScriptExtensions(codepoint);
            if (current.size() > 1) {
                getCombinations().add(current); // we know there are not Common or Inherited
            } else {
                if (current.equals(ScriptDetector.COMMON_SET)) {
                    haveCommon = true;
                } else {
                    singleScripts.addAll(current);
                }
            }
        }
        // Remove redundant combinations
        if (getCombinations().size() > 0) {
            toRemove.clear();
            // TODO if the combinations are ordered by shortest set first, then we can optimize this
            // but such cases will be rare...
            for (Set<Script_Values> combo : getCombinations()) {
                if (!Collections.disjoint(combo, singleScripts)) {
                    toRemove.add(combo);
                } else {
                    for (Set<Script_Values> combo2 : getCombinations()) {
                        if (combo2 != combo) {
                            if (combo.containsAll(combo2)) {
                                toRemove.add(combo);
                                break; // inner loop
                            }
                        }
                    }
                }
            }
            getCombinations().removeAll(toRemove);
        }
        if (haveCommon && singleScripts.isEmpty() && getCombinations().isEmpty()) {
            singleScripts.add(Script_Values.Common);
            isCommon = true;
        }
        return this;
    }

    private static UnicodeMap<Set<Script_Values>> fix() {
        final UnicodeMap<Set<Script_Values>> codepointToScripts 
        = ScriptDetector.IUP.loadEnumSet(UcdProperty.Script_Extensions, UcdPropertyValues.Script_Values.class);
        final EnumSet<Script_Values> KANA = EnumSet.of(Script_Values.Hangul, Script_Values.Hiragana, Script_Values.Katakana);

        UnicodeMap<Set<Script_Values>>result = new UnicodeMap<>();
        for (Set<Script_Values> scriptValueSet : codepointToScripts.values()) {
            UnicodeSet uset = codepointToScripts.getSet(scriptValueSet);
            for (UnicodeSet.EntryRange range : uset.ranges()) {
                Set<Script_Values> fixedScriptValueSet = fix(scriptValueSet, KANA);
                result.putAll(range.codepoint, range.codepointEnd, fixedScriptValueSet);
            }
            for (String s : uset.strings()) {
                Set<Script_Values> fixedScriptValueSet = fix(scriptValueSet, KANA);
                result.put(s, fixedScriptValueSet);
            }
        }
        return result;
    }


    private static Set<Script_Values> fix(Set<Script_Values> scriptValueSet, Set<Script_Values> KANA) {
        //      * TODO 
        //      * scx={...Han...} : add Jpan, Kore, eg => {...Han, Jpan, Kore...}
        //      * scx={...Hiragana...} : replace by Jpan, eg => {...Hiragana, Jpan...}
        //      * scx={...Katakana...} : add Jpan, eg => {...Katakana, Jpan...}
        //      * scx={...Hangul...} : add Kore, eg => {...Han, Kore...}

        EnumSet<Script_Values> temp = null;
        if (scriptValueSet.equals(ScriptDetector.INHERITED_SET)) {
            scriptValueSet = ScriptDetector.COMMON_SET;
        } else if (scriptValueSet.contains(Script_Values.Han)) {
            temp = EnumSet.of(Script_Values.Japanese, Script_Values.Korean);
            temp.addAll(scriptValueSet);
            temp.remove(Script_Values.Bopomofo);
            temp.removeAll(KANA);
        } else if (scriptValueSet.contains(Script_Values.Hangul)) {
            temp = EnumSet.of(Script_Values.Korean);
            temp.addAll(scriptValueSet);
            temp.removeAll(KANA);
        } else if (!Collections.disjoint(scriptValueSet, KANA)) {
            temp = EnumSet.of(Script_Values.Japanese);
            temp.addAll(scriptValueSet);
            temp.removeAll(KANA);
        }
        if (temp == null) {
            return scriptValueSet;
        }
        return Collections.unmodifiableSet(temp);
    }

    public int size() {
        return singleScripts.size() + getCombinations().size();
    }

    public Set<Script_Values> getSingleSetOrNull() {
        return getCombinations().isEmpty() ? singleScripts
                : !singleScripts.isEmpty() ? null
                        : getCombinations().size() > 1 ? null
                                : getCombinations().iterator().next();
    }
    @Override
    public String toString() {
        return singleScripts.isEmpty() ? JOINER_COMMA_SPACE.join(getCombinations())
                : getCombinations().isEmpty() ? JOINER_COMMA_SPACE.join(singleScripts)
                        : JOINER_COMMA_SPACE.join(singleScripts) + ", " + JOINER_COMMA_SPACE.join(getCombinations());
    }

    public Set<Set<Script_Values>> getAll() {
        if (singleScripts.isEmpty()) {
            return getCombinations();
        }
        Set<Set<Script_Values>> result = new LinkedHashSet<>();
        for (Script_Values script : singleScripts) {
            result.add(Collections.singleton(script));
        }
        result.addAll(getCombinations());
        return result;
    }

    public static UnicodeSet getCharactersForScriptExtensions(Set<Script_Values> scriptValueSet) {
        return ScriptDetector.FIXED_CODEPOINT_TO_SCRIPTS.getSet(scriptValueSet);
    }

    public static Set<Script_Values> getScriptExtensions(int codepoint) {
        return ScriptDetector.FIXED_CODEPOINT_TO_SCRIPTS.get(codepoint);
    }

    /**
     * @return the isCommon
     */
    public boolean isCommon() {
        return isCommon;
    }

    /**
     * @return the singleScripts
     */
    public EnumSet<Script_Values> getSingleScripts() {
        return singleScripts;
    }

    /**
     * @return the combinations
     */
    public HashSet<Set<Script_Values>> getCombinations() {
        return combinations;
    }
}