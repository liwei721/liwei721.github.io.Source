---
title: Android知识点
date: 2017-04-20 12:11:53
tags: Android知识
categories: Android技术
---

## 背景
- 平时工作学习中，有些Android的知识点经常碰到，每次查找原因之后，隔一段时间不碰到，再次碰见还是需要查询。
- 因此就在这里记录下来，方便之后查看。节省时间。

## 知识点

### dex方法数不超过65535
- 在app足够复杂时，在打包时会遇到下面这种错误提示：

``` files
Unable to execute dex: method ID not in [0, 0xffff]: 65536
```

- 为什么方法数目不能超过65K呢，有人说是dexopt的问题，其实dex文件结构是用32位来存储method id 的，并且dexopt是app已经打包成功，安装到手机后才会发生的过程，但是65K问题是在打包时发生的，所以问题不在 dexopt。
- 一般提到的dexopt错误，其实是Android2.3及其以下在dexopt执行时只分配5M内存，导致方法数目过多（数量不一定到65K）时在odex过程中崩溃，官方称之为Dalvik linearAlloc bug。
- 那么65K问题真正的原因是：dalvik bytecode中的指令格式使用了16位来存放@CCCC导致的，不仅Method数据不能超过65K，Field和Class数目也不能超过65K。
