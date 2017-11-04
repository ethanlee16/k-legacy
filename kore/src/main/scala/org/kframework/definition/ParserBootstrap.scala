package org.kframework.definition

import org.kframework.attributes.Att
import org.kframework.kore.ADT._
import org.kframework.kore._
import collection.JavaConverters._

/**
  * Created by lpena on 10/11/16.
  */

object KParserBootsrap {

  implicit def BecomingNonTerminal(s: ADT.SortLookup): NonTerminal = NonTerminal(s)
  implicit def BecomingTerminal(s: String): Terminal = Terminal(s)
  implicit def BecomingSequence(ps: ProductionItem*): Seq[ProductionItem] = ps

  def Sort(s: String): ADT.SortLookup = ADT.SortLookup(s)

  def regex(s: String): ProductionItem = RegexTerminal("#", s, "#")

  case class token(s: ADT.SortLookup) {
    def is(pis: ProductionItem): BecomingToken = BecomingToken(s, List(pis))
  }

  case class BecomingToken(sort: ADT.SortLookup, pis: Seq[ProductionItem]) {
    def att(atts: K*): Production = Production(sort, pis, atts.foldLeft(Att() + "token")(_+_))
  }

  implicit def tokenWithoutAttributes(bp: BecomingToken) : Production =
    Production(bp.sort, bp.pis, Att() + "token")

  case class syntax(s: ADT.SortLookup) {
    def is(pis: ProductionItem*): BecomingSyntax = BecomingSyntax(s, pis)
  }

  case class BecomingSyntax(sort: ADT.SortLookup, pis: Seq[ProductionItem]) {
    def att(atts: K*): Production = Production(sort, pis, atts.foldLeft(Att())(_+_))
  }

  implicit def syntaxWithoutAttributes(bp: BecomingSyntax) : Production =
    Production(bp.sort, bp.pis, Att())

  case class Axiom(ax: String, attr: Att) extends Sentence {
    val att = attr
  }

  def axiom(ax: String): BecomingAxiom = BecomingAxiom(ax)

  case class BecomingAxiom(ax: String) {
    def att(atts: K*): Axiom = Axiom(ax, atts.foldLeft(Att())(_+_))
  }

  implicit def axiomWithoutAttributes(bax: BecomingAxiom) : Axiom =
    Axiom(bax.ax, Att())

  def imports(s: Module*): Set[Module] = s.toSet
  def sentences(s: Sentence*): Set[Sentence] = s.toSet
  def klabel(label: String): K = Att.asK("klabel", label)


  def downToModule(parsed: K): Module = {
    // KApply(KLabel("module___endmodule"), moduleName, importList, sentenceList) =>
    // BecomingModule(downToModuleName(moduleName), downToImportStringSet(importList)
    // downToSentenceList(sentenceList)
    // syntax(getSort(s))
    ???
  }

  def getASTNodes(parsed: K, nodeLabel: String): List[K] = parsed match {
    case node@KApply(nl, klist, _) => klist.items.asScala.flatMap(x => getASTNodes(x, nodeLabel)).toList ++ (if (nl == KLabelLookup(nodeLabel)) List(node) else List.empty)
    case _ => List.empty
  }

  def getSortMap(parsed: K): Map[String, ADT.SortLookup] =
    getASTNodes(parsed, "syntax_::=_").foldLeft(Map.empty : Map[String, ADT.SortLookup]) ( (x, z) => z match {
      case KApply(_, KList(KToken(sortName, _, _) :: tl), _) => Map(sortName -> Sort(sortName)) ++ x
    })

  def getASTModules(parsed: K): List[K] = getASTNodes(parsed, "module___endmodule")






  // module KTOKENS
  //   .KImportList
  //
  //   token KString       ::= r"\"[a-zA-Z0-9\\-]*\"" [klabel(KString), .Attributes]
  //   token KSort         ::= r"[A-Z][A-Za-z0-9]*" [klabel(KSort), .Attributes]
  //   token KAttributeKey ::= r"[a-z][A-Za-z\\-0-9]*" [klabel(KAttributeKey), .Attributes]
  //   token KModuleName   ::= r"[A-Z][A-Z]*" [klabel(KModuleName), .Attributes]
  //
  //   .KSentenceList
  // endmodule

  val KString = Sort("KString")
  val KSort = Sort("KSort")
  val KAttributeKey = Sort("KAttributeKey")
  val KModuleName = Sort("KModuleName")

  val KTOKENS = Module("KTOKENS", imports(), sentences(

    token(KString) is regex("[\\\"](([^\\\"\n\r\\\\])|([\\\\][nrtf\\\"\\\\])|([\\\\][x][0-9a-fA-F]{2})|([\\\\][u][0-9a-fA-F]{4})|([\\\\][U][0-9a-fA-F]{8}))*[\\\"]") att klabel("KString"),
    token(KSort) is regex("[A-Z][A-Za-z0-9]*") att klabel("KSort"),
    token(KAttributeKey) is regex("[a-z][A-Za-z\\-0-9]*") att klabel("KAttributeKey"),
    token(KModuleName) is regex("[A-Z][A-Z\\-]*") att klabel("KModuleName")

  ))

  val KML_STRING =
    """
    module KML
      imports KTOKENS .KImportList
      syntax KMLVar ::= "kmlvar" "(" KString ")" [.KAttributes]
      syntax KMLFormula ::= KMLVar [.KAttributes]
      syntax KMLFormula ::= "KMLtrue" [.KAttributes]
      syntax KMLFormula ::= "KMLfalse" [.KAttributes]
      syntax KMLFormula ::= KMLFormula "KMLand" KMLFormula [.KAttributes]
      syntax KMLFormula ::= KMLFormula "KMLor" KMLFormula [.KAttributes]
      syntax KMLFormula ::= "KMLnot" KMLFormula [.KAttributes]
      syntax KMLFormula ::= "KMLexists" KMLVar "." KMLFormula [.KAttributes]
      syntax KMLFormula ::= "KMLforall" KMLVar "." KMLFormula [.KAttributes]
      syntax KMLFormula ::= KMLFormula "KML=>" KMLFormula [.KAttributes]
      .KSentenceList
    endmodule
    """

  val KMLVar = Sort("KMLVar")
  val KMLFormula = Sort("KMLFormula")

  val KML = Module("KML", imports(KTOKENS), sentences(

    syntax(KMLVar) is ("kmlvar", "(", KString, ")") att klabel("kmlvar(_)"),

    syntax(KMLFormula) is KMLVar,
    syntax(KMLFormula) is "KMLtrue" att klabel("KMLtrue"),
    syntax(KMLFormula) is "KMLfalse" att klabel("KMLfalse"),
    syntax(KMLFormula) is (KMLFormula, "KMLand", KMLFormula) att klabel("_KMLand_"),
    syntax(KMLFormula) is (KMLFormula, "KMLor", KMLFormula) att klabel("_KMLor_"),
    syntax(KMLFormula) is ("KMLnot", KMLFormula) att klabel("KMLnot_"),
    syntax(KMLFormula) is ("KMLexists", KMLVar, ".", KMLFormula) att klabel("KMLexists_._"),
    syntax(KMLFormula) is ("KMLforall", KMLVar, ".", KMLFormula) att klabel("KMLforall_._"),
    syntax(KMLFormula) is (KMLFormula, "KML=>", KMLFormula) att klabel("_KML=>_")

  ))

  val KATTRIBUTES_STRING =
    """
    module KATTRIBUTES
      imports KTOKENS .KImportList
      syntax KKeyList ::= KAttributeKey [.KAttributes]
      syntax KKeyList ::= KAttributeKey "," KKeyList [.KAttributes]
      syntax KAttribute ::= KAttributeKey [.KAttributes]
      syntax KAttribute ::= KAttributeKey "(" KKeyList ")" [.KAttributes]
      syntax KAttributes ::= ".KAttributes" [.KAttributes]
      syntax KAttributes ::= KAttribute "," KAttributes [.KAttributes]
      .KSentenceList
    endmodule
    """

  val KKeyList = Sort("KeyList")
  val KAttribute = Sort("Attribute")
  val KAttributes = Sort("Attributes")

  val KATTRIBUTES = Module("KATTRIBUTES", imports(KTOKENS), sentences(

    syntax(KKeyList) is KAttributeKey,
    syntax(KKeyList) is (KAttributeKey, ",", KKeyList) att klabel("_,_"),

    syntax(KAttribute) is KAttributeKey,
    syntax(KAttribute) is (KAttributeKey, "(", KKeyList, ")") att klabel("_(_)"),
    syntax(KAttributes) is ".KAttributes" att klabel(".KAttributes"),
    syntax(KAttributes) is (KAttribute, ",", KAttributes) att klabel("_,_")

  ))

  val KSENTENCES_STRING =
    """
    module KSENTENCES
      imports KATTRIBUTES .KImportList
      syntax KImport ::= "imports" KModuleName [.KAttributes]
      syntax KImportList ::= ".KImportList" [.KAttributes]
      syntax KImportList ::= KImport KImportList [.KAttributes]
      syntax KTerminal ::= KString [.KAttributes]
      syntax KNonTerminal ::= KSort [.KAttributes]
      syntax KProductionItem ::= KTerminal [.KAttributes]
      syntax KProductionItem ::= KNonTerminal [.KAttributes]
      syntax KProduction ::= KProductionItem [.KAttributes]
      syntax KProduction ::= KProductionItem KProduction [.KAttributes]
      syntax KPreSentence ::= "token" KSort "::=" KProduction [.KAttributes]
      syntax KPreSentence ::= "syntax" KSort "::=" KProduction [.KAttributes]
      syntax KPreSentence ::= "axiom" KMLFormula [.KAttributes]
      syntax KSentence ::= KPreSentence "[" KAttributes "]" [.KAttributes]
      syntax KSentenceList ::= ".KSentenceList" [.KAttributes]
      syntax KSentenceList ::= KSentence KSentenceList [.KAttributes]
      .KSentenceList
    endmodule
    """

  val KImport = Sort("KImport")
  val KImportList = Sort("KImportList")

  val KTerminal = Sort("KTerminal")
  val KNonTerminal = Sort("KNonTerminal")
  val KProductionItem = Sort("KProductionItem")
  val KProduction = Sort("KProduction")

  val KPreSentence = Sort("KPreSentence")
  val KSentence = Sort("KSentence")
  val KSentenceList = Sort("KSentenceList")

  val KSENTENCES = Module("KSENTENCES", imports(KATTRIBUTES, KML), sentences(

    syntax(KImport) is ("imports", KModuleName) att klabel("imports_"),
    syntax(KImportList) is ".KImportList" att klabel(".KImportList"),
    syntax(KImportList) is (KImport, KImportList) att klabel("__"),

    syntax(KTerminal) is KString,
    syntax(KNonTerminal) is KSort,
    syntax(KProductionItem) is KTerminal,
    syntax(KProductionItem) is KNonTerminal,
    syntax(KProduction) is KProductionItem,
    syntax(KProduction) is (KProductionItem, KProduction) att klabel("__"),

    syntax(KPreSentence) is ("token", KSort, "::=", KProduction) att klabel("token_::=_"),
    syntax(KPreSentence) is ("syntax", KSort, "::=", KProduction) att klabel("syntax_::=_"),
    syntax(KPreSentence) is ("axiom", KMLFormula) att klabel("axiom_"),

    syntax(KSentence) is (KPreSentence, "[", KAttributes, "]") att klabel("_[_]"),
    syntax(KSentenceList) is ".KSentenceList" att klabel(".KSentenceList"),
    syntax(KSentenceList) is (KSentence, KSentenceList) att klabel("__")

  ))

  val KDEFINITION_STRING =
    """
    module KDEFINITION
      imports KSENTENCES .KImportList
      syntax KModule ::= "module" KModuleName KImportList KSentenceList "endmodule" [.KAttributes]
      syntax KModuleList ::= KModule [.KAttributes]
      syntax KModuleList ::= KModule KModuleList [.KAttributes]
      syntax KRequire ::= "require" KString [.KAttributes]
      syntax KRequireList ::= ".KRequireList" [.KAttributes]
      syntax KRequireList ::= Require RequireList [.KAttributes]
      syntax KDefinition ::= KRequireList KModuleList [.KAttributes]
      .KSentenceList
    endmodule
    """

  val KModule = Sort("KModule")
  val KModuleList = Sort("KModuleList")

  val KRequire = Sort("KRequire")
  val KRequireList = Sort("KRequireList")
  val KDefinition = Sort("KDefinition")

  val KDEFINITION = Module("KDEFINITION", imports(KSENTENCES), sentences(

    syntax(KModule) is ("module", KModuleName, KImportList, KSentenceList, "endmodule") att klabel("module___endmodule"),
    syntax(KModuleList) is KModule,
    syntax(KModuleList) is (KModule, KModuleList) att klabel("__"),

    syntax(KRequire) is ("require", KString) att klabel("require_"),
    syntax(KRequireList) is ".KRequireList" att klabel(".KRequireList"),
    syntax(KRequireList) is (KRequire, KRequireList) att klabel("__"),

    syntax(KDefinition) is (KRequireList, KModuleList) att klabel("__")

  ))

  val ALL_DEFS_STRING = ".KRequireList" + "\n" + KML_STRING + "\n" + KATTRIBUTES_STRING + "\n" + KSENTENCES_STRING + "\n" + KDEFINITION_STRING

  val EXP_STRING =
    """
    module EXP
      .KImportList
      syntax Exp ::= "0" [.KAttributes]
      syntax Exp ::= "1" [.KAttributes]
      syntax Exp ::= Exp "+" Exp [.KAttributes]
      syntax Exp ::= Exp "*" Exp [.KAttributes]
      .KSentenceList
    endmodule
    """

  val Exp = Sort("Exp")

  val EXP = Module("EXP", imports(), sentences(

    syntax(Exp) is "0" att klabel("0"),
    syntax(Exp) is "1" att klabel("1"),
    syntax(Exp) is (Exp, "+", Exp) att klabel("_+_"),
    syntax(Exp) is (Exp, "*", Exp) att klabel("_*_")

  ))

}
