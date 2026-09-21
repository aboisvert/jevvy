package io.github.aboisvert.jevvy.csv

import com.github.tototoshi.csv.*

final case class CsvTable(headers: Seq[String], rows: Seq[Map[String, String]])

object CsvIO:

  def read(path: String): Either[String, CsvTable] =
    try
      val reader = CSVReader.open(path)
      try
        val lines = reader.iterator.toList
        lines match
          case Nil => Right(CsvTable(Nil, Nil))
          case headerRow :: dataRows =>
            val headers = headerRow
            val rows = dataRows.map { cells =>
              headers.zipAll(cells, "", "").toMap
            }
            Right(CsvTable(headers, rows))
      finally reader.close()
    catch case e: Exception => Left(s"cannot read CSV $path: ${e.getMessage}")

  def write(path: String, headers: Seq[String], rows: Seq[Seq[String]]): Either[String, Unit] =
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

  def rowValuesInOrder(headers: Seq[String], row: Map[String, String]): Seq[String] =
    headers.map(h => row.getOrElse(h, ""))
