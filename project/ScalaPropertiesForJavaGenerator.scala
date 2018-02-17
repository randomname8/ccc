import java.beans.IndexedPropertyDescriptor
import java.nio.file.{Path, Paths, Files}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

object ScalaPropertiesForJavaGenerator {

  def generate(destFile: Path, classpath: Array[Path], patterns: Seq[String]): Path = {
    java.nio.file.Files.createDirectories(destFile.getParent)

    val inheritedJars = Iterator.iterate(ClassLoader.getSystemClassLoader)(_.getParent).takeWhile(_ != null).flatMap {
      case ucl: java.net.URLClassLoader => ucl.getURLs.map(u => Paths.get(u.toURI))
      case _ => Iterator.empty
    }.filter(_.getFileName.toString.endsWith(".jar"))
    val fullClasspath = (inheritedJars ++ classpath).toArray
    
    val allClasses = fullClasspath.iterator.flatMap { p =>
      if (Files.isDirectory(p)) {
        listRecursively(p).map(f => p.relativize(f).toString)
      } else {
        try new java.util.jar.JarFile(p.toFile).stream.iterator.asScala.map(_.getName)
        catch { case NonFatal(ex) => throw new RuntimeException(s"Could not open $p", ex)}
      }
    }.filter(e => e.endsWith(".class") && !e.contains("$")).
      filter(e => patterns.exists(e.matches))
      
    val jmodClasses = if (util.Properties.javaVersion.startsWith("9")) {
      for {
        moduleRef <- java.lang.module.ModuleFinder.ofSystem().findAll().iterator.asScala
        reader = moduleRef.open
        clazz <- reader.list.iterator.asScala if clazz.endsWith(".class") && !clazz.contains("module-info") && !clazz.contains("$") && patterns.exists(clazz.matches)
      } yield clazz
    } else Iterator.empty

    val out = new java.io.PrintStream(destFile.toFile, "utf-8")

    var classpathClassLoader = new java.net.URLClassLoader(classpath.map(_.toUri.toURL).to[Array])
    val valueClasses = (for {
        entry <- (allClasses ++ jmodClasses)
        c = Class.forName(entry.replace("/", ".").replace(".class", ""), false, classpathClassLoader)
        beanInfo = java.beans.Introspector.getBeanInfo(c)
        properties = beanInfo.getPropertyDescriptors().filterNot(_.getName == "class").
            filterNot(_.isInstanceOf[IndexedPropertyDescriptor]).filter( prop =>
              Option(prop.getWriteMethod).getOrElse(prop.getReadMethod).getDeclaringClass == c)
        if properties.nonEmpty
      } yield {
        val methods = properties.flatMap { descriptor =>
          Option(descriptor.getReadMethod).map(rm => s"@inline def `${descriptor.getName}` = _v.${rm.getName}()") ++
          Option(descriptor.getWriteMethod).map { wm =>
            val valuePassing = if (wm.isVarArgs) "v:_*" else "v"
            s"@inline def ${descriptor.getName}_=(v: ${adaptType(descriptor.getWriteMethod.getGenericParameterTypes()(0))}) = _v.${wm.getName}($valuePassing)"
          }
        }
        val genericDecls = c.getTypeParameters() match {
          case arr if arr.isEmpty => ""
          case arr => arr.map(adaptType).mkString("[", ", ", "]")
        }
        val generics = c.getTypeParameters() match {
          case arr if arr.isEmpty => ""
          case arr => arr.map(_.getName).mkString("[", ", ", "]")
        }

        c.getPackage -> s"""
  implicit class ${c.getSimpleName}BeanDsl$genericDecls(val _v: ${c.getName}$generics) extends AnyVal {
    ${methods.mkString("\n    ")}
  }
"""
      }).toSeq.groupBy(_._1).mapValues(_.map(_._2))

    for ((pck, valueClasses) <- valueClasses) {
      val pkgParts = pck.getName.split("\\.")
      val parentPkg = pkgParts.take(pkgParts.length - 1).mkString(".")
      out.println(s"package $parentPkg { package object ${pkgParts.last} {\n" +
                  valueClasses.mkString("\n") + "}}")
    }

    java.beans.Introspector.flushCaches()
    System.gc()
    classpathClassLoader.close()
    classpathClassLoader = null
    System.gc()
    out.flush()
    out.close()

    destFile
  }
  
  val ObjectClass = classOf[Object]
  def adaptType(tpe: java.lang.reflect.Type): String = tpe match {
    case tpe: Class[_] =>
      if (tpe.isArray) {
        "Array[" + adaptType(tpe.getComponentType) + "]"
      } else if (tpe.isPrimitive) {
        tpe match {
          case java.lang.Byte.TYPE => "Byte"
          case java.lang.Short.TYPE => "Short"
          case java.lang.Integer.TYPE => "Int"
          case java.lang.Long.TYPE => "Long"
          case java.lang.Float.TYPE => "Float"
          case java.lang.Double.TYPE => "Double"
          case java.lang.Boolean.TYPE => "Boolean"
          case java.lang.Character.TYPE => "Char"
          case java.lang.Void.TYPE => "Unit"
        }
      } else {
        tpe.getTypeParameters match {
          case Array() => tpe.getCanonicalName
          case other => tpe.getCanonicalName + other.map(_ => "_").mkString("[", ", ", "]")
        }
      }
      
      //handle cases like SomeType.ThisType[ActualParam1, ActualParam2...]
    case tpe: java.lang.reflect.ParameterizedType => 
//      val owner = Option(tpe.getOwnerType).map(_.asInstanceOf[Class[_]]).map(_ + ".").getOrElse("")
      tpe.getRawType.asInstanceOf[Class[_]].getCanonicalName + tpe.getActualTypeArguments.map {
        case tv: java.lang.reflect.TypeVariable[_] => tv.getName
        case other => adaptType(other)
      }.mkString("[", ", ", "]")
      
      //handle wildcars like ? extends or ? super
    case tpe: java.lang.reflect.WildcardType =>
      val upper = tpe.getUpperBounds match {
        case Array(ObjectClass) | Array() => None
        case others => Some(" <: " + others.map(adaptType).mkString(" with "))
      }
      val lower = tpe.getLowerBounds match {
        case Array() => None
        case others => Some(" >: " + others.map(adaptType).mkString(" with "))
      }
      
      "_" + upper.getOrElse("") + lower.getOrElse("")
      
    case tpe: java.lang.reflect.TypeVariable[_] =>
      val bounds = tpe.getBounds match {
        case Array(ObjectClass) | Array() => ""
        case others => " <: " + others.map(adaptType).mkString(" with ")
      }
      tpe.getName + bounds
  }
  
  def listRecursively(p: Path): Iterator[Path] = 
    if (Files.isRegularFile(p)) Iterator(p)
    else Files.list(p).iterator.asScala.flatMap(listRecursively)
}
