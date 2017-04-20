package org.kframework.tinyimplementation

import org.kframework.definition._
import org.kframework.legacykore._
import org.kframework.meta.{Down, Up}

class TestMeta {

  import org.junit._

  val up = new Up(KORE, Set())

  val down = Down(Down.constructorBased(Set(
    "org.kframework.definition",
    "scala.collection.immutable",
    "org.kframework.kore.ADT",
    "org.kframework.kore.ADT$",
    "org.kframework.attributes")))

  import org.kframework.legacykore.KORE._

  @Test def simple() {
    //    assertEquals(1: K, up(1))
    //    assertEquals('List(1, 2, 3), up(List[Int](1, 2, 3)))
    //    assertEquals('Definition('Set('Require(KToken(Sort("File"), "foo.k"))), 'Set(),
    //      KLabel("org.kframework.attributes.Att")('Set())),
    //      up(Definition(Set(Require(new File("foo.k"))), Set())))

    //    assertEquals('Foo(5), Meta(Foo(5)))
  }

  def assertEquals(x: Any, y: Any) {
    if (x != y) Assert.assertEquals(x.toString, y.toString)
  }

  val m = Module("TEST", Set(), Set(Production("Foo", Sort("Foo"), Seq(Terminal("Bar", Seq())))))
  val d = Definition(m, Set(m), Att())

  val metamodule = 'Module ("TEST", 'Set (),
    'Set ('Production ('SortLookup ("Foo", 'ModuleName("*")), 'List ('Terminal ("Bar")))))

  val metad = 'Definition (metamodule, 'Set (metamodule), 'Att ('Set ()))

  @Test def location(): Unit = {
    val imports = Set("org.kframework.attributes")
    assertEquals(Location(1, 2, 3, 4), down('Location (1, 2, 3, 4)))
    val up = new Up(KORE, imports)
    assertEquals('Location (1, 2, 3, 4), up(Location(1, 2, 3, 4)))
  }

  @Test def upDownSort(): Unit = {
    val imports = Set("org.kframework.attributes")
    val sort = Sort("Exp")
    val up = new Up(KORE, imports)
    assertEquals(sort, down(up(sort)))
  }

  @Test def upDownNonObjectAttribute(): Unit = {
    val imports = Set("org.kframework.attributes")
    val att = Att().add("token", "abc")
    val up = new Up(KORE, imports)
    println(up(att))
    assertEquals(att, down(up(att)))
  }

  @Test def upDownList: Unit = {
    assertEquals('List (), up(List()))
    assertEquals(List(), down('List ()))
  }

  @Test def upDownProduction: Unit = {
    val expected = Production(Sort("Exp"), Seq(), Att())
    assertEquals(expected, down(up(expected)))
  }

  @Test def upDownProd() {

    {
      val prod = Production(Sort("Exp"), Seq(), Att())
      val prod2 = Production(Sort("Exp"), Seq(), Att().add("originalProd", prod))
      val actual: Production = prod2.att.get("originalProd").get

      assertEquals(prod, actual)
    }
    {
      val prod = Production(Sort("Exp"), Seq(NonTerminal(Sort("Int")), Terminal("+", Seq()), RegexTerminal("#", "[a-z]", "#")), Att())
      val prod2 = Production(Sort("Exp"), Seq(), Att().add("originalProd", prod))
      assertEquals(prod, prod2.att.get("originalProd").get)
    }
    {
      val prod = Production(Sort("Exp"), Seq(), Att().add("token", "abc"))
      val prod2 = Production(Sort("Exp"), Seq(), Att().add("originalProd", prod))
      assertEquals(prod, prod2.att.get("originalProd").get)
    }
    {
      val prod = Production(Sort("Exp"), Seq(), Att().add("token"))
      val prod2 = Production(Sort("Exp"), Seq(), Att().add("originalProd", prod))
      assertEquals(prod, prod2.att.get("originalProd").get)
    }
  }
}