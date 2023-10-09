// See README.md for license details.

ThisBuild / scalaVersion     := "2.13.7"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "Kasper Hesse"

val chiselVersion = "5.0.0"

lazy val root = (project in file("."))
  .settings(
    name := "Risc-V Core",
    libraryDependencies ++= Seq(
      "org.chipsalliance" %% "chisel" % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest" % "5.0.0" % "test",
      "com.lihaoyi" %% "os-lib" % "0.9.1"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
    ),
    addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full),
  )

