---
title: Gradle实践问题及经验
date: 2017-03-20 19:58:27
tags: gradle经验
categories: gradle
---

## productFlavors
- 项目中一般都会用到ProductFlavors，实现比如不同的场景，包名不一样或者配置信息不一样等待。
- 举个例子，假如，我们的app有手机版（com.xx.xxx）和 pad版(com.xx.xxx.hd)两个版本，它们共用一份代码，那么在build.gradle中配置如下：
``` groovy
  productFlavors {
     Hd{
        applicationId "com.xx.xxx.hd"
     }
  }
  //我们可以通过gradle assembleHd命令来生成hd适配包。
```
- 再举个例子（美团的例子），假设有个需求，wandoujia市场默认禁止自动更新功能，可以这么写：
``` groovy
android {
    defaultConfig {
        buildConfigField "boolean", "AUTO_UPDATES", "true"
    }

    productFlavors {
        wandoujia {
            buildConfigField "boolean", "AUTO_UPDATES", "false"
        }        
    }

}

// 运行gradle assembleWandoujia命令生成  AUTO_UPDATES = false的包，然后代码中可以通过判断AUTO_UPDATES的值确定是否可以自动升级。

```
- Gradle会在generateSources阶段为flavors生成一个BuildConfig.java文件，BuildConfig类默认提供了一些常量字段，比如，应用的版本名（VERSION_NAME）、应用的包名（PACKAGE_NAME）等。上面的是自定义的一些字段。很强大吧。

## buildTypes
- 应用场景是：构建App的内测版和正式版，让他们能够安装到同一个手机上。
``` groovy
android{
  buildTypes{
    release{

    }

    debug{
      applicationIdSuffix '.debug'
    }
  }
}
```
- 不过需要考虑到自定义view在布局文件中的包名、其他用到包名的地方也要做修改。

## 依赖更新
- 手动设置 changing 标记为true，项目依赖的远程包如果有更新，会有提醒或者自动更新。gradle会每24小时检查更新，通过更改resolutionStrategy可以修改检查周期。
``` groovy
configurations.all {
    // check for updates every build
    resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
}
dependencies {
    compile group: "group", name: "projectA", version: "1.1-SNAPSHOT", changing: true
}
```

## 上传aar到Maven仓库
- 在工程的build.gradle中添加以下脚本：
``` groovy
apply plugin: 'maven'
uploadArchives {
    repositories {
        mavenDeployer {
            pom.groupId = GROUP_ID
            pom.artifactId = ARTIFACT_ID
            pom.version = VERSION
            repository(url: RELEASE_REPOSITORY_URL) {
                authentication(userName: USERNAME, password: PASSWORD)
            }
        }
    }
}
```
- 在build.gradle同目录下添加gradle.properties文件，配置如下：
``` properties
GROUP_ID=dianping.android.nova.thirdparty
ARTIFACT_ID=zxing
VERSION=1.0
RELEASE_REPOSITORY_URL=http://mvn.dp.com/nova
USERNAME=hello
PASSWORD=hello
```
- 最后执行 gradle :Zxing:uploadArchives

## 取消任务
- 项目构建过程中，有些任务不需要，可以直接关掉，在build.gradle中加入如下脚本：
``` groovy
tasks.whenTaskAdded { task ->
    if (task.name.contains('AndroidTest')) {
        task.enabled = false
    }
}
```
- tasks 会获取当前project中所有的task，enabled属性控制任务开关，whenTaskAdded后面的闭包会在gradle配置阶段完成。

## 加入任务
- 上面提到取消任务，那么也可以添加任务，比如，想在执行dex打包之前，加入一个hello任务，可以这么写：
``` groovy
afterEvaluate {
    android.applicationVariants.each { variant ->
        def dx = tasks.findByName("dex${variant.name.capitalize()}")
        def hello = "hello${variant.name.capitalize()}"
        task(hello) << {
            println "hello"
        }
        tasks.findByName(hello).dependsOn dx.taskDependencies.getDependencies(dx)
        dx.dependsOn tasks.findByName(hello)
    }
}
```
- afterEvaluate可以理解在配置阶段结束时，会走到这一步。variant = productFlavors  + buildTyps。
- dx.taskDependencies.getDependencies(dx)会获取dx任务的所有依赖，让hello任务以来dx任务的所有依赖，再让dx依赖hello任务，这样就可以加入某个任务到构建流程了。

## 任务监听
- 通过hook方式，我们可以监听一个task执行过程以及执行时间，如下所示：
``` groovy
class TimingsListener implements TaskExecutionListener, BuildListener {
    private Clock clock
    private timings = []

    @Override
    void beforeExecute(Task task) {
        clock = new org.gradle.util.Clock()
    }

    @Override
    void afterExecute(Task task, TaskState taskState) {
        def ms = clock.timeInMs
        timings.add([ms, task.path])
        task.project.logger.warn "${task.path} took ${ms}ms"
    }

    @Override
    void buildFinished(BuildResult result) {
        println "Task timings:"
        for (timing in timings) {
            if (timing[0] >= 50) {
                printf "%7sms  %s\n", timing
            }
        }
    }

    @Override
    void buildStarted(Gradle gradle) {}

    @Override
    void projectsEvaluated(Gradle gradle) {}

    @Override
    void projectsLoaded(Gradle gradle) {}

    @Override
    void settingsEvaluated(Settings settings) {}
}

gradle.addListener new TimingsListener()

```

## buildscript方法
- Android项目中，跟工程默认的build.gradle如下：
``` gradle
// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:1.2.3'
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
```
- buildscript方法的作用是配置脚本的依赖，而我们平常用的compile是配置project的依赖，repositories的意思是需要包的时候来我这里拿。
- 这里的com.android.tools.build:gradle:1.2.3是从C:\Program Files\Android\Android Studio\gradle\m2repository  下面取的，因此如果这里没有其他依赖（除了Android 插件相关），去掉jcenter也是可以的。

## 引入脚本
- 脚本写多了之后，都放到build.gradle中会显得很乱，可以将相关的一些脚本抽离除去，放到一个新的文件中，比如other.gradle,然后在build.gradle中只需要apply from 'other.gradle'即可。
- 但是需要注意，根工程中配置的buildscript会传递到所有工程，但只会传到build.gradle脚本中，所以你需要在other.gradle中重新配置buildscript,并且other.gradle中的repositories不再包含m2repository目录，自己配置jcenter()又会导致依赖重新下载到~/.gradle/caches目录。如果不想额外下载，也可以在other.gradle中这么搞：
``` groovy
buildscript {
    repositories {
        maven {
            url rootProject.buildscript.repositories[0].getUrl()
        }
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:1.2.3'
    }
}
```

## 获取AndroidManifest文件
- radle中的applicationid用来区分应用，manifest中packageName用来指定R文件包名，并且各个productFlavor 的manifest中的packageName应该一致。applicationid只是gradle脚本中的定义，其实最后生成的apk中的manifest文件的packageName还是会被applicationid替换掉。
- 那获取R文件的包名怎么搞？要获取AndroidManifest中package属性，并且这个manifest要是起始的文件，因为最终文件中的package属性会被applicationid冲掉，由于各个manifest中的package属性一样，并且非主manifest可以没有package属性，所以只有获取主manifest的package属性才是最准确的。
``` groovy
def manifestFile = android.sourceSets.main.manifest.srcFile
def packageName = new XmlParser().parse(manifestFile).attribute('package')
```

## 无用资源
- 无用的资源就不要打包进APK了
``` groovy
Resource Shrinking
```
