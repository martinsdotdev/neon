package neon.core

import scala.collection.mutable

/** Records repository interactions across a set of in-memory async repositories, so suites can
  * assert cross-repository ordering (e.g. task saved before stock, stock before shortpick) and
  * that conditional loads were skipped. Share one instance across all repositories in a suite.
  */
final class CallRecorder:
  val entries: mutable.ListBuffer[String] = mutable.ListBuffer.empty
  def record(entry: String): Unit = entries += entry
  def saves: List[String] = entries.filter(_.endsWith(".save")).toList
  def contains(entry: String): Boolean = entries.contains(entry)
