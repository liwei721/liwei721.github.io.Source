---
title: gradle构建过程(Android)（gradle 系列八）
date: 2017-03-20 16:45:21
tags: gradle构建过程，Android
categories: gradle
---
## 背景
- 这篇文章主要讲Gradle大概的工作流程和实现原理，并以部分源码分析佐证，其中包括project中配置数据什么时候获取，各个task的创建时机，如何自定义控制编译过程等。
- 接着分析编译过程中class到dex这一步，以及当初遇到的问题。
- 本文是参考[打通Android Gradle编译过程的任督二脉](http://chuansong.me/n/425965451447)
- 这篇文章写的有点乱，后续再做整理。

## 主要工作流程
### 初始化阶段
- 读取根工程中的setting.gradle中的include信息，确定有多少工程加入构建并创建project实例，每个工程中的build.gradle对应一个project实例。

### 配置阶段
- 根据每个工程目录下面的build.gradle，配置gradle对象，并构建好任务依赖有向图。

### 执行阶段
- 根据配置阶段拿到的配置信息和任务依赖有向图执行对应的task。
![gradle流程](/upload/image/zlw/gradle_task.jpg)

- 配置阶段和执行阶段的不同：
``` java
 // 一个project有若干的task，一个task有若干的action
 // 这里task在配置时执行
  task hello {
     print 'hello'}
// 这里task在执行到hello这个task时才会执行。doLast 和 doFirst区别，一个插入action list最前面，一个是插入最后面。
   task hello {
      doLast{
      print 'hello'
      }
   }
```

### hook方式
- 从上图中可以看出我们可以通过hook的方式来做一些操作，gradle里面有两个监听器接口：BuildListener 和 TaskExecutionListener

#### BuildListener
``` java
void buildStarted(Gradle gradle);
void settingsEvaluated(Settings settings);
void projectsLoaded(Gradle gradle);
void projectsEvaluated(Gradle gradle);
void buildFinished(BuildResult result);
```

#### TaskExecutionListener
``` java
void beforeExecute(Task task);
void afterExecute(Task task, TaskState state);
```
-

## 源码分析
- Gradle源码当中主要有这么几个类，VariantManager,TaskManager,AndroidBuilder,ConfigAction:
>- VariantManager负责收集对应的变量数据，如build.gradle中的一些基本配置变量可以在AndroidSourceSet类中查看
>- TaskManager负责管理task的创建和执行
>- AndroidBuilder负责具体执行Android构建的一些命令，如编译aidl，aapt，class转dex等。
>- ConfigAction负责task的具体表现行为，task是由若干Action组成的，gradle在创建每一个任务的时候会默认指定一个ConfigAction来指定task名字，输入输出等。

- gradle进程启动的时候，VariantManager初始化的时候会收集对应的variantData，然后根据这些信息首先创建默认的AndroidTask，然后调用对应的TaskManager继承类如ApplicationTaskManager的createTasksForvariantData创建相关的task，并构建依赖有向图，如下图所示：
![gradle流程](/upload/image/zlw/gradle_img.png)
- 在createTasksForvariantData函数中创建任务的方式如下：
``` java
ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_COMPILE_TASK,
          new Recorder.Block() {
       @Override
       public Void call () {
           AndroidTask javacTask = createJavacTask(tasks, variantScope);
           if (variantData.getVariantConfiguration().getUseJack()) {
               createJackTask(tasks, variantScope);
           } else {
               setJavaCompilerTask(javacTask, tasks, variantScope);
               createJarTask(tasks, variantScope);
               createPostCompilationTasks(tasks, variantScope);
           }
           return null;
       }
   });

// 具体创建task的方法
public synchronized AndroidTask create(
           TaskFactory taskFactory,
           String taskName,
           Closure configAction) {
       taskFactory.create(taskName, DefaultTask.class, new ClosureBackedAction(configAction));
       final AndroidTask newTask = new AndroidTask(taskName, Task.class);
       tasks.put(taskName, newTask);
       return newTask;
   }
```
- 上面ThreadRecorder保证task之间是串行的，另外在具体创建每一个任务的时候的时候可以看到都传了一个ConfigAction，这个可以默认指定该任务执行某些行为，如指定该任务的输入输出等，获取AndroidBuilder中的工具等。

## 打包过程
- 打包主要包含两个过程，首先是编译过程，编译的内容包括本工程的文件以及依赖的各种库文件，编译的输出包括dex文件和编译后的资源文件。其次是打包过程。配合Keystore对第一步的输出进行签名对齐，生成最终的apk文件。
![gradle流程](/upload/image/zlw/build_apk.png)

上图主要包含四步：
- Java编译器对工程本身的java代码进行编译，这些java代码有三个来源：app的源代码，由资源文件生成的R文件(aapt工具)，以及有aidl文件生成的java接口文件(aidl工具)。产出为.class文件。
- .class文件和依赖的三方库文件通过dex工具生成Delvik虚拟机可执行的.dex文件，可能有一个或多个，包含了所有的class信息，包括项目自身的class和依赖的class。产出为.dex文件。、
- apkbuilder工具将.dex文件和编译后的资源文件生成未经签名对齐的apk文件。这里编译后的资源文件包括两部分，一是由aapt编译产生的编译后的资源文件，二是依赖的三方库里的资源文件。产出为未经签名的.apk文件。
- 分别由Jarsigner和zipalign对apk文件进行签名和对齐，生成最终的apk文件。

## 加速Android/gradle构建
### 开启gradle单独的守护进程
- 在下面的目录下面创建gradle.properties文件,在文件中添加 org.gradle.daemon=true：
>1. /home/<username>/.gradle/ (Linux)
>2. /Users/<username>/.gradle/ (Mac)
>3. C:\Users\<username>\.gradle (Windows)

- 同时修改项目下的gradle.properties文件也可以优化：
``` property
# Project-wide Gradle settings.
# IDE (e.g. Android Studio) users:
# Settings specified in this file will override any Gradle settings
# configured through the IDE.
# For more details on how to configure your build environment visit
# http://www.gradle.org/docs/current/userguide/build_environment.html
# The Gradle daemon aims to improve the startup and execution time of Gradle.
# When set to true the Gradle daemon is to run the build.
# TODO: disable daemon on CI, since builds should be clean and reliable on servers
org.gradle.daemon=true
# Specifies the JVM arguments used for the daemon process.
# The setting is particularly useful for tweaking memory settings.
# Default value: -Xmx10248m -XX:MaxPermSize=256m
org.gradle.jvmargs=-Xmx2048m -XX:MaxPermSize=512m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8
# When configured, Gradle will run in incubating parallel mode.
# This option should only be used with decoupled projects. More details, visit
# http://www.gradle.org/docs/current/userguide/multi_project_builds.html#sec:decoupled_projects
org.gradle.parallel=true
# Enables new incubating mode that makes Gradle selective when configuring projects.
# Only relevant projects are configured which results in faster builds for large multi-projects.
# http://www.gradle.org/docs/current/userguide/multi_project_builds.html#sec:configuration_on_demand
org.gradle.configureondemand=true
```
- 这个配置文件也可以配置到上面在用户目录下面创建的gradle.properties里面，这样这个配置就适用于所有的项目
