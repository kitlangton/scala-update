package dependencies

import zio._

//object Main extends ZIOAppDefault {
//  val run = {
//    for {
//      deps    <- ZIO.succeed(DependencyParser.getDependencies(DependencyParser.parsed))
//      options <- ZIO.foreach(deps)(UpgradeOptions.getOptions)
//      _       <- ZIO.debug(options.mkString("\n"))
//    } yield options
//  }.provide(VersionService.live)
//}
