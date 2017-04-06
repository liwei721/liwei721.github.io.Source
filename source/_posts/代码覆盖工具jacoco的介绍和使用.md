---
title: 浅谈代码覆盖率
date: 2017-03-02 11:22:25
tags: javacoco, 代码覆盖率
categories: 测试工具
---

## 背景
- 我们平时写自动化，或者功能测试，最理想的状态是测完之后能自信的告诉开发，我们测试没问题了，可以发布了。
- 代码覆盖率就是这样一个工具，可以帮助我们了解我们的测试现状，对源代码的覆盖程度。
- 但是需要明确代码覆盖率本身对 产品质量 是没有意义的。并不能说覆盖率高，产品质量就高。
![代码覆盖率](/upload/image/zlw/代码覆盖率.png)

## 基本概念
### 代码覆盖率
- 代码覆盖率我的理解是一种度量方式，用来度量测试代码对源码逻辑的覆盖程度。作为对测试用例的一个补充、衡量和完善。

### 代码覆盖率统计方式
- 行覆盖率：度量被测程序的每行代码是否被执行，判断标准行中是否至少有一个指令被执行。
- 类覆盖率：度量计算class类文件是否被执行。
- 分支覆盖率：度量if和switch语句的分支覆盖情况，计算一个方法里面的总分支数，确定执行和不执行的 分支数量。
- 方法覆盖率：度量被测程序的方法执行情况，是否执行取决于方法中是否有至少一个指令被执行。
- 圈复杂度：又称断言覆盖(PredicateCoverage)。它度量了是否函数的每一个分支都被执行了。 这句话也非常好理解，就是所有可能的分支都执行一遍，有多个分支嵌套时，需要对多个分支进行排列组合，可想而知，测试路径随着分支的数量指数级别增加。
- 关于这几种代码覆盖率方式详细介绍[请点我](http://www.cnblogs.com/coderzh/archive/2009/03/29/1424344.html)，这篇文章中介绍了代码覆盖率的概念、策略使用优先级以及对使用代码覆盖率的建议，可以认真看看。

### 代码覆盖率意义
- 分析未覆盖部分的代码，从而反推在前期测试设计是否充分，没有覆盖到的代码是否是测试设计的盲点，为什么没有考虑到？需求/设计不够清晰，测试设计的理解有误，工程方法应用后的造成的策略性放弃等等，之后进行补充测试用例设计。
- 检测出程序中的废代码，可以逆向反推在代码设计中思维混乱点，提醒设计/开发人员理清代码逻辑关系，提升代码质量。
- 代码覆盖率高不能说明代码质量高，但是反过来看，代码覆盖率低，代码质量不会高到哪里去，可以作为测试自我审视的重要工具之一。

## 工具
- 主流的测试工具有Jacoco、Emma，这俩工具是同一个团队做的，不过Emma已经不再维护了。下图是常用工具的一些对比：
![代码覆盖率](/upload/image/zlw/codeConvery.png)
- 由于jacoco的优点，推荐使用jacoco做代码覆盖率检查。下面我们花点时间介绍下Jacoco。

### Jacoco简介
- JaCoCo是一个开源的覆盖率工具(官网地址：http://www.eclemma.org/JaCoCo/)，它针对的开发语言是java，其使用方法很灵活，可以嵌入到Ant、Maven中；可以作为Eclipse插件，可以使用其JavaAgent技术监控Java程序等等。
- 此外JaCoCo还可以集成到Jenkins中做持续集成。

### Jacoco等工具的工作原理
![工作原理](/upload/image/zlw/覆盖率工具工作流程.png)
1. 对Java字节码进行插桩（插入用于统计信息的标志），On-The-Fly（需要使用agent代理）和Offine（需要源码，测试之前插桩）两种方式。
2. 执行测试用例，收集程序执行轨迹信息，将其dump到内存。
3. 数据处理器结合程序执行轨迹信息和代码结构信息分析生成代码覆盖率报告。
4. 将代码覆盖率报告图形化展示出来，如html、xml等文件格式。

### JaCoCo插桩原理
![插桩原理](/upload/image/zlw/插桩原理.jpg)
- 这个图包含了几种不同的收集覆盖率信息的方法，每种方法的实现方法都不一样，带颜色的部分是JaCoCo比较有特色的地方。
- 主流代码覆盖率工具都采用字节码插桩模式，通过钩子的方式来记录代码执行轨迹信息。其中字节码插桩又分为两种模式On-The-Fly和Offine。On-The-Fly模式优点在于无需修改源代码，通过代理或者ClassLoader装载类的时候，判断是否已经插入了用于计数的探针，没有则插入，因此它可以实时获取覆盖率。Offline对应的优点是不需要开启对应的代理程序或者自定义ClassLoader，它需要程序运行完成之后

#### On-The-Fly
- On-the-Fly模式优点在于无需修改源代码，通过代理或者ClassLoader装载类的时候，判断是否已经插入了用于计数的探针，没有则插入，因此它可以实时获取覆盖率。

#### Offline
- Offline模式的优点是无需再额外的搞个代理程序或者自定义ClassLoader。
- 它需要在测试之前先对文件进行插桩，生成插过桩的class文件或者jar包，执行插过桩的class文件或者jar包之后，会生成覆盖率信息到文件，最后统一对覆盖率信息进行处理，并生成报告。
- offline模式插桩又分为两种：
>1. Replace:修改字节码生成新的class。
>2. InJect: 在原有的class上面修改。

- 更详细的可以参考腾讯tmq写的系列文章[JAVA代码覆盖率工具JaCoCo-原理篇](http://tmq.qq.com/2016/08/java-code-coverage-tools-jacoco-principle/),它里面详细介绍了根据代码逻辑type，怎么埋标记探针。当然也可以找源码看看，不过我也没看源码呢。

### JaCoCo使用
- JaCoco 提供了很多使用方式，比如：Ant、命令行、Maven、gradle、jenkins集成。
- 我比较关心的方式是（主要针对Android项目）：Jacoco和UI自动化结合、Jacoco和手动测试结合、Jacoco与Jenkins的集成。
- 其实gradle中android插件已经支持了jacoco的使用，因此我们只需要在app build.gradle中配置 jaCoco 以及在buildTypes中配置testCoverageEnabled=true,如下是我写的一个demo：

``` java
apply plugin: 'com.android.application'
apply plugin: 'jacoco'

android {
    compileSdkVersion 25
    buildToolsVersion "25.0.0"
    defaultConfig {
        applicationId "xdja.com.dreamcode"
        minSdkVersion 15
        targetSdkVersion 25
        versionCode 1
        versionName "1.0"
        testInstrumentationRunner "android.support.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }

        debug{
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
            zipAlignEnabled true
            testCoverageEnabled = true
        }
    }
}

def coverageSourceDirs = [
        '../app/src/main/java'
]
task jacocoTestReport(type: JacocoReport) {
    group = "Reporting"
    description = "Generate Jacoco coverage reports after running tests."
    reports {
        xml.enabled = true
        html.enabled = true
    }
    classDirectories = fileTree(
            dir: './build/intermediates/classes/debug',
            excludes: ['**/R*.class',
                       '**/*$InjectAdapter.class',
                       '**/*$ModuleAdapter.class',
                       '**/*$ViewInjector*.class'
            ])
    sourceDirectories = files(coverageSourceDirs)
    executionData = files("$buildDir/outputs/code-coverage/connected/coverage.ec")

    doFirst {
        new File("$buildDir/intermediates/classes/").eachFileRecurse { file ->
            if (file.name.contains('$$')) {
                file.renameTo(file.path.replace('$$', '$'))
            }
        }
    }
}

dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
    androidTestCompile('com.android.support.test.espresso:espresso-core:2.2.2', {
        exclude group: 'com.android.support', module: 'support-annotations'
    })
    compile 'com.android.support:appcompat-v7:25.0.1'
    testCompile 'junit:junit:4.12'
}
```

- 上面jacocoTestReport 任务用于将采集的ec文件转成html文件。

### Jacoco和手动测试结合
- 与手动结合测试，思路是通过反射，获取收集的覆盖率数据：

``` java
out = new FileOutputStream(mCoverageFilePath.getPath(), true);
            Object agent = Class.forName("org.jacoco.agent.rt.RT")
                    .getMethod("getAgent")
                    .invoke(null);
            out.write((byte[]) agent.getClass().getMethod("getExecutionData", boolean.class)
                    .invoke(agent, false));
```
- 网上有人实现了一种方式是不修改源码的情况下，通过Instrumentation来启动个集成首页Activity的页面，然后当测试完成销毁页面的时候会去执行收集数据的代码。
- 不过实现方式上可以根据自己的需要来实现触发收集数据的场景，比如搞个广播、长按某个物理键等等。
- 生成的数据时ec格式，需要用到上面的jacocoTestReport task，转成html文件。生成的报告位于：.\app\build\reports\jacoco\jacocoTestReport目录

### Jacoco和UI自动化结合
- 首先需要了解gradle android plugin 一些内置的命令：

列名 | 描述
---- | ---- :|
connectedAndroidTest | 执行android的case
createDebugCoverageReport | 产生代码覆盖率的报告
connectedCheck | 包含上面2个任务

- 只需要将UI自动化代码准备好，然后执行：

``` bash
$ gradlew clean createDebugCoverageReport
```
- 这个命令会执行UI自动化代码，同时生成jaCoCo报告。
- 生成的JaCoCo报告位于：\app\build\reports\coverage（在reports目录下还有单元测试的结果报告）。
- 这里再介绍个手动测试收集代码覆盖率的思路： 在UI自动化中，执行一个sleep（比如停10分钟），这10分钟内可以手动操作测试业务，然后10分钟之后，UI自动化结束后会自动生成报告，可以参考[Android手工测试的代码覆盖率](https://testerhome.com/topics/2510)

### Jacoco与Jenkins的集成
- Jenkins 集成Jacoco，其实其原理和上面提到的gradle的处理方式是比较类似的。
- 首先Jenkins需要安装一个【Jacoco plugin】，然后在项目配置文件中会有如下图所示的配置选项：
![Jenkins-Jacoco](/upload/image/zlw/jenkins-jacoco.png)

其主要配置如下：
- Path to Exec files：\app\build\outputs\code-coverage\connected\coverage.ec    这个路径是执行gradle任务时，生成的ec文件的目录。
- Path to class directories:   \app\build\intermediates\classes\debug           这个路径是编译之后生成class所在的目录
- Path to source directories :  /src/main/java            源代码所在路径
- Inclusions： com/xxx/xxx/ *.class       包含哪些class文件。
- Exclusions： **/R.class, **/R$*.class, **/Manifest.class , **/Manifest$*.class, **/BuildConfig.class
>- 这里需要注意，路径结尾的/必须去掉，否则会有非预期的结果，其实也就是加载了我们不需要的目录。

## 总结
- 代码覆盖率虽然对于项目质量不具有绝对的衡量意义，但是对于优化我们的测试用例及UI自动化脚本还是非常有用的。通过代码覆盖率结果，我们大致可以知道我们的UI自动化脚本对代码的覆盖程度。
- 因此应该推动代码覆盖率在项目中的应用。
