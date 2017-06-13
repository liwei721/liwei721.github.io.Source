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

## 举个例子
- 从网上找了一个监控Android 日志打印敏感信息的例子，我在代码中做了详细的注释，建议看代码时最好结合着PMD designer来看，这样对一些节点的名字和结构更容易理解和掌握，下面就直接上代码了。

### Android中常见的打印日志的写法

``` java
public class TestLog{
    static Logger Log = Logger.getLogger("log");
    static boolean DEBUG = true;
    static boolean DEBUG1 = false;
     public static void main(String []args){
        Context cont = activity.getApplicationContext();    
        String classname = activity.getLocalClassName();
        String pcodeName = cont.getPackageCodePath();
        int id= android.os.Process.myPid();
        String pid =String.valueOf(id);     
        int uicd= android.os.Process.myUid();
        String uid = String.valueOf(uicd);
        int idname= android.os.Process.getUidForName("pay");
        String imei = ((TelephonyManager)getSystemService(TELEPHONY_SERVICE)).getDeviceId();
        int bbq=activity.getLocalClassName();

        Log.i("classname", classname);//触发规则
        Log.i("pcodeName", pcodeName);//触发规则
        Log.i("pid", pid);//触发规则
        Log.i("uid", uid);//触发规则
        Log.i("imei", imei); //触发规则
        Log.i("imei", imei.length);
        Log.i("imei", imei.size());
        Log.i("imei:", activity.getLocalClassName());//触发规则
        Log.i("imei:", MYUUID);
        Log.i("imei:", imei.toString());//触发规则
        Log.i("imei:", ab.imei.toString());//触发规则
        Log.i("imei:", bbq);//触发规则
        Log.i("imei:", idname);//触发规则
        Log.i("imei:", id);//触发规则
        Log.i("imei:", uicd);//触发规则
        Log.i("imei:", pcodeName);//触发规则
        Log.i("imei:", 101);       

        if (DEBUG) {
            Log.i("imei", imei);//触发规则
        }      
        if (DEBUG1) {
            Log.i("imei", imei);
        }      
     }  
}
```

### 编写的规则代码

``` java
package net.sourceforge.pmd.lang.java.rule.androidreadline;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by zlw on 2017/6/12.
 * 对日志中的敏感信息进行检测
 */
public class LogBlockRule extends AbstractJavaRule {
    private static Set<String> SensitiveStrings = new HashSet<String>();
    private List<ASTName> astNamewithLog = (List<ASTName>) new ArrayList<ASTName>();
    private List<String> BooleanStrings = new ArrayList<String>();
    private List<ASTName> SASTNames = (List<ASTName>) new ArrayList<ASTName>();
    private List<ASTVariableDeclaratorId> SensitiveVariables = (List<ASTVariableDeclaratorId>) new ArrayList<ASTVariableDeclaratorId>();

    static {
        SensitiveStrings.add("classname");
        SensitiveStrings.add("pid");
        SensitiveStrings.add("uid");
        SensitiveStrings.add("imei");
        SensitiveStrings.add("getLocalClassName");
        SensitiveStrings.add("getPackageCodePath");
        SensitiveStrings.add("getPackagePath");
        SensitiveStrings.add("android.os.Process.myPid");
        SensitiveStrings.add("android.os.Process.myUid");
        SensitiveStrings.add("android.os.Process.getUidForName");
    }


    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        return super.visit(node, data);
    }

    /**
     * 检查log是否有敏感信息输出
     *
     * @param node
     * @param data
     */
    private void checkLogRule(Node node, Object data) {
        // 这个xpathBoolean 是为了找到定义的boolean变量 = true
        String xpathBoolean = ".//FieldDeclaration/VariableDeclarator/VariableInitializer/Expression/PrimaryExpression"
                + "/PrimaryPrefix/Literal/BooleanLiteral[@True='true']";
        // 找出源码中所有以Log.*开头的代码
        pickUpLogMethods(node);

        if (astNamewithLog.isEmpty()) {
            return;
        }
        try {
            // 通过xpath获取所有定义的boolean类型的变量
            List<ASTBooleanLiteral> xpathBooleanStringNames = (List<ASTBooleanLiteral>) node.findChildNodesWithXPath(xpathBoolean);
            if (xpathBooleanStringNames.size() > 0) {
                for (ASTBooleanLiteral booleanLiteral : xpathBooleanStringNames) {
                    // 从boolean型值的父节点中查找VariableDeclarator  比如： b = true;
                    ASTVariableDeclarator variableDeclarator = booleanLiteral.getFirstParentOfType(ASTVariableDeclarator.class);
                    // 这里是获取 变量的名称，比如 b
                    ASTVariableDeclaratorId variableDeclaratorId = variableDeclarator.getFirstChildOfType(ASTVariableDeclaratorId.class);
                    this.BooleanStrings.add(variableDeclaratorId.getImage());
                }
            }

            List<ASTName> xpathLogNames = this.astNamewithLog;
            for (ASTName name : xpathLogNames) {
                String imageString = name.getImage();
                // 这里重复判断一次，是否是包含Log.d的语句
                if (imageString != null && imageString.contains("Log.")) {
                    // 检测Log.d是否被if语句包围
                    ASTIfStatement ifStatement = name.getFirstParentOfType(ASTIfStatement.class);
                    ASTBlockStatement blockStatement = name.getFirstParentOfType(ASTBlockStatement.class);
                    List<ASTName> names2 = blockStatement.findDescendantsOfType(ASTName.class);
                    if (names2.size() > 0) {
                        for (ASTName name2 : names2) {
                            if (name2 != null) {
                                String imageString2 = name2.getImage();
                                boolean sflag = CheckIsSensitiveString(imageString2);

                                // 没有发现包含敏感信息，把该ASTName节点存储后续解析
                                if (!sflag) {
                                    this.SASTNames.add(name2);
                                }

                                // 当前发现包含敏感信息，确认是否被if包围
                                if (sflag) {
                                    if (ifStatement != null) {

                                        // 这里是获取if语句中的boolean值，这里只判断了if（isTrue）的情况
                                        ASTExpression astExpression = ifStatement.getFirstDescendantOfType(ASTExpression.class);
                                        ASTName astName = astExpression.getFirstDescendantOfType(ASTName.class);
                                        if (astName != null) {
                                            String asstNameString = astName.getImage();
                                            if (this.BooleanStrings.size() > 0 && BooleanStrings.contains(asstNameString)) {
                                                // 这里从之前获取的所有Boolean变量为true中查找是否有当前的boolean值。如果有就记录当前的触发规则的数据
                                                addViolation(data, name2);
                                            }
                                        }
                                    } else {
                                        // 没有被if包围，触发规则
                                        addViolation(data, name2);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 第二层敏感信息监测，这里是获取所有的变量值，比如b
            List<ASTVariableDeclaratorId> variableDeclaratorIds = node.findDescendantsOfType(ASTVariableDeclaratorId.class);
            // 找出定义的所有变量
            if (variableDeclaratorIds.size() > 0) {
                for (ASTVariableDeclaratorId variableDeclaratorId : variableDeclaratorIds) {
                    // 获取变量的type类型节点
                    ASTType type = variableDeclaratorId.getTypeNode();
                    if (!(type.jjtGetParent() instanceof ASTFormalParameter)) {
                        // 获取变量的值
                        ASTName astName = variableDeclaratorId.getFirstParentOfType(ASTVariableDeclarator.class).getFirstDescendantOfType(ASTName.class);
                        if (astName != null) {
                            if (CheckIsSensitiveString(astName.getImage())) {
                                this.SensitiveVariables.add(variableDeclaratorId);
                            }
                        }
                    }
                }

                // 获取到有敏感信息的变量
                if (SensitiveVariables.size() > 0) {
                    for (ASTVariableDeclaratorId sensitiveVariable : SensitiveVariables) {
                        // 这句话的意思是对于声明了变量，变量的值是敏感信息，处理这一类的敏感信息判断。
                        for (ASTName secondastName : this.SASTNames) {
                            String astNameimage = secondastName.getImage();
                            if (!(hasNullInitializer(sensitiveVariable)) && astNameimage != null && sensitiveVariable.getImage().equalsIgnoreCase(astNameimage)) {
                                // 重复上面的步骤，判断是否被if包围。
                                ASTIfStatement ifStatement = secondastName.getFirstParentOfType(ASTIfStatement.class);
                                if (ifStatement != null) {
                                    ASTExpression astExpression = ifStatement.getFirstDescendantOfType(ASTExpression.class);
                                    ASTName astName3 = astExpression.getFirstDescendantOfType(ASTName.class);
                                    if (astName3 != null) {
                                        String astNameString = astName3.getImage();
                                        if (BooleanStrings.size() > 0 && BooleanStrings.contains(astNameString)) {
                                            addViolation(data, secondastName);
                                        }
                                    }
                                } else {
                                    // 没有被if包围则直接触发规则
                                    addViolation(data, secondastName);
                                }
                            }
                        }
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            astNamewithLog.clear();
            SASTNames.clear();
            BooleanStrings.clear();
            SensitiveVariables.clear();
        }

    }

    /**
     * 判断变量是否为null， 如果初始化为null，则剔除
     *
     * @return
     */
    private boolean hasNullInitializer(ASTVariableDeclaratorId var) {
        ASTVariableInitializer init = var.getFirstDescendantOfType(ASTVariableInitializer.class);
        if (init != null) {
            List<?> nulls = init.findChildNodesWithXPath("Expression/PrimaryExpression/PrimaryPrefix/Literal/NullLiteral");
            return !nulls.isEmpty();
        }

        return false;
    }

    /**
     * 找出源代码中的log.*的代码
     */
    private void pickUpLogMethods(Node node) {
        // 查找所有的语句表达式
        // 查找代码中的语句，比如：Log.d(Tag, msg);
        List<ASTStatementExpression> pexs = node.findDescendantsOfType(ASTStatementExpression.class);

        // 遍历所有的语句
        for (ASTStatementExpression ast : pexs) {
            // 查找执行方法括号之前的部分
            // 这里是去获取Log.d
            ASTPrimaryPrefix primaryPrefix = ast.jjtGetChild(0).getFirstDescendantOfType(ASTPrimaryPrefix.class);

            if (primaryPrefix != null) {
                // 获取name属性
                // 这里用到的是Log.d
                ASTName name = primaryPrefix.getFirstChildOfType(ASTName.class);
                if (name != null) {
                    // 通过getImage来获取Log.d的字符串值"Log.d"
                    String imageString = name.getImage();
                    if (imageString.startsWith("Log.")) {
                        // 保存有Log.的ASTName
                        astNamewithLog.add(name);
                    }
                }
            }
        }
    }

    /**
     * 判断是否包含敏感信息
     *
     * @param imageString2
     * @return
     */
    private boolean CheckIsSensitiveString(String imageString2) {
        if (imageString2 == null) return false;

        for (String sensitiveString : SensitiveStrings) {
            if (imageString2.equalsIgnoreCase(sensitiveString)) {
                return true;
            }

            // 处理类似Log.i("imei", imei.length);   Log.i("imei", imei.size()); 这种情况

            if (imageString2.contains(".")) {
                String[] partStrings = imageString2.split("\\.");
                int LastIndex = partStrings.length - 1;
                if (partStrings[LastIndex].equals("length") || partStrings[LastIndex].equals("size")) {
                    return false;
                } else {
                    for (int i = 0; i < partStrings.length; i++) {
                        String partString = partStrings[i];
                        if (partString.equalsIgnoreCase(sensitiveString)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
}

```

## 总结
- 其实PMD自定义规则并不难，结合着PMD Rule Designer，熟悉了代码的树形结构，然后确定要触发规则时代码的写法是什么，这样就能比较轻松的写出规则代码。
