package io.github.aboisvert.jevvy.cli

object BatchPaths:
  def stem(input: String): String =
    val lower = input.toLowerCase
    // Strip extension case-insensitively; path casing is preserved in the stem string.
    if lower.endsWith(".csv") then input.dropRight(4) else input

  def defaultConfig(input: String): String = s"${stem(input)}.yaml"

  def defaultOutput(input: String): String = s"${stem(input)}-out.csv"

  def resolve(
    input: String,
    config: Option[String],
    output: Option[String]
  ): (String, String, String) =
    (
      config.getOrElse(defaultConfig(input)),
      input,
      output.getOrElse(defaultOutput(input))
    )
