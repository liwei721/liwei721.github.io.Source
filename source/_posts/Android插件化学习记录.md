---
title: Android插件化学习记录
date: 2017-04-18 22:42:04
tags: Android, 插件化
categories: Android技术
---

## 背景
- 插件化可以解决两种问题：
>1. 从技术上讲，业务逻辑的复杂导致代码量急剧膨胀，各大厂商陆续碰到了65535方法数的限制。
>2. 在业务层面上，功能模块的解耦以及团队模块分工，每个模块能够单独的发布升级，提升产品迭代效率。

- 插件化的实现，从本质上要解决两个问题：1、代码加载   2、资源加载
### 代码加载
- 类的加载可以使用JAVA的ClassLoader机制，但是对于Android来说，并不是类加载进来就可以使用了，Android中很多组件都是有生命的，所以我们还应该考虑组件生命周期管理。
- 另外，如何管理加载进来的类也是一个问题。假设多个插件依赖了相同的类，是抽取公共依赖进行管理还是插件单独依赖？这就是ClassLoader的管理问题；

### 资源加载
- 资源加载方案大家使用的原理都差不多，都是用AssetManager的隐藏方法addAssetPath。
- 但是，不同插件的资源如何管理？是公用一套资源还是插件独立资源？共用资源如何避免资源冲突？对于资源加载，有的方案共用一套资源并采用资源分段机制解决冲突（要么修改aapt要么添加编译插件）；有的方案选择独立资源，不同插件管理自己的资源。

## Hook机制之动态代理
### 动态代理

- JDK提供了动态代理方式，可以简单理解为JVM可以在运行时动态生成一系列的代理类。

``` java
/***
*  @param loader 　一个ClassLoader对象，定义了由哪个ClassLoader对象来对生成的代理对象进行加载
*  @param interfaces 一个Interface对象的数组，表示的是我将要给我需要代理的对象提供一组什么接口，如果我提供了一组接口给它，那么这个代理对象就宣称实现了该接口(多态)，这样我就能调用这组接口中的方法了
* @param 一个InvocationHandler对象，表示的是当我这个动态代理对象在调用方法的时候，会关联到哪一个InvocationHandler对象上
*
**/
public static Object newProxyInstance(ClassLoader loader, Class<?>[] interfaces,  InvocationHandler h)  throws IllegalArgumentException
```
- 上面提到了Invocationhandler，每一个动态代理类都必须要实现InvocationHandler这个接口，并且每个代理类的实例都关联到了一个handler，当我们通过代理对象调用一个方法的时候，这个方法的调用就会被转发由InvocationHandler这个接口的invoke方法来调用：

``` java
Object invoke(Object proxy, Method method, Object[] args) throws Throwable
proxy:   指代我们所代理的真实对象
method:  指代的是我们所要调用真实对象的某个方法的method对象。
args：   指代的是调用真实对象某个方法时所接受的参数。
```

### 代理Hook
- 我们首先创建自己的代理对象（可以实现自己的逻辑），然后把原始对象替换为我们的代理对象，这样我们就能通过这个代理对象为所欲为了（比如：修改参数、替换返回值），这种方式我们称为Hook。
- 找被Hook的对象：静态变量和单例比较容易被Hook，因为在一个进程内，静态变量和单例变量时相对不容易发生变化的。






















##
