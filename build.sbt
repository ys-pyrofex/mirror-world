lazy val compilerOptions = Seq(
  "-Xfatal-warnings",
  "-Xfuture",
  "-Xlint:-unused",
  "-Yno-adapted-args",
  "-Ypartial-unification",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:_",
  "-unchecked"
)

lazy val wartremoverOptions = Seq(
  "Any",
  "ArrayEquals",
  "AsInstanceOf",
  "DefaultArguments",
  "EitherProjectionPartial",
  "Enumeration",
  "Equals",
  "ExplicitImplicitTypes",
  "FinalCaseClass",
  "FinalVal",
  "ImplicitConversion",
  "IsInstanceOf",
  "JavaConversions",
  "LeakingSealed",
  "MutableDataStructures",
  "NonUnitStatements",
  "Nothing",
  "Null",
  "Option2Iterable",
  "OptionPartial",
  "Overloading",
  "PublicInference",
  "Product",
  "Return",
  "Serializable",
  "StringPlusAny",
  "Throw",
  "ToString",
  "TraversableOps",
  "TryPartial",
  "Var",
  "While"
).map((s: String) => "-P:wartremover:traverser:org.wartremover.warts." + s)

lazy val nonConsoleOptions =
  Set("-Ywarn-unused-import", "-Xfatal-warnings") ++ wartremoverOptions.toSet

lazy val commonSettings = Seq(
  scalaVersion := "2.12.4",
  scalacOptions ++= compilerOptions ++ wartremoverOptions,
  scalacOptions in (Compile, console) ~= { _.filterNot(nonConsoleOptions) },
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value,
  organization := "coop.rchain",
)

lazy val commonSettingsDependencies = Seq(
  libraryDependencies ++= Seq(
    compilerPlugin("org.wartremover" %% "wartremover" % "2.1.1"),
    "ch.qos.logback"  % "logback-classic" % "1.2.3",
    "com.novocode"    % "junit-interface" % "0.11" % "test",
    "org.log4s"      %% "log4s"           % "1.4.0",
    "org.scalatest"  %% "scalatest"       % "3.0.4" % "test",
    "org.typelevel"  %% "cats-core"       % "1.0.1"
  )
)

lazy val shitheap = (project in file("."))
  .settings(commonSettings: _*)
  .settings(commonSettingsDependencies: _*)
  .settings(name := "shitheap")
