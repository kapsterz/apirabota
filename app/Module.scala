import actors._
import com.google.inject.AbstractModule
import play.api.{Configuration, Environment}
import play.api.libs.concurrent.AkkaGuiceSupport

/**
  * Created by Garapko Robert on 14.06.2016.
  */
class Module(environment: Environment, configuration: Configuration) extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    println("init bindings")
//    bindActor[FileParserActor]("file-parser-actor")
    println("finish bindings")
  }
}
