/*
 * MIT License
 *
 * Copyright (c) 2016 Ramjet Anvil
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.scalapenos.spray

import spray.json._

/**
 * A custom version of the Spray DefaultJsonProtocol with a modified field naming strategy
 */
trait LowercaseSprayJsonSupport extends DefaultJsonProtocol {
  import reflect._

  /**
   * This is the most important piece of code in this object!
   * It overrides the default naming scheme used by spray-json and replaces it with a scheme that turns camelcased
   * names into snakified names (i.e. using underscores as word separators).
   */
  override protected def extractFieldNames(classTag: ClassTag[_]) = {
    super.extractFieldNames(classTag).map(toLowercase)
  }

  private val toLowercase: String => String = {
    import java.util.Locale

    val Pass1 = """([A-Z]+)([A-Z][a-z])""".r
    val Pass2 = """([a-z\d])([A-Z])""".r
    val Replacement = "$1$2"

    name => Pass2.replaceAllIn(Pass1.replaceAllIn(name, Replacement), Replacement).toLowerCase(Locale.US)
  }
}

object LowercaseSprayJsonSupport extends SnakifiedSprayJsonSupport