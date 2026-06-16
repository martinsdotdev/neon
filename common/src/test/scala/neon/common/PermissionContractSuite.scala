package neon.common

import org.scalatest.funspec.AnyFunSpec

import java.nio.file.{Files, Path, Paths}

/** Contract test for the TypeScript domain mirror: the `PERMISSION_KEYS` list in
  * `packages/domain/src/auth.ts` must contain exactly the keys of the [[Permission]] enum. Fails
  * the backend build when either side drifts.
  */
class PermissionContractSuite extends AnyFunSpec:

  private val keysBlockPattern = """(?s)PERMISSION_KEYS\s*=\s*\[(.*?)\]\s*as\s+const""".r
  private val keyPattern = """"([a-z-]+:[a-z-]+)"""".r

  private def repositoryRoot: Path =
    Iterator
      .iterate(Paths.get(sys.props("user.dir")).toAbsolutePath)(_.getParent)
      .takeWhile(_ != null)
      .find(path => Files.exists(path.resolve("pnpm-workspace.yaml")))
      .getOrElse(fail("could not locate the repository root (no pnpm-workspace.yaml upwards)"))

  describe("Permission mirror"):
    it("matches the TypeScript PERMISSION_KEYS in packages/domain"):
      val authFile = repositoryRoot
        .resolve("packages")
        .resolve("domain")
        .resolve("src")
        .resolve("auth.ts")
      assert(Files.exists(authFile), s"TypeScript mirror not found at $authFile")

      val source = Files.readString(authFile)
      val keysBlock = keysBlockPattern
        .findFirstMatchIn(source)
        .map(_.group(1))
        .getOrElse(fail("PERMISSION_KEYS block not found in auth.ts"))
      val typescriptKeys = keyPattern.findAllMatchIn(keysBlock).map(_.group(1)).toSet
      val scalaKeys = Permission.values.map(_.key).toSet

      val missingInTypescript = scalaKeys -- typescriptKeys
      val missingInScala = typescriptKeys -- scalaKeys
      assert(
        typescriptKeys == scalaKeys,
        s"permission mirror drift — missing in TypeScript: $missingInTypescript, " +
          s"missing in Scala: $missingInScala"
      )
