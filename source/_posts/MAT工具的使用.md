---
title: MAT（Memory Analyzer Tool）工具的使用
date: 2016-10-19 17:28:59
tags: MAT,工具
categories: 测试工具
---
## 背景介绍
#### MAT简介
- MAT(Memory Analyzer Tool)，一个用于内存分析的工具，它能够抓取一段时间内的内存快照，帮助我们分析这一段时间内存的分配情况，通过分析内存情况可以帮助我们发现内存泄漏、内存大量分配等问题的原因所在。Eclipse本身可以装MAT的插件，但是现在我们一般都转向AndroidStudio，所以可以使用MAT独立的版本，可以从这里下载[MAT](https://eclipse.org/mat/downloads.php)

- 下载完成之后，解压就可以直接使用。

#### java垃圾回收机制
- java比较幸福的一件事情就是有GC（Garbage Collection），但是在项目节奏比较快的情况下，有可能开发同学会随心所欲的使用内存，重而就有可能造成内存泄漏，即有些内存明明不使用了，但是却不能被回收，严重浪费资源。
- JVM（java虚拟机）是根据**对象树**来判断某个对象是否能被回收的。如下图所示：
![GC Tree](/upload/image/gc_tree.png)
- 即从GC Root（直译为GC 根，形象的理解为一颗大树的根吧）开始检查看哪些对象是可以到达的，直到遍历完所有的叶子结点。遍历完成之后，就去回收那些不可达的对象。

###### GC root分类
- 我们应该掌握几种GC root，便于后面使用MAT对内存进行分析。
- Class：即由System Class Loader/Boot Class Loader加载的类对象，这些类对象不会被回收。
- Thread线程：激活状态的线程
- Stack Local栈中的对象，每个线程都会分配一个栈，栈中的局部变量或者参数都是GC root，因为它们的引用随时可能被用到
- JNI JNI中的引用的对象；可能在JNI中定义的，也可能在虚拟机中定义
- Monitor Used 用于保证同步的对象，例如wait()，notify()中使用的对象、锁等。
- Held by JVM JVM持有的对象。JVM为了特殊用途保留的对象，它与JVM的具体实现有关。比如有System Class Loader, 一些Exceptions对象，和一些其它的Class Loader。对于这些类，JVM也没有过多的信息。

## 使用MAT
#### 抓取内存快照
###### DDMS
- 直接使用DDMS，点击[Dump Hprof file],等待5s钟会弹出保存hprof文件的对话框，如下图所示：
![DDMS MAT](/upload/image/mat.PNG)
- 不过在Dump Hprof file之前，需要GC至少两次，让JVM回收那些可以被回收的对象。如下图所示：![ddms gc](/upload/image/ddmsgc.PNG)
- 生成的hprof不能直接被MAT打开，需要用Android SDK Tools中工具转一下
```bash
$ hprof-conv dump.hprof converted-dump.hprof
  之后就可以打开了。
```
###### AndroidStudio Monitor
- AndroidStudio最新版本我感觉比DDMS要人性化，所有的操作都可以在一个面板中完成，如下图所示：![AndroidStudio monitor](/upload/image/androidstudio_MAT.PNG)
- 如图中所示，1 对应的就是GC   2 对应的是可以抓取堆内存快照hprof文件（堆中对象的引用情况）  3 对应的是抓取一定时间内对象的分配情况（分配对象的个数及大小）。
- 抓取hprof时，还是先GC至少两次。
- 点击【dump java heap】后会在AndroidStudio的左边目录栏的Captures选项中的Heap SnapShot列表中多一个以时间命名的hprof文件，然后在hprof文件上右键选择【Export to standard .hprof】即可以转成MAT能打开的格式。如下图所示：
![Androidstudio_hprof](/upload/image/Androidstudio_hprof.PNG)
- 这里要说下AndroidStudio比较好用的功能，最新版（我也不知道从哪个版本开始）提供了可以检测内存泄漏的工具，直接点击上一步中生成的hprof文件，然后在右侧有一个【Analyzer Tasks】的选项，用它就可以直接来分析内存泄漏。如下图所示：
![AndroidStudio_Analyzermemor](/upload/image/AndroidStudio_Analyzermemory.PNG)

#### MAT分析hprof文件
- 这里主要介绍下MAT的各个模块都是干啥的以及简单用法，至于更详细的用法会在后面的实例分析中进行说明。
- 打开hprof文件之后，如下图所示：
![MAT_hprof](/upload/image/MAT_hprof.PNG)
- 如上所示，最顶部的是工具栏，大家可以自己点点用用，反正我觉得是不经常用到，这里就不过多介绍了。最常用的是【Histogram】(用于列出每个class的实例个数)和【Dominator Tree】(用于列出还存活的大对象，是从大到小进行排序)，下面就分别介绍下他们简单的用法

###### Histogram
- 点开Histogram，会列出每个class对应的对象个数，以及他们占有内存的大小，如下图所示：![histogram](/upload/image/histogram.PNG)
- 如图中所示最左边这一列是class，第二列是Object个数，第三列是Shallow Heap表示**对象在内存中的实际空间**，第四列是**对象回收后能释放出来的空间**
- 需要说明非常有用的是第一行是可以过滤信息的，可以写正则表达式，一般我们想过滤和我们业务有关系的，比如：xdja，我们就直接输入xdja，然后就可以过滤出来我们需要的东西啦。

###### doinator_tree
- 点开doinator_tree,会列出每个对象的内存大小情况，样子和HIstogram长的比较像，如下图所示：![Dominator_tree](/upload/image/Dominator_tree.PNG)
- 如图所示第一列是所有对象的名称 第二列是Shallow Heap表示**对象实际所占用的内存空间大小**，第三列是Retained Heap 表示**对象被释放之后所能释放的空间大小** 第四列表示对象占用内存空间的百分比
- 我们一般通过比较Retained Heap 的大小来估计内存泄漏的内存大小。Shallow Heap在分析问题时一般作用不大。
- Shallow Heap 和 Dominator的单位是Byte，所以一般可以直接除以1000，转成KB来预估大小。

###### 查看对象树
- 通过上面两种方式可以过滤出自己关心的类或者对象，然后在类或者对象的item上单击右键，如下图所示：
![gc_root_look](/upload/image/gc_root_look.png)
- 如图所示，我们介绍下弹出菜单中各个选项的作用。
- 【merge shortest Paths to  GC Roots】合并从GC根节点到一个对象或一组对象的共同路径。
- 【Path To GC Roots】查看这个对象所有的GC Roots。
- 上面两个选项，点击之后子菜单中再选择exclude all phantom/weak/soft etc.references(排查虚引用/弱引用/软引用等）因为被虚引用/弱引用/软引用的对象可以直接被GC给回收。
- List objects -> with incoming references ：查看这个对象持有的外部对象引用
- List objects -> with outcoming references ：查看这个对象被哪些外部对象引用

- 其实所有MAT的菜单选项都是由SQL语句组成的，你也可以写自己的sql语句，只不过MAT帮我们封装的基本已经够我们用了

## 结尾
- 上面只是简单介绍了MAT的基本用法，其实它还有很多可供我们使用的工具，比如：将drawable怎么以bitmap的形式查看、查看集合的值、写SQL语句、查看线程信息等。大家可以在使用的时候Google一下就可以了。上面讲的是最常用的用法。
- 之后会有结合实例分析的文章，进一步熟悉MAT的使用。
