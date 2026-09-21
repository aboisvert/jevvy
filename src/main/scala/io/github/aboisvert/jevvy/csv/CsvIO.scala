package io.github.aboisvert.jevvy.csv

import com.github.tototoshi.csv.*

final case class CsvTable(headers: Seq[String], rows: Seq[Map[String, String]])

object CsvIO:

  /** Reads CSV row-by-row; `f` must fully consume `rows` before returning
    * (reader closes after `f`).
    */
  def readRows[A](path: String)(
      f: (headers: Seq[String], rows: Iterator[Map[String, String]]) => A
  ): Either[String, A] =
    try
      val reader = CSVReader.open(path)
      try
        val it = reader.iterator
        if !it.hasNext then Right(f(Nil, Iterator.empty))
        else
          val headers = it.next()
          val rows = it.map { cells => headers.zipAll(cells, "", "").toMap }
          Right(f(headers, rows))
      finally reader.close()
    catch case e: Exception => Left(s"cannot read CSV $path: ${e.getMessage}")

  /** Writes the header once, then calls `f` with a row writer; closes the file
    * after `f`.
    */
  def writeRows[A](path: String, headers: Seq[String])(
      f: ((cells: Seq[String]) => Unit) => A
  ): Either[String, A] =
    try
      val writer = CSVWriter.open(path)
      try
        writer.writeRow(headers)
        val rowWriter = (fields: Seq[String]) => writer.writeRow(fields)
        Right(f(rowWriter))
      finally writer.close()
    catch case e: Exception => Left(s"cannot write CSV $path: ${e.getMessage}")

  def read(path: String): Either[String, CsvTable] =
    try
      val reader = CSVReader.open(path)
      try
        val lines = reader.iterator.toList
        lines match
          case Nil                   => Right(CsvTable(Nil, Nil))
          case headerRow :: dataRows =>
            val headers = headerRow
            val rows = dataRows.map { cells =>
              headers.zipAll(cells, "", "").toMap
            }
            Right(CsvTable(headers, rows))
      finally reader.close()
    catch case e: Exception => Left(s"cannot read CSV $path: ${e.getMessage}")

  def write(
      path: String,
      headers: Seq[String],
      rows: Seq[Seq[String]]
  ): Either[String, Unit] =
    try
      val writer = CSVWriter.open(path)
      try
        writer.writeRow(headers)
        rows.foreach(writer.writeRow(_))
        Right(())
      finally writer.close()
    catch case e: Exception => Left(s"cannot write CSV $path: ${e.getMessage}")

  def rowToState(row: Map[String, String]): io.github.ticofab.jev.Content =
    val fields = row.toSeq.sortBy(_._1).map { case (k, v) => k -> ujson.Str(v) }
    io.github.ticofab.jev.Content.obj(fields*)

  def rowValuesInOrder(
      headers: Seq[String],
      row: Map[String, String]
  ): Seq[String] =
    headers.map(h => row.getOrElse(h, ""))
