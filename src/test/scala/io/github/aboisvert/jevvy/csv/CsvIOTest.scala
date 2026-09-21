package io.github.aboisvert.jevvy.csv

import munit.FunSuite

import java.nio.file.Files

class CsvIOTest extends FunSuite:

  test("read empty file yields empty table"):
    val path = Files.createTempFile("jevvy-empty", ".csv").toString
    try
      Files.writeString(java.nio.file.Path.of(path), "")
      assertEquals(CsvIO.read(path), Right(CsvTable(Nil, Nil)))
    finally Files.deleteIfExists(java.nio.file.Path.of(path))

  test("write and read roundtrip"):
    val path = Files.createTempFile("jevvy-roundtrip", ".csv").toString
    try
      val headers = Seq("a", "b")
      val rows = Seq(Seq("1", "2"), Seq("3", "4"))
      assert(CsvIO.write(path, headers, rows).isRight)
      val table = CsvIO.read(path).getOrElse(fail("read failed"))
      assertEquals(table.headers, headers)
      assertEquals(table.rows.size, 2)
      assertEquals(table.rows.head.get("a"), Some("1"))
      assertEquals(table.rows.last.get("b"), Some("4"))
    finally Files.deleteIfExists(java.nio.file.Path.of(path))

  test("rowValuesInOrder follows header order"):
    val row = Map("z" -> "last", "a" -> "first")
    assertEquals(CsvIO.rowValuesInOrder(Seq("a", "z", "missing"), row), Seq("first", "last", ""))

  test("rowToState sorts fields by key"):
    val state = CsvIO.rowToState(Map("b" -> "two", "a" -> "one"))
    assertEquals(state.asString.contains("\"a\""), true)
    val idxA = state.asString.indexOf("\"a\"")
    val idxB = state.asString.indexOf("\"b\"")
    assert(idxA >= 0 && idxB >= 0)
    assert(idxA < idxB)
