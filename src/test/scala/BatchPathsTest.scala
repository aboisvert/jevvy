import cli.BatchPaths
import munit.FunSuite

class BatchPathsTest extends FunSuite:

  test("default paths from csv input"):
    assertEquals(BatchPaths.defaultConfig("examples/foo.csv"), "examples/foo.yaml")
    assertEquals(BatchPaths.defaultOutput("examples/foo.csv"), "examples/foo-out.csv")

  test("strips .csv case-insensitively"):
    assertEquals(BatchPaths.stem("FOO.CSV"), "FOO")
    assertEquals(BatchPaths.defaultConfig("FOO.CSV"), "FOO.yaml")
    assertEquals(BatchPaths.defaultOutput("FOO.CSV"), "FOO-out.csv")

  test("no csv suffix uses full path as stem"):
    assertEquals(BatchPaths.defaultConfig("data"), "data.yaml")
    assertEquals(BatchPaths.defaultOutput("data"), "data-out.csv")

  test("resolve preserves explicit overrides"):
    val (cfg, inp, out) =
      BatchPaths.resolve("examples/foo.csv", Some("custom.yaml"), Some("/tmp/out.csv"))
    assertEquals(cfg, "custom.yaml")
    assertEquals(inp, "examples/foo.csv")
    assertEquals(out, "/tmp/out.csv")

  test("resolve applies defaults when options empty"):
    val (cfg, inp, out) = BatchPaths.resolve("examples/foo.csv", None, None)
    assertEquals(cfg, "examples/foo.yaml")
    assertEquals(inp, "examples/foo.csv")
    assertEquals(out, "examples/foo-out.csv")
