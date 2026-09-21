package io.github.ticofab.jev

import internal.Codec

/** Test-only access to SDK response parsing (Answers is private[jev]). */
object JevResponseFixtures:

  def parse(body: String, questions: Seq[Question]): Either[JevError, JevResponse] =
    Codec.parseResponse(body, questions)

  def bodyForNoul(name: String, probability: Double): String =
    s"""{"model":"test","answers":{"$name":{"noul":$probability}}}"""

  def bodyForChoice(name: String, choice: String, probs: (String, Double)*): String =
    val probFields = probs.map { case (k, v) => s""""$k":$v""" }.mkString(",")
    s"""{"model":"test","answers":{"$name":{"choice":"$choice","probabilities":{$probFields},"confidence":0.9}}}"""

  def bodyForScore(name: String, score: Double, levelProbs: (Int, Double)*): String =
    val probFields = levelProbs.map { case (k, v) => s""""$k":$v""" }.mkString(",")
    s"""{"model":"test","answers":{"$name":{"score":$score,"probabilities":{$probFields},"confidence":0.85}}}"""

  def bodyCombined(answersJson: String): String =
    s"""{"model":"test","answers":{$answersJson}}"""
