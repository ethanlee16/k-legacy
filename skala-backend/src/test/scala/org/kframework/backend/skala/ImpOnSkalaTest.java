package org.kframework.backend.skala;


import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.kframework.attributes.Source;
import org.kframework.backend.Backends;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Module;
import org.kframework.frontend.K;
import org.kframework.frontend.KApply;
import org.kframework.frontend.KORE;
import org.kframework.frontend.KToken;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.Kompile;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.Definition;
import org.kframework.main.GlobalOptions;
import org.kframework.minikore.converters.KoreToMini;
import org.kframework.unparser.AddBrackets;
import org.kframework.unparser.KOREToTreeNodes;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;

import javax.swing.text.html.Option;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;

public class ImpOnSkalaTest {

    private static CompiledDefinition compiledDef;
    private static String resources;
    private static KExceptionManager kem;
    private static SkalaRewriter skalaBackendRewriter;
    private static Module unparsingModule;
    private static BiFunction<String, Source, K> programParser;

    static long timeBefore = System.nanoTime();

    public static void logTime(String what) {
        System.out.println(what + ": " + ((System.nanoTime() - timeBefore) / 1000000) + "ms");
        timeBefore = System.nanoTime();
    }

    @BeforeClass
    public static void kompileSetup() {
        logTime("start");
        resources = "src/test/resources/imp/";
        File definitionFile = new File(resources + "imp.k");
        String mainModuleName = "IMP";
        KompileOptions kompileOptions = new KompileOptions();
        kompileOptions.backend = Backends.SKALA;
        GlobalOptions globalOptions = new GlobalOptions();
        globalOptions.debug = true;
        globalOptions.warnings = GlobalOptions.Warnings.ALL;
        kem = new KExceptionManager(globalOptions);
        Kompile kompile = new Kompile(kompileOptions, FileUtil.testFileUtil(), kem, false);
        compiledDef = kompile.run(definitionFile, mainModuleName, mainModuleName, new SkalaKompile(kompileOptions, kem).steps());
        logTime("kompile");
        Definition definition = KoreToMini.apply(compiledDef.kompiledDefinition);
        logTime("frontend kore to kore");
        skalaBackendRewriter = new SkalaRewriter(compiledDef.executionModule(), definition);
        logTime("make Scala Rewriter");
        unparsingModule = compiledDef.getExtensionModule(compiledDef.languageParsingModule());
        programParser = compiledDef.getProgramParser(kem);
        logTime("parse-unparse stuff");

        // warmup
        K input = getParsedProgram("initialization.imp");
        K kResult = skalaBackendRewriter.execute(input, Optional.empty()).k();
        String actual = unparseResult(kResult);
    }

    private static K getParsedProgram(String pgmName) {
        String program = FileUtil.load(new File(resources + pgmName));
        Source source = Source.apply("from test");
        K parsed = programParser.apply(program, source);
        Map<KToken, K> map = new HashMap<>();
        map.put(KORE.KToken("$PGM", Sorts.KConfigVar()), parsed);
        KApply input = KORE.KApply(compiledDef.topCellInitializer, map.entrySet().stream().map(e -> KORE.KApply(KORE.KLabel("_|->_"), e.getKey(), e.getValue())).reduce(KORE.KApply(KORE.KLabel(".Map")), (a, b) -> KORE.KApply(KORE.KLabel("_Map_"), a, b)));
        return input;
    }

    private static String unparseResult(K result) {
        return KOREToTreeNodes.toString(new AddBrackets(unparsingModule).addBrackets((org.kframework.parser.ProductionReference) KOREToTreeNodes.apply(KOREToTreeNodes.up(unparsingModule, result), unparsingModule)));
    }


    @Test
    public void sumTest() {
        K input = getParsedProgram("sum.imp");
        K kResult = skalaBackendRewriter.execute(input, Optional.empty()).k();
        String actual = unparseResult(kResult);
        assertEquals("Execution with Skala Backend Failed", "<T> <k> . </k> <state> 'n |-> 0 'sum |-> 55 </state> </T>", actual);
    }

    @Test
    public void sum100Test() {
        K input = getParsedProgram("sum100.imp");
        K kResult = skalaBackendRewriter.execute(input, Optional.empty()).k();
        String actual = unparseResult(kResult);
        assertEquals("Execution with Skala Backend Failed", "<T> <k> . </k> <state> 'n |-> 0 'sum |-> 5050 </state> </T>", actual);
    }

    @Test
    public void sum1000Test() {
        K input = getParsedProgram("sum1000.imp");
        K kResult = skalaBackendRewriter.execute(input, Optional.empty()).k();
        String actual = unparseResult(kResult);
        assertEquals("Execution with Skala Backend Failed", "<T> <k> . </k> <state> 'n |-> 0 'sum |-> 500500 </state> </T>", actual);
    }

    @Test
    public void collatzTest() {
        K input = getParsedProgram("collatz.imp");
        K kResult = skalaBackendRewriter.execute(input, Optional.empty()).k();
        String actual = unparseResult(kResult);
        assertEquals("Execution with Skala Backend Failed", "<T> <k> . </k> <state> 'n |-> 1 'r |-> 3 'q |-> 1 's |-> 66 'm |-> 2 </state> </T>", actual);
    }

    @Test
    public void primesTest() {
        K input = getParsedProgram("primes.imp");
        K kResult = skalaBackendRewriter.execute(input, Optional.empty()).k();
        String actual = unparseResult(kResult);
        assertEquals("Execution with Skala Backend Failed", "<T> <k> . </k> <state> 'n |-> 11 't |-> 0 'y |-> 20 'x |-> 0 'z |-> 10 'r |-> 1 'q |-> 0 'i |-> 2 's |-> 4 'm |-> 10 </state> </T>", actual);
    }
}