//> using scala 3.3.1
//> using dep com.lihaoyi::os-lib:0.9.3
import scala.util.Try

def eith[A](a: => A): Either[Throwable, A] = Try(a).toEither

val appDir = os.pwd / "app"

val buildTempDir = appDir / "build-destination-temp"

val s3destination = "s3://typelevel-tyrian-spa"

val out =
  for {
    jsLinkingOutDir <- eith {
      os.proc("sbt", "project app", "print fullLinkJSOutput").call().out.lines().last
    }

    pathToMainjs = os.Path(jsLinkingOutDir) / "main.js"

    // move js output to app dir, so that app.js can refer to it
    _ <- eith {
      os.move(
        from = pathToMainjs,
        to = appDir / "main.js",
        replaceExisting = true
      )
    }

    // package all of front end and place it in buildTempDir
    _ <- eith {
      os.proc(
        "npx",
        "parcel",
        "build",
        "index.html",
        "--dist-dir",
        buildTempDir.toString,
        "--log-level",
        "info",
        "--no-source-maps")
        .call(cwd = appDir)
    }

    // use buildTempDir contents to update the s3 bucket holding the frontend
    _ <- eith {
      os.proc(
        "aws",
        "s3",
        "sync",
        buildTempDir.toString,
        s3destination,
        "--delete"
      ).call()
    }

    _ <- eith {
      os.remove.all(buildTempDir)
    }

  } yield ()

out.left.foreach(_.printStackTrace())
