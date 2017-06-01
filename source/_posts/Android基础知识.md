---
title: Android基础知识
date: 2017-05-02 09:43:28
tags: Android，基础
categories: Android技术
---

## Activity
### Activity的InstanceState
- Activity的 onSaveInstanceState() 和 onRestoreInstanceState()并不是生命周期方法，当遇到意外情况（如：内存不足、用户直接按Home键）由系统销毁一个Activity时，onSaveInstanceState() 会被调用。但是当用户主动去销毁一个Activity时，例如在应用中按返回键，onSaveInstanceState()就不会被调用。通常onSaveInstanceState()只适合用于保存一些临时性的状态，而onPause()适合用于数据的持久化保存。

### Activity的启动模式（launchMode）
- standard
-
