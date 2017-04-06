---
title: 初识gradle（gradle系列一）
date: 2017-03-07 18:20:18
tags: gradle简介，gradle
categories: gradle
---
## 背景
- 大家对Ant和Maven一定不陌生，Ant是2000年发布，它基于程序编程思想的发展，但它主要缺点是使用XML作为一种格式来写构建脚本，XML是分层的，不利于程序的编程，而且当XML文件变大之后维护变的困难。
- Maven在2004年推出，比Ant有很大的改进，它改变了结构并且继续使用XML编写生成规范，它的依赖约定能通过网络下载依赖关系（这是特点）。但它的缺点是它不会处理同一库版本之间的冲突，另外复杂的定制构建脚本实际上比Ant更加难写。
- Gradle于2012年发布，是一个基于Ant和Maven概念的项目自动化构建工具，它使用一种基于Groovy的特定领域语言（DSL）来声明项目配置，抛弃了繁琐的XML配置。它面向java应用为主，当前支持的语言：java、Groovy、Scala，未来可能会支持更多的语言。
- Groovy语言是一种基于JVM的动态语言。

### gradle的功能特点
- 声明式构建和合约构建
>1. Gradle通过提供随意集成的声明式语言将声明语言推到一个新高度，这些元素也为java、Groovy、OSGI、Web和Scala等项目提供基于合约构建的支持，扩展性很好。

- 基于依赖的编程语言
- 让构建结构化
- API深化
- gradle扩展
- 多项目构建
- 多种方式来管理依赖
>1. 从远程的maven和ivy库的依赖管理到本地文件系统的jars或者dirs都支持。

- Gradle是第一个构建整合工具
>1. 它整合了Ant和Maven的一些特点和概念。

- 易于迁移
- 基于Groovy编写。
- Gradle包装器
>1. gradle包装器允许你在没有安装Gradle的机器上运行Gradle构建。这非常方便，可以降低使用项目的门槛。

- 免费和开源

## 安装Gradle
### 环境准备
- gradle运行在一个java环境里。因此需要安装java环境，且版本需要在6以上。

### 下载与安装
- 从[gradle网站](https://gradle.org/install)下载gradle zip文件。注意gradle的版本，最新的应该是3.x了。
- 解压缩之后，将gradle配置到环境变量中（GRADLE_HOME=解压根目录，GRADLE_HOME\bin配置到PATH路径中）
- 配置完成后，打开cmd命令行，输入gradle -v验证配置是否成功。
![gradle配置](/upload/image/zlw/gradleV.PNG)

### JVM选项
- JAVA_OPTS是一个用于JAVA应用的环境变量，一个典型的用例是在JAVA_OPTS里设置HTTP代理服务器（proxy）
- GRADLE_OPTS是内存选项，这些变量可以在gradle开始设置或者通过Gradlew脚本来设置。

## gradle概念
### Project && tasks
- 在gradle中有两个重要的概念，分别是project和tasks。每一次构建都是有至少一个project来完成，所以AndroidStudio中的project和Gradle中的project不是一个概念。每个project有至少一个tasks，每一个build.gradle文件代表一个gradle，tasks在build.gradle中定义，当初始化构建进程，gradle会基于build文件，集合所有的project和tasks，一个task包含了一系列动作，然后他们将会按照顺序执行，一个动作就是一段被执行的代码，很像java中的方法。

### 构建的生命周期
一旦一个task被执行，那么它不会再次执行了，不包含依赖的tasks总是优先执行，一次构建会经历下面三个阶段：
- 初始化阶段：project实例在这儿创建，如果有多个模块，即有多个build.gradle文件，多个project将会被创建。
- 配置阶段：在该阶段，build.gradle脚本将会执行，为每个project创建和配置所有的tasks。
- 执行阶段：这一阶段，gradle会决定哪一个tasks会被执行，哪一个tasks会被完全依赖开始构建时传入的参数和当前所在的文件夹位置有关。

### build.gradle的配置文件
- 我们来看看Android的build.gradle
``` java
buildscript {
   repositories {
        jcenter()
   }
   dependencies {
       classpath 'com.android.tools.build:gradle:1.2.3'
 }
}
```
- 这个是构建实际开始的地方，在仓库地址中使用了jcenter , jcenter 类似于Maven库，不需要任何额外的配置，gradle还支持其他几个仓库，不论是远程还是本地仓库。

### Gradle Wrapper
- gradle只是一个构建工具，而新版本总是在更迭，所以使用Gradle Wrapper将会是一个好的选择去避免由于gradle版本更新导致的问题，Gradle Wrapper提供了一个windows的batch文件和其他系统的shell文件，当你使用这些脚本的时候，当前gradle版本将会被下载，并且会自动运行到项目构建。windows上运行gradlew.bat, mac上运行gradlew。
``` java
myapp/
  ├── gradlew
  ├── gradlew.bat
  └── gradle/wrapper/
      ├── gradle-wrapper.jar
      └── gradle-wrapper.properties
```
- 配置文件的内容如下：
``` java
#Sat May 30 17:41:49 CEST 2015
   distributionBase=GRADLE_USER_HOME
   distributionPath=wrapper/dists
   zipStoreBase=GRADLE_USER_HOME
   zipStorePath=wrapper/dists
   distributionUrl=https\://services.gradle.org/distributions/
   gradle-2.4-all.zip
```

### 使用基本的构建命令
- 查看可以运行的tasks
``` bash
$ gradlew tasks （--all）    加上-all参数来查看所有的task
```
- 构建项目
``` bash
$ gradlew assembleDebug   构建一个debug版本的app
除了assemble，还有三个基本命令：
1. check运行所有的checks，换句话说是运行所有的tests在已连接的设备或者模拟器上。
2. build 是check和assemble的集合体。
3. clean 是清除项目的outputs文件。
```
