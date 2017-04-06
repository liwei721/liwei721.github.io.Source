---
title: 初始build.gradle(gradle系列二)
date: 2017-03-08 10:38:01
tags: build.gradle
categories : gradle
---
## 理解Gradle脚本
- 当你新建一个AndroidStudio 工程时，会默认的创建三个gradle文件，一个setting.gradle, 两个build.gradle，他们的文件结构如下：
``` java
MyApp
  ├── build.gradle
  ├── settings.gradle
  └── app
      └── build.gradle
```

### setting.gradle
- 当你的app只有一个模块的时候，你的setting.gradle是这个样子：
``` java
include ':app'
```
- setting.gradle 文件将会在初始化时期执行，它定义了哪个模块会被构建，比如上面app模块会被构建。setting.gradle 是针对多模块操作的，所以单独的模块工程可以删除掉该文件。初始化后，Gradle会为我们创建一个Setting对象，并为其包含必要的方法。

### 根目录的build.gradle
- 该gradle文件是定义在这个工程下的所有模块的公共属性，它默认包含二个方法：
``` java
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
- buildscript方法是定义了全局的相关属性，repositories定义了jcenter作为仓库，一个仓库代表着你依赖包的来源。
- dependencies用来定义构建过程，你只需要定义默认的Android插件就可以了，因为该插件可以让你执行相关的Android的tasks。
- allprojects方法可以用来定义各个模块的默认属性，你可以不仅仅局限于默认的配置，你可以自己创建tasks在allprojects方法体内，这些tasks将会在所有模块中可见。

### 模块内的build.gradle
- 模块内的gradle文件只对该模块起作用，而且其可以重写任何来自于根目录gradle文件的参数，该模块文件如下所示：
``` java
apply plugin: 'com.android.application'
   android {
       compileSdkVersion 22
       buildToolsVersion "22.0.1"
       defaultConfig {
           applicationId "com.gradleforandroid.gettingstarted"
           minSdkVersion 14
           targetSdkVersion 22
           versionCode 1
           versionName "1.0"
       }
       buildTypes {
           release {
               minifyEnabled false
               proguardFiles getDefaultProguardFile
                ('proguard-android.txt'), 'proguard-rules.pro'
           }
        }
    }
    dependencies {
       compile fileTree(dir: 'libs', include: ['*.jar'])
       compile 'com.android.support:appcompat-v7:22.2.0'
     }
```
- 第一行是Android应用插件，是Google的Android开发团队编写的插件，可以提供所有Android应用和依赖库的构建，打包和测试。
- android括号包含了Android属性，而唯一必须的属性是：compileSdkVersion （编译app时使用的api版本）和 buildToolsVersion（Android构建工具的版本号），其中构建工具包含了很多使用的命令行命令，比如：aapt、zipalign、dx等。
- defaultConfig方法包含了该app的核心属性，该属性会重写AndroidMainfest.xml中的对应属性。
>1. applicationId ,该属性复写了AndroidManifest.xml文件中的包名packageName
>2.  minSdkVersion和targetSDKVersion，这两个和AndroidManifes中的<uses-sdk>很像。
>3. versionCode 将作为版本号标示，而versionName没有作用。

- buildTypes 方法定义了如何构建不同版本的app。
- dependencies 为app定义了所有的依赖包，默认情况下，会依赖所有libs文件下的jar文件，同时包含了AppCompat这个aar文件。

## 理解tasks
- 如果你想知道你有多少tasks可以用，直接运行gradlew tasks，其会为你展示所有可用的tasks，当你创建一个Android工程，那么将包含Android tasks、build tasks、build setup tasks、help tasks、install tasks、verification tasks等。

### 基本的tasks
- Android插件依赖于java插件，而java插件依赖于base插件。
- base插件有基本的tasks生命周期和一些通用的属性。
- base插件定义了例如assemble和clean任务，java插件定义了check和build任务，这两个任务不在base插件中定义。这些tasks的含义：
>1. assemble 集合所有的output
>2. clean   清除所有的output
>3. check   执行所有的checks检查，通常是unit测试和instrumentation测试。
>4. build   执行所有的assemble和check

- java插件同时也添加了source sets的概念。

### Android tasks
- Android插件继承了这些基本tasks并且实现了它自己的行为：
>1. assemble 针对每个版本创建一个apk
>2. clean    删除所有的构建任务，包含apk文件。
>3. check    执行lint检查并且能够在lint检查到错误后停止执行脚本。
>4. build    执行assemble和check

- 默认情况下assemble tasks定义了assembleDebug和assembleRelease，当然你还可以定义更多的构建版本，除了这些tasks，Android插件也提供了一些新的tasks：
>1. connectedCheck 在测试机上面执行所有测试任务。
>2. deviceCheck     执行所有的测试在远程设备上。
>3. installDebug 和 installRelease 在设备安装一个特殊的版本。
>4. 所有的install task 对应有uninstall 任务。

- build task依赖于check任务，但是不依赖于connectedCheck或者deviceCheck，执行check任务的使用的lint会产生一些相关文件，这些报告可以在/app/build/outputs/中查看。

### BuildConfig & resources
``` java
android {
    buildTypes {
        debug {
            buildConfigField "String", "API_URL",
               "\"http://test.example.com/api\""
            buildConfigField "boolean", "LOG_HTTP_CALLS", "true"
     }
       release {
            buildConfigField "String", "API_URL",
                "\"http://example.com/api\""
            buildConfigField "boolean", "LOG_HTTP_CALLS","false"
     }
 }
```
- 这样定义的常量，可以在代码中使用BuildConfig.API_URL和BuildConfig.LOG_HTTP

### 全局设置
- 如果你有很多模块在一个工程下，你可以这么定义你的project文件。
``` java
allprojects {
       apply plugin: 'com.android.application'
       android {
           compileSdkVersion 22
           buildToolsVersion "22.0.1"
       }
 }
```
- 这样只有你的模块是Android app应用的时候有效，你需要添加Android插件才能访问Android的tasks，更好的做法是在你全局的gradle文件中定义一些属性，然后在模块中运用他们，如下所示：
```java
ext {
      compileSdkVersion = 22
      buildToolsVersion = "22.0.1"
}  
```
这样你在子模块中就可以使用这些属性了：
```java
android {
       compileSdkVersion rootProject.ext.compileSdkVersion
       buildToolsVersion rootProject.ext.buildToolsVersion
 }
```

### Project properties
- 上面提到的ext只是一种办法，还有其他方法，比如：gradle properties 、-p参数
``` java
ext {
     local = 'Hello from build.gradle'
}
   task printProperties << {
     println local        // Local extra property
     println propertiesFile        // Property from file
     if (project.hasProperty('cmd')) {
       println cmd        // Command line property
     }
}
```
- 在gradle.properties中定义：
``` java
propertiesFile = Hello from gradle.properties
```
- 这样在命令行执行：
``` bash
$ gradlew printProperties -P cmd='Hello from the command line'
:printProperties
Hello from build.gradle
Hello from gradle.properties
Hello from the command line
```
- 上面就是三种定义gradle属性的方法。可以根据自己的需求来选择使用哪种方式。
