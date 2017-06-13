---
title: 静态代码扫描工具findBugs
date: 2017-06-13 09:37:34
tags: 静态代码扫描,findBugs
categories: 测试技术
---

## findbugs 简介
- findBugs是一个静态分析工具，它检查类或者jar文件，将字节码与一组缺陷模式进行对比以发现可能的问题，有了静态分析工具，就可以在不实际运行程序的情况下对软件进行分析，不是通过分析类文件的形式或者结构来确定程序的意图，而是通过使用Visitor模式，发现潜在问题。
- FindBugs原理是分析编译后的class文件，也就是字节码文件。我们需要了解FindBugs底层的处理机制。根据FindBugs官网文档描述，FindBugs使用了BCEL来分析Java字节码文件。从1.1版本开始，FindBugs也支持使用ASM字节码框架来编写bug探测器。
- findBugs中定义了很多检测器：比如检测hash equals不匹配、忽略方法返回值等等，我们也可以自己定义一个检测器。

## 下载findbugs源码
- 去[findBugs官网](http://findbugs.sourceforge.net/downloads.html)下载findbugs-3.0.1.zip 和 findbugs-3.0.1-source.zip（当前版本是3.0.1）
- findbugs-3.0.1.zip放的是可执行文件，其中lib下面有个findbugs.jar, 我们自定义规则编译jar要替换它。
- findbugs-3.0.1-source.zip是源码文件。

## 定义一个检测器
- 我们以Android代码中不能使用System.out.println打印日志为例，来写一个检测器。直接上代码吧，通过代码可以大致掌握findBugs的规则怎么写。

### 待检测代码

``` java
/**
 * Created by zlw on 2017/6/13.
 */
public class TestFindBugs {

    public void Test() {
        System.out.println("123"); //bug
        System.err.println("123"); //bug
    }
}
```

### 对应的字节码
- 用Intellij IDEA编写代码，它自带了一个ByteCodeViewer，可以通过View—Show ByteCode显示class字节码：

``` File
// class version 52.0 (52)
// access flags 0x21
public class com/liwei/findbugs/TestFindBugs {

  // compiled from: TestFindBugs.java

  // access flags 0x1
  public <init>()V
   L0
    LINENUMBER 8 L0
    ALOAD 0
    INVOKESPECIAL java/lang/Object.<init> ()V
    RETURN
   L1
    LOCALVARIABLE this Lcom/liwei/findbugs/TestFindBugs; L0 L1 0
    MAXSTACK = 1
    MAXLOCALS = 1

  // access flags 0x1
  public Test()V
   L0
    LINENUMBER 11 L0
    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
    LDC "123"
    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
   L1
    LINENUMBER 12 L1
    GETSTATIC java/lang/System.err : Ljava/io/PrintStream;
    LDC "123"
    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
   L2
    LINENUMBER 13 L2
    RETURN
   L3
    LOCALVARIABLE this Lcom/liwei/findbugs/TestFindBugs; L0 L3 0
    MAXSTACK = 2
    MAXLOCALS = 1
}

```

- 通过查看字节码，我们能找出关键信息

``` File
GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
GETSTATIC java/lang/System.err : Ljava/io/PrintStream;
```

### 编写探测器
- 将上面下载的findbugs-3.0.1-source.zip解压之后，导入Intellij idea中，然后开始写我们的探测器(在/src/java/目录下)，我在代码中已经详细的注释：

``` java
package edu.umd.cs.findbugs.detect;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;
import org.apache.bcel.classfile.Code;

/**
 * Created by zlw on 2017/6/13.
 *  用于检测代码中不使用System.out
 */
public class ForbiddenSystemClass extends OpcodeStackDetector {
    BugReporter bugReporter;

    public ForbiddenSystemClass(BugReporter bugReporter){
        this.bugReporter = bugReporter;
    }

    /**
     * 在每次进入字节码方法的时候调用，在每次进入新方法时清空标志位
     * @param obj
     */
    @java.lang.Override
    public void visit(Code obj) {
        super.visit(obj);
    }

    /**
     *  每扫描一条字节码就会进入sawOpcode方法
     * @param seen   字节码的枚举值
     */
    @java.lang.Override
    public void sawOpcode(int seen) {
        /**
         * System.out.println("123");
         *  这句代码的字节码如下：
         *   L0
         * LINENUMBER 11 L0
         * GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
         * LDC "123"
         * INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
         */
        if (seen == GETSTATIC){
            System.out.println("I am here");
            // getClassConstantOperand用于获取类的名称
            if (getClassConstantOperand().equals("java/lang/System")){
                System.out.println("I am here 11");
                // 用于获取方法的名称
                if (getNameConstantOperand().equals("out") || getNameConstantOperand().equals("err")){

                    // CJ_SYSTEMCLASS 是规则的名称
                    // NORMAL_PRIORITY 是优先级
                    // addClassAndMethod 用于添加正在检测的代码
                    // addSourceLine 添加源码line的信息
                    BugInstance bug = new BugInstance(this, "CJ_SYSTEMCLASS",
                            NORMAL_PRIORITY).addClassAndMethod(this).addSourceLine(
                            this, getPC());
                    System.out.println("I am here report");
                    // 上报bug
                    bugReporter.reportBug(bug);
                }
            }
        }
    }
}

```

### 将规则加入规则文件中
- 规则文件findbugs在（/etc/目录下）

``` xml
<FindbugsPlugin>  
  <!-- 这里的CJ_SYSTEMCLASS 就是上面代码中定义的规则type-->
  <Detector class="edu.umd.cs.findbugs.detect.ForbiddenSystemClass"  speed="fast" reports="CJ_SYSTEMCLASS" hidden="false" />  
  <BugPattern abbrev="CJ_SYSTEMCLASS" type="CJ_SYSTEMCLASS" category="PERFORMANCE" />  
</FindbugsPlugin>
```

- 配置message.xml,message.xml主要是配置检测到错误时展示的信息

``` xml
<?xml version="1.0" encoding="UTF-8"?>  
<MessageCollection>  
  <Plugin>  
    <ShortDescription>Default FindBugs plugin</ShortDescription>  
    <Details>  
    <![CDATA[
    <p>
    This plugin contains all of the standard FindBugs detectors.
    </p>
    ]]>  
    </Details>  
  </Plugin>  
    <Detector class="edu.umd.cs.findbugs.detect.ForbiddenSystemClass">  
       <Details>  
        <![CDATA[
        <p>代码不能出现System.out
        <p>请使用log日志形式打印
        ]]>  
       </Details>  
    </Detector>  
    <BugPattern type="CJ_SYSTEMCLASS">  
        <ShortDescription>代码不能出现System.out</ShortDescription>  
        <LongDescription>{1}代码不能出现System.out，请使用log形式输出</LongDescription>  
        <Details>  
      <![CDATA[
        <p>不能使用System.out和System.err，请使用log</p>
      ]]>  
        </Details>  
      </BugPattern>  
    <BugCode abbrev="CJ_SYSTEMCLASS">影响性能的输出System.out</BugCode>  
</MessageCollection>
```

### 编译jar
- 在findbugs根目录运行
``` bash
mvn clean install -Dmaven.test.skip=true
```
- 运行的jar在/target/目录下。

### 进行静态代码扫描
- 将上面编译的jar拷贝到上面提到的/lib目录下，然后运行命令（最好将findbugs.bat配置到PATH中）

``` bash
findbugs -textui -html -outputFile ff.html E:\javaworkspace\MineDemo\out\production\MineDemo\com\liwei\findbugs\TestFindBugs.class
```
- 这里的outputfile是html，也可以用xml。
- 得到的结果，如下图所示：
![findbugs_result.png](/upload/image/zlw/findbugs_result.png)

- 可以看到有2个waring，详细信息可以去ff.html中查看， 看看是不是自己定义的错误信息。

## 总结
- 上面大致讲解了findbugs的规则是怎么编写的，大家可以依葫芦画瓢，针对自己的业务，编写自己的探测器。
