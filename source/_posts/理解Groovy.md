---
title: 理解Groovy（gradle系列七）
date: 2017-03-16 19:49:57
tags: Groovy语法
categories: gradle
---
## 背景
- 要彻底理解gradle，我们需要对Groovy语法有一定的了解，这对我们自定义自己的gradle task及使用gradle很有用处。


## 理解Groovy
### 简介
- Groovy起源于java，其运行在JVM上，它的目标是创造更简单，更直接的语言。
- Groovy中一些基础的用法：

``` groovy
// 打印一个字符串
print 'Hello world!!'
// 上面用到了单引号，但是也可以使用双引号，不过双引号可以插入语句，如下：
def name = 'Andy'
def greeting = "Hello $name!"       // Hello Andy
def name_sie = "Your name is ${name.size()} characters long"  // Your name is 4 characters long

// 上面可以看到,对于占位符是方法或者变量的，要用${}这种格式，而对于单一变量，只需要$就可以了。

// 还可以像下面这样写，这在java中是不可能的。但在Groovy中是合法的
def method = 'toString'
new Date()."$method"()

```

### Classes和members
- Groovy里面创建类和java类似，举个例子：

``` Groovy
class MyGroovyClass {
       String greeting
       String getGreeting() {
           return 'Hello!'
        }
}
// 类名和成员变量都不需要修饰符，类默认的修饰符是public，成员变量默认修饰符是private

// 使用MyGroovyClass，用def创建变量，成员变量的get/set方法是Groovy默认添加的
def instance = new MyGroovyClass()
instance.setGreeting 'Hello, Groovy!'
instance.getGreeting()

// 使用成员变量，可以这么干（两种方式都是可以的）：
 println instance.getGreeting()
 println instance.greeting
```
### 方法
- 直接来看个例子：

``` groovy
def square(def num) {
      num * num
}
square 4   // 16

//可以看到相对于java来说没有了返回值、修饰符变成了def、方法体内没有了return。

// 我们甚至可以写的更简单
def square = { num ->
       num * num
}
square 8

```

### 闭包
- 闭包是一段匿名的方法体，其可以接受参数和返回值，他们可以定义变量或者可以将参数传递给方法：

``` groovy
Closure square = {
       it * it
}
square 16

// Groovy会默认为你添加一个参数叫做it
```

### Collections
- Groovy中，有两个重要的容器：lists和maps

``` groovy
List list = [1, 2, 3, 4, 5]

//迭代list
list.each() { element ->
       println element
}

//你甚至可以使得你的代码更加简洁，使用it:
list.each() {
       println it
}
```

``` groovy
//map
Map pizzaPrices = [margherita:10, pepperoni:12]

// 如果你想取出map中的元素，可以使用get方法：
pizzaPrices.get('pepperoni')
pizzaPrices['pepperoni']

// 甚至可以这么用：
pizzaPrices.pepperoni

```

### Groovy 导入包
- 在gradle脚本中使用InputStream不用import包，而使用ZipFile需要import包,是因为Groovy默认import下面的包和类：

``` java
java.io.*
java.lang.*
java.math.BigDecimal
java.math.BigInteger
java.net.*
java.util.*
groovy.lang.*
groovy.util.*
```

### build.gradle 中 groovy 常用写法

``` groovy
//apply是一个方法，plugin是参数，值为'com.android.application'
apply plugin: 'com.android.application'

/**
*buildscript,repositories和dependencies本身是方法名。
*后面跟的大括号部分，都是一个闭包，作为方法的参数。
*闭包可以简单的理解为一个代码块或方法指针。
*/
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:1.2.3'
    }
}

//groovy遍历的一种写法 each后面是闭包
android.applicationVariants.each { variant ->
}
```
