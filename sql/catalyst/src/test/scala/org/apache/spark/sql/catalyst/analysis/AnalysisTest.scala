/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.catalyst.analysis

import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.catalyst.SimpleCatalystConf
import org.apache.spark.sql.catalyst.catalog.{InMemoryCatalog, SessionCatalog}
import org.apache.spark.sql.catalyst.plans.PlanTest
import org.apache.spark.sql.catalyst.plans.logical._

trait AnalysisTest extends PlanTest {

  protected val caseSensitiveAnalyzer = makeAnalyzer(caseSensitive = true)
  protected val caseInsensitiveAnalyzer = makeAnalyzer(caseSensitive = false)

  private def makeAnalyzer(caseSensitive: Boolean): Analyzer = {
    val conf = new SimpleCatalystConf(caseSensitive)
    val catalog = new SessionCatalog(new InMemoryCatalog, EmptyFunctionRegistry, conf)
    catalog.createTempTable("TaBlE", TestRelations.testRelation, overrideIfExists = true)
    new Analyzer(catalog, conf) {
      override val extendedResolutionRules = EliminateSubqueryAliases :: Nil
    }
  }

  protected def getAnalyzer(caseSensitive: Boolean) = {
    if (caseSensitive) caseSensitiveAnalyzer else caseInsensitiveAnalyzer
  }

  protected def checkAnalysis(
      inputPlan: LogicalPlan,
      expectedPlan: LogicalPlan,
      caseSensitive: Boolean = true): Unit = {
    val analyzer = getAnalyzer(caseSensitive)
    val actualPlan = analyzer.execute(inputPlan)
    analyzer.checkAnalysis(actualPlan)
    comparePlans(actualPlan, expectedPlan)
  }

  protected def assertAnalysisSuccess(
      inputPlan: LogicalPlan,
      caseSensitive: Boolean = true): Unit = {
    val analyzer = getAnalyzer(caseSensitive)
    val analysisAttempt = analyzer.execute(inputPlan)
    try analyzer.checkAnalysis(analysisAttempt) catch {
      case a: AnalysisException =>
        fail(
          s"""
            |Failed to Analyze Plan
            |$inputPlan
            |
            |Partial Analysis
            |$analysisAttempt
          """.stripMargin, a)
    }
  }

  protected def assertAnalysisError(
      inputPlan: LogicalPlan,
      expectedErrors: Seq[String],
      caseSensitive: Boolean = true): Unit = {
    val analyzer = getAnalyzer(caseSensitive)
    val e = intercept[AnalysisException] {
      analyzer.checkAnalysis(analyzer.execute(inputPlan))
    }

    if (!expectedErrors.map(_.toLowerCase).forall(e.getMessage.toLowerCase.contains)) {
      fail(
        s"""Exception message should contain the following substrings:
           |
           |  ${expectedErrors.mkString("\n  ")}
           |
           |Actual exception message:
           |
           |  ${e.getMessage}
         """.stripMargin)
    }
  }
}
