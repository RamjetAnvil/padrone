package com.scalapenos.spray

import spray.json._

/**
 * A custom version of the Spray DefaultJsonProtocol with a modified field naming strategy
 */
trait SnakifiedSprayJsonSupport extends DefaultJsonProtocol {
  import reflect._

  /**
   * This is the most important piece of code in this object!
   * It overrides the default naming scheme used by spray-json and replaces it with a scheme that turns camelcased
   * names into snakified names (i.e. using underscores as word separators).
   */
  override protected def extractFieldNames(classTag: ClassTag[_]) = {
    super.extractFieldNames(classTag).map(snakify)
  }

  private val snakify: String => String = {
    import java.util.Locale

    val Pass1 = """([A-Z]+)([A-Z][a-z])""".r
    val Pass2 = """([a-z\d])([A-Z])""".r
    val Replacement = "$1_$2"

    name => Pass2.replaceAllIn(Pass1.replaceAllIn(name, Replacement), Replacement).toLowerCase(Locale.US)
  }
}

object SnakifiedSprayJsonSupport extends SnakifiedSprayJsonSupport