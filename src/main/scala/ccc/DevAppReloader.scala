package ccc

/**
 * Main application used during developement for hot reloading of the application using classloaders magic.
 */
import com.sun.javafx.application.ParametersImpl
import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{ Files , StandardWatchEventKinds, FileVisitor, FileVisitResult, Path, Paths , WatchEvent }
import javafx.application.Application
import javafx.application.Platform
import scala.collection.JavaConverters._

object DevAppReloader {
  def main(args: Array[String]) = {
    System.setProperty("prism.lcdtext", "false")
    System.setProperty("prism.text", "t2k")
    Application.launch(classOf[DevAppReloader], args:_*)
  }
}
class DevAppReloader extends Application {

  override def init(): Unit = {
    //install a monitor on the classes to detect a change
    val classesDirectories = Array(Paths.get("target/scala-2.12/classes"), Paths.get("target/scala-2.12/test-classes")).filter(Files.exists(_))
    val fileWatcher = classesDirectories.head.getFileSystem.newWatchService
    classesDirectories foreach { classesDir =>
      Files.walkFileTree(classesDir, new FileVisitor[Path] {
          override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = {
            dir.register(fileWatcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY)
            FileVisitResult.CONTINUE
          }
          override def postVisitDirectory(dir: Path, excec: IOException) = {
            FileVisitResult.CONTINUE
          }
          override def visitFile(file: Path, attrs: BasicFileAttributes) = {
            FileVisitResult.CONTINUE
          }
          override def visitFileFailed(file: Path, excec: IOException) = {
            FileVisitResult.TERMINATE
          }
        })
      classesDir.register(fileWatcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY)
    }

    new Thread("ClassesChangesWatcher") {
      override def run(): Unit = {
        println("watching")
        var updateFound = false
        var lastUpdate = System.currentTimeMillis

        while(!isInterrupted()) {
          val now = System.currentTimeMillis
          val wk = fileWatcher.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
          if (wk != null) {
            wk.pollEvents.asScala foreach {
              case watchEvent: WatchEvent[Path @unchecked] =>
                val context = wk.watchable.asInstanceOf[Path].resolve(watchEvent.context)

                if (watchEvent.kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(context)) {
                  context.register(fileWatcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY)
                }
            }
            wk.reset()
            updateFound = true
            lastUpdate = now
          }
          if (updateFound && (now - lastUpdate) > 1000) { //if there was some time ago, trigger reloading
            updateFound = false
            reloadApp()
          }
        }
        println("watcher dying")
      }
    }.start()
  }

  @volatile var recompiling = false
  var lastApplication: Application = _
  def reloadApp(): Unit = {
    if (!recompiling) { // if I'm already recompiling, ignore the request. This might happen if the watcher thread detects many file changing in quick not not so quick intervals
      println(Console.CYAN + "RELOADING" + Console.RESET)
      recompiling = true

      Platform runLater new Runnable {
        def run: Unit = {
          //is there was an application, we need to dispose of it first
          if (lastApplication != null) {
            lastApplication.stop()
            primaryStage.setScene(null) //remove all references generated by the dynamically loaded class
            val cl = lastApplication.getClass.getClassLoader.asInstanceOf[URLClassLoader]
            System.gc()
            cl.close()
          }

          val loader = new URLClassLoader(Array(getClass.getResource("/"))) {
            //override default class loader behaviour to prioritize classes in this classloader
            override def loadClass(name: String, resolve: Boolean): Class[_] = {
              var res: Class[_] = findLoadedClass(name)
              val startTime = System.currentTimeMillis
              while (res == null && System.currentTimeMillis - startTime < 5000) {//will retry for an entire second for this class to appear
                try res = findClass(name)
                catch { case e: ClassNotFoundException =>
                    try res = super.loadClass(name, false)
                    catch { case e: ClassNotFoundException => Thread.sleep(50) } //sleep 50ms and retry
                }
              }
              if (res == null) throw new ClassNotFoundException(name)
              if (resolve) resolveClass(res)
              res
            }
          }

          lastApplication = loader.loadClass(getParameters.getRaw.get(0)).newInstance.asInstanceOf[Application]
          ParametersImpl.registerParameters(lastApplication, getParameters)
          lastApplication.init()
          lastApplication.start(primaryStage)
          recompiling = false
        }
      }
    }
  }

  var primaryStage: javafx.stage.Stage = _
  override def start(stage: javafx.stage.Stage): Unit = {
    primaryStage = stage
    reloadApp()
  }
}
