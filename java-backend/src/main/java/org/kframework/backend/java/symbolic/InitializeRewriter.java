// Copyright (c) 2015-2016 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import org.apache.commons.lang3.tuple.Pair;
import org.kframework.KapiGlobal;
import org.kframework.ProofResult;
import org.kframework.RewriterResult;
import org.kframework.backend.java.MiniKoreUtils;
import org.kframework.backend.java.compile.KOREtoBackendKIL;
import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.backend.java.kil.Definition;
import org.kframework.backend.java.kil.GlobalContext;
import org.kframework.backend.java.kil.KItem;
import org.kframework.backend.java.kil.KLabelConstant;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.util.JavaKRunState;
import org.kframework.definition.Module;
import org.kframework.definition.Rule;
import org.kframework.kast.Kast;
import org.kframework.kil.Attribute;
import org.kframework.frontend.K;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.api.KRunState;
import org.kframework.krun.api.io.FileSystem;
import org.kframework.main.GlobalOptions;
import org.kframework.minikore.converters.MiniToKore;
import org.kframework.rewriter.Rewriter;
import org.kframework.rewriter.SearchType;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.options.SMTOptions;
import scala.Tuple2;
import scala.collection.JavaConversions;

import java.lang.invoke.MethodHandle;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by dwightguth on 5/6/15.
 */
public class InitializeRewriter implements Function<Pair<Module, org.kframework.kore.Definition>, Rewriter> {

    private final FileSystem fs;
    private final boolean deterministicFunctions;
    private final GlobalOptions globalOptions;
    private final KExceptionManager kem;
    private final SMTOptions smtOptions;
    private final Map<String, MethodHandle> hookProvider;
    private final List<String> transitions;
    private final KRunOptions krunOptions;
    private final FileUtil files;
    private final InitializeDefinition initializeDefinition;
    private static final int NEGATIVE_VALUE = -1;

    public InitializeRewriter(
            FileSystem fs,
            boolean deterministicFunctions,
            GlobalOptions globalOptions,
            KExceptionManager kem,
            SMTOptions smtOptions,
            Map<String, MethodHandle> hookProvider,
            List<String> transitions,
            KRunOptions krunOptions,
            FileUtil files,
            InitializeDefinition initializeDefinition) {
        this.fs = fs;
        this.deterministicFunctions = deterministicFunctions;
        this.globalOptions = globalOptions;
        this.kem = kem;
        this.smtOptions = smtOptions;
        this.hookProvider = hookProvider;
        this.transitions = transitions;
        this.krunOptions = krunOptions;
        this.files = files;
        this.initializeDefinition = initializeDefinition;
    }

    public InitializeRewriter(KapiGlobal g,
                              Map<String, MethodHandle> hookProvider,
                              InitializeDefinition initializeDefinition) {
        this(g.fs, g.deterministicFunctions, g.globalOptions, g.kem, g.smtOptions, hookProvider, g.kompileOptions.transition, g.kRunOptions, g.files, initializeDefinition);
    }

    @Override
    public synchronized Rewriter apply(Pair<Module, org.kframework.kore.Definition> modulePair) {
        TermContext initializingContext = TermContext.builder(new GlobalContext(fs, deterministicFunctions, globalOptions, krunOptions, kem, smtOptions, hookProvider, files, Stage.INITIALIZING))
                .freshCounter(0).build();
        org.kframework.kore.Module mainModule = null;
        Definition definition;
        if (modulePair.getRight() != null) {
            mainModule = MiniKoreUtils.getMainModule(modulePair.getRight());
            definition = initializeDefinition.invoke(kem, initializingContext.global(), mainModule, modulePair.getRight());
        } else {
            definition = initializeDefinition.invoke(modulePair.getKey(), kem, initializingContext.global());
        }
        GlobalContext rewritingContext = new GlobalContext(fs, deterministicFunctions, globalOptions, krunOptions, kem, smtOptions, hookProvider, files, Stage.REWRITING);
        rewritingContext.setDefinition(definition);
        Kast kastParser = new Kast(files);
        rewritingContext.setKastParser(kastParser);

        return new SymbolicRewriterGlue(modulePair.getKey(), definition, definition, transitions, initializingContext.getCounterValue(), rewritingContext, kem);
    }

    public static class SymbolicRewriterGlue implements Rewriter {

        private SymbolicRewriter rewriter;
        public final Definition definition;
        public Definition miniKoreDefinition;
        public final Module module;
        private final BigInteger initCounterValue;
        public final GlobalContext rewritingContext;
        private final KExceptionManager kem;
        private final List<String> transitions;

        public SymbolicRewriterGlue(
                Module module,
                Definition definition,
                Definition miniKoreDefinition,
                List<String> transitions,
                BigInteger initCounterValue,
                GlobalContext rewritingContext,
                KExceptionManager kem) {
            this.transitions = transitions;
            this.rewriter = null;
            this.definition = definition;
            this.miniKoreDefinition = miniKoreDefinition;
            this.module = module;
            this.initCounterValue = initCounterValue;
            this.rewritingContext = rewritingContext;
            this.kem = kem;
        }

        @Override
        public RewriterResult execute(K k, Optional<Integer> depth) {
            TermContext termContext = TermContext.builder(rewritingContext).freshCounter(initCounterValue).build();
            KOREtoBackendKIL converter = new KOREtoBackendKIL(module, definition, termContext.global(), false);
            termContext.setKOREtoBackendKILConverter(converter);
            Term backendKil = MacroExpander.expandAndEvaluate(termContext, kem, converter.convert(k));
            this.rewriter = new SymbolicRewriter(rewritingContext, transitions, new KRunState.Counter(), converter);
            JavaKRunState result = (JavaKRunState) rewriter.rewrite(new ConstrainedTerm(backendKil, termContext), depth.orElse(-1));
            return new RewriterResult(result.getStepsTaken(), result.getJavaKilTerm());
        }

        @Override
        public K match(K k, org.kframework.definition.Rule rule) {
            return search(k, Optional.of(0), Optional.empty(), rule, SearchType.STAR, true);
        }


        @Override
        public K search(K initialConfiguration, Optional<Integer> depth, Optional<Integer> bound, Rule pattern, SearchType searchType, boolean resultsAsSubstitution) {
            TermContext termContext = TermContext.builder(rewritingContext).freshCounter(initCounterValue).build();
            KOREtoBackendKIL converter = new KOREtoBackendKIL(module, definition, termContext.global(), false);
            termContext.setKOREtoBackendKILConverter(converter);
            Term javaTerm = MacroExpander.expandAndEvaluate(termContext, kem, converter.convert(initialConfiguration));
            org.kframework.backend.java.kil.Rule javaPattern = converter.convert(Optional.empty(), pattern);
            this.rewriter = new SymbolicRewriter(rewritingContext, transitions, new KRunState.Counter(), converter);
            return rewriter.search(javaTerm, javaPattern, bound.orElse(NEGATIVE_VALUE), depth.orElse(NEGATIVE_VALUE), searchType, termContext, resultsAsSubstitution);
        }


        public Tuple2<RewriterResult, K> executeAndMatch(K k, Optional<Integer> depth, Rule rule) {
            RewriterResult res = execute(k, depth);
            return Tuple2.apply(res, match(res.k(), rule));
        }

        @Override
        public ProofResult prove(List<Rule> rules) {
            TermContext termContext = TermContext.builder(rewritingContext).freshCounter(initCounterValue).build();
            KOREtoBackendKIL converter = new KOREtoBackendKIL(module, definition, termContext.global(), false);
            termContext.setKOREtoBackendKILConverter(converter);
            List<org.kframework.backend.java.kil.Rule> javaRules = rules.stream()
                    .map(r -> converter.convert(Optional.<Module>empty(), r))
                    .map(r -> new org.kframework.backend.java.kil.Rule(
                            r.label(),
                            r.leftHandSide().evaluate(termContext),
                            r.rightHandSide().evaluate(termContext),
                            r.requires(),
                            r.ensures(),
                            r.freshConstants(),
                            r.freshVariables(),
                            r.lookups(),
                            r,
                            termContext.global()))
                    .collect(Collectors.toList());
            List<org.kframework.backend.java.kil.Rule> allRules = javaRules.stream()
                    .map(org.kframework.backend.java.kil.Rule::renameVariables)
                    .collect(Collectors.toList());

            // rename all variables again to avoid any potential conflicts with the rules in the semantics
            javaRules = javaRules.stream()
                    .map(org.kframework.backend.java.kil.Rule::renameVariables)
                    .collect(Collectors.toList());

            this.rewriter = new SymbolicRewriter(rewritingContext, transitions, new KRunState.Counter(), converter);

            List<ConstrainedTerm> proofResults = javaRules.stream()
                    .filter(r -> !r.containsAttribute(Attribute.TRUSTED_KEY))
                    .map(r -> {
                        ConstrainedTerm lhs = r.createLhsPattern(termContext);
                        ConstrainedTerm rhs = r.createRhsPattern();
                        termContext.setInitialVariables(lhs.variableSet());
                        return rewriter.proveRule(lhs, rhs, allRules);
                    })
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            List<K> filteredResults = proofResults.stream()
                        .map(ConstrainedTerm::term)
                        .map(t -> (KItem) t)
                        .collect(Collectors.toList());

            if (filteredResults.isEmpty()) {
               return new ProofResult(filteredResults, ProofResult.Status.PROVED);
            }
            return new ProofResult(filteredResults, ProofResult.Status.NOT_PROVED);
        }

    }


    public static class InitializeDefinition {

        private final Map<Module, Definition> cache = new LinkedHashMap<Module, Definition>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Module, Definition> eldest) {
                return this.size() > 20;
            }
        };

        public Definition invoke(Module module, KExceptionManager kem, GlobalContext global) {
            if (cache.containsKey(module)) {
                return cache.get(module);
            }
            Definition definition = new Definition(module, kem);

            global.setDefinition(definition);

            JavaConversions.setAsJavaSet(module.attributesFor().keySet()).stream()
                    .map(l -> KLabelConstant.of(l.name(), definition))
                    .forEach(definition::addKLabel);
            definition.addKoreRules(module, global);
            cache.put(module, definition);
            return definition;
        }


        public Definition invoke(KExceptionManager kem, GlobalContext global, org.kframework.kore.Module miniKoreModule, org.kframework.kore.Definition miniKoreDefinition) {
            MiniKoreUtils.ModuleUtils moduleUtils = new MiniKoreUtils.ModuleUtils(miniKoreModule, miniKoreDefinition);
            Definition definition = new Definition(moduleUtils, kem);

            global.setDefinition(definition);

            JavaConversions.setAsJavaSet(moduleUtils.attributesFor().keySet()).stream()
                    .map(l -> KLabelConstant.of(l, definition))
                    .forEach(definition::addKLabel);

            //Todo: Bypass Conversion to Kore
            Module koreModule = MiniToKore.apply(MiniKoreUtils.getOriginalModuleMap(miniKoreDefinition), JavaConversions.mapAsScalaMap(new HashMap<String, Module>()), miniKoreModule);

            //TODO: Change add KoreRules and the converter to use MiniKore
            definition.addKoreRules(koreModule, global);
            return definition;
        }

    }
}
