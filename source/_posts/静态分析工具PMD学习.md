---
title: 静态分析工具PMD学习
date: 2017-06-01 16:28:45
tags: 静态代码扫描,PMD
categories: 测试技术
---

## PMD简介
- PMD是一种开源分析Java代码错误的工具。与其他分析工具不同的是，PMD通过静态分析获知代码错误。也就是说，在不运行Java程序的情况下报告错误。PMD附带了许多可以直接使用的规则，利用这些规则可以找出Java源程序的许多问题，例如：
>- 潜在的bug：空的try/catch/finally/switch语句
>- 未使用的代码：未使用的局部变量、参数、私有方法等
>- 可选的代码：String/StringBuffer的滥用
>- 复杂的表达式：不必须的if语句、可以使用while循环完成的for循环
>- 重复的代码：拷贝/粘贴代码意味着拷贝/粘贴bugs
>- 循环体创建新对象：尽量不要再for或while循环体内实例化一个新对象
>- 资源关闭：Connect，Result，Statement等使用之后确保关闭掉

- 此外，用户还可以自己定义规则，检查Java代码是否符合某些特定的编码规范。例如，你可以编写一个规则，要求PMD找出所有创建Thread和Socket对象的操作。

## 工作原理
- PMD的核心是JavaCC解析器生成器。PMD结合运用JavaCC和EBNF（扩展巴科斯-诺尔范式，Extended Backus-Naur Formal）语法，再加上JJTree，把Java源代码解析成抽象语法树（AST，Abstract Syntax Tree）。
- 从根本上看，Java源代码只是一些普通的文本。不过，为了让解析器承认这些普通的文本是合法的Java代码，它们必须符合某种特定的结构要求。这种结构可以用一种称为EBNF的句法元语言表示，通常称为“语法”（Grammar）。JavaCC根据语法要求生成解析器，这个解析器就可以用于解析用Java编程语言编写的程序。
- 不过实际运行中的PMD还要经过JJTree的一次转换。JJTree是一个JavaCC的插件，通过AST扩充JavaCC生成的解析器。AST是一个Java符号流之上的语义层。有了JJTree，语法分析的结果不再是“System, ., out, ., . println”之类的符号序列，而是一个由对象构成的树型层次结构。例如，下面是一段简单的Java代码以及与之对应的AST：

``` java
public class Foo {
  public void bar() {
    System.out.println("hello world");
      }
}
```

与之对应的语法树如下：

``` File
CompilationUnit
TypeDeclaration
ClassDeclaration
UnmodifiedClassDeclaration
ClassBody
ClassBodyDeclaration
MethodDeclaration
ResultType
MethodDeclarator
FormalParameters
Block
BlockStatement
Statement
StatementEXPression
PrimaryExpression
PrimaryPrefix
Name
PrimarySuffix
Arguments
ArgumentList
Expression
PrimaryExpression
PrimaryPrefix
Literal
```

## PMD安装及目录介绍

### 安装
- 可以到[官网](https://pmd.github.io/)下载对应的包：
>1. pmd-src-5.4.1是PMD源码包，是无法直接执行的。
>2. pmd-bin-5.4.1是PMD的可执行包。

### 目录介绍
#### pmd-bin-5.7.0
- bin目录
>- designer.bat[界面工具，能将java源代码转化为AST（抽象语法树）]
>- bgastviewer.bat [界面工具，与designer.bat功能相似]
>- cpd.bat[用来查找重复代码的工具，命令行版]
>- cpdgui.bat[用来查找重复代码的工具，GUI版]
>- pmd.bat[Window平台下运行PMD需要使用的文件]
>- run.sh [Linux平台下运行PMD需要使用的文件]

- lib目录
>- 【该目录存放PMD运行依赖的jar包，包括第三方jar包和各种语言的模块jar包】

#### pmd-src-5.7.0
- pmd-core【PMD的核心执行调度模块】
- pmd-java 【针对java语言的检测模块】
  - java -> net -> sourceforge -> pmd -> lang->java【目录太深，在此处聚合】
  - rule【该目录下存放已经编写好的java规则文件】
      - basic【基础类规则】
      - AvoidBranchingStatementAsLastInLoopRule.java【避免在循环的最后使用分支语句】
      - AvoidMultipleUnaryOperatorsRule.java【避免一元运算符的多重使用】
      - ...【其他基础类的规则文件】
      - codesize【代码体积类规则】
      - ...【各种规则类别的目录，包含该类别的java编写的规则文件】
  - resources
    - rulesets【java规则对应的xml文件】
      - java
      - android.xml【PMD运行时使用该文件会调用安卓类规则进行扫描】
      - basic.xml【PMD运行时使用该文件会调用基础类规则进行扫描】
      - ...【其他类别的规则xml文件】
  - etc
    - grammar
- Java.jjt【AST抽象语法树生成所需的语法文件】
- pmd-java8 【新增对java1.8版本的支持模块】
- pmd-javascript 【针对javascript语言的检测模块】
- pmd-jsp 【针对jsp语言的检测模块】
- ...【其余的主要是针对不同语言实现的独立的检测模块】
