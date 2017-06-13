---
title: 静态代码扫描工具fireline
date: 2017-06-13 16:12:39
tags: 静态扫描,火线,fireline
categories: 测试技术
---

## 背景
- firline(火线)是360 搞的一款移动端的静态代码扫描工具，现在火线在360公司app的发布流程中是必不可少的环节，目前火线在360发布流程中已累计运行超过500天，扫描文件数2千万+，扫描代码量超过45亿行。火线最近推出的Android Studio插件360 Fireline Plugin下载量已达到4000+。
- 火线拥有四大规则：
  - 安全类：和360信息安全部分合作，根据最权威的SDL专门定制，每一条SDL都有真实的攻击案例。
  - 内存类：各种资源关闭类问题检测。
  - 日志类：检测日志输出敏感信息内容的规则。
  - 基础类：规范类、代码风格类、复杂度检查规则。

## 使用方法
- 详细可以参考官网的介绍[使用手册](http://magic.360.cn/user.html)。
- 我们这里重点来介绍下如何自定义规则。

## 自定义规则
- 以pmd规则为例，之前的文章中介绍过如何自定义pmd规则。[静态代码扫描工具PMD](https://liwei721.github.io/2017/06/01/静态分析工具PMD学习/)

### 下载fireline.jar
- 先去官网下载[fireline.jar](http://magic.360.cn/fireline.jar)。
- 然后将fireline.jar解压到当前目录，方便修改其文件。
- 看下解压后的目录结构：
![pmd_folder](/upload/image/zlw/pmd_folder.png)

### 以xpath 方式自定义pmd规则
- 以xpath方式定义的规则，只需要修改xml目录下的文件，如下图所示目录：
![pmd_path](/upload/image/zlw/pmd_path.png)

- 在PMD_RedLineRule_All.xml中添加一个rule，如下所示

``` xml
<!--用于测试-->
<rule xmlns="" class="net.sourceforge.pmd.lang.rule.XPathRule"
         externalInfoUrl="${pmd.website.baseurl}/rules/java/empty.html#EmptyFinallyBlock"
         language="java"
         message="While With brance"
         name="WhileLoopsMustUseBracesRule"
         since="0.4">
		    <description>
WhileLoops Must use braces Rule.
      </description>
		    <priority>1</priority>
		    <properties>
			      <property name="xpath">
				        <value>
//WhileStatement[not(Statement/Block)]
              </value>
			      </property>
		    </properties>
		    <example>

public class PmdWhileTest {
    public void testWhile(){
        int i = 0;
        while (i != 0)
            i++;

        System.out.println("this is a test");
    }
}
      </example>
	  </rule>
```
- 然后将自己定义的规则复制到pmd_rules/java/目录下，我这里的规则是mycustom.xml。这些规则可以用pmd自带的规则改改就成了。

``` xml
<?xml version="1.0"?>

<ruleset name="My Custom rules"
         xmlns="http://pmd.sourceforge.net/ruleset/2.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://pmd.sourceforge.net/ruleset/2.0.0 http://pmd.sourceforge.net/ruleset_2_0_0.xsd">
    <description>
        mine rules
    </description>

    <rule name="WhileLoopsMustUseBrancesRule"
          since="5.7"
          language="java"
          message="Avoid using 'while' statements without using curly braces"
          class="net.sourceforge.pmd.lang.rule.XPathRule"
          externalInfoUrl="${pmd.website.baseurl}/rules/java/WhileLoopsMustUseBracesRule.html">
        <description>
            Avoid using 'while' statements without using curly braces
        </description>
        <properties>
            <property name="xpath">
                <value>
                    <![CDATA[
//WhileStatement[not(Statement/Block)]
]]>
                </value>
            </property>
        </properties>
        <priority>1</priority>
        <example>
            <![CDATA[
    public void doSomething() {
      while (true)
          x++;
    }
]]>
        </example>
    </rule>
</ruleset>
```

### 写java文件定义的规则
- 这种方式需要在pmd源码中编译pmd-core（如果有改动）和pmd-java。进入它们对应的目录，执行：

``` bash
mvn clean package
```
- 将编译得到的jar文件复制到fireline目录下，替换里面的对应的jar。

### 重新打包jar
- 在fireline的上层目录下面，执行命令：

``` bash
## 这里的MANIFEST.MF是jar的配置清单，必须有，否则会报【没有清单文件】的错误
jar cvfm fireline.jar fireline/META-INF/MANIFEST.MF -C fireline/ .  
```
### 进行扫描
- 接下来就可以用编译的jar进行扫描了。

``` bash
java -jar D:\worktools\fireline.jar -s=E:\javaworkspace\MineDemo\src\com\liwei\pmd\PmdWhileTest.java -r=F:\360FireReport
```

## 总结
- 火线已经对规则过滤的非常好了，所以一般情况下不需要自定义规则，除非有和自己业务相关的代码需要规则来约束。
