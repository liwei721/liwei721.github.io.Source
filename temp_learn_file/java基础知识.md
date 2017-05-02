---
title: java相关知识点
date: 2017-3-31 14:49:23
tags: java知识
categories: java知识
---

## java双亲委派机制
### JVM预定义的三种类型类加载器
- 启动（bootstrap）类加载器：是用native代码实现的类装载器，它负责将<Java_Runtime_Home>/lib下面的类加载到内存中（比如rt.jar）。
- 标准扩展（Extension）类加载器：是由Sun的ExtClassLoader实现的。它负责将<Java_Runtime_Home>/lib/ext或者系统变量java.ext.dir指定位置的类库加载到内存中。
- 系统（System）类加载器：是由sun 的APPClassLoader实现的。负责加载ClassPath中的类库加载到内存。
- Android中类加载器有BootClassLoader,URLClassLoader,PathClassLoader,DexClassLoader,BaseDexClassLoader,等都最终继承自java.lang.ClassLoader。
### 双亲委派机制
- 某个特定的类加载器在接到类加载请求时，首先将任务委托给父类加载器，依次递归，如果父类加载器完成类加载任务，就返回；如果父类加载器没有加载到类，自己才去加载。

### 思考
- JAVA虚拟机的第一个类加载器是Bootstrap，这个加载器比较特殊，它不是java类，不需要别人加载，嵌套在java虚拟机内核里，JVM启动时，它也随之启动。
- 委派机制的意义——防止内存中出现多份同样的字节码。
