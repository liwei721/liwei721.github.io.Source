---
title: eventBus简介
date: 2017-04-12 21:54:40
tags: eventBus
categories: Android开源库
---
## 背景

### 参考文章
- [ Android EventBus实战 没听过你就out了](http://blog.csdn.net/lmj623565791/article/details/40794879#comments)
- [Android消息传递之EventBus 3.0使用详解](http://www.cnblogs.com/whoislcj/p/5595714.html)
- [github地址](https://github.com/greenrobot/EventBus)

### 概述
- EventBus是一个发布/订阅的事件总线简化了应用程序各个组件间（Activity、service、Fragment等）组件和后台线程间的通信。EventBus主要角色如下：
>1. Event 传递的事件对象。
>2. Subscriber 事件的订阅者。
>3. Publisher 事件的发布者。
>4. ThreadMode 定义函数在何种线程中执行。
- 订阅者订阅事件到总线，发送者发布事件，官方给出的它们的关系图如下：
![EventBus-Publish-Subscribe.png](/upload/image/zlw/EventBus-Publish-Subscribe.png)

## 使用EventBus
### build.gradle添加引用
- github里面可以找到最新的版本，现在是3.0

``` groovy
compile 'org.greenrobot:eventbus:3.0.0'
```
### 定义一个事件类型
- 这个事件类型实体类，可以自己定义：

``` java
public class EventBusMsg {
  // 类的结构是自定义的,我这类添加了一个 String类型的 name 字段 方便测试.
  public String name;

  public EventBusMsg(String name) {
      this.name = name;
  }
}
```

### 订阅/取消订阅事件
- 订阅事件进行三步设置，注册、取消注册和定义事件接受函数，

``` java
@Override
protected void onCreate(Bundle savedInstanceState) {
   ...
   // 1. 注册
   EventBus.getDefault().register(MainActivity.this);
}
```

- 在界面销毁时取消订阅

``` java
@Override
protected void onDestroy() {
    super.onDestroy();
    // 2. 解注册
    EventBus.getDefault().unregister(MainActivity.this);
}
```

- 定义事件接收方法，方法的名字自己定义，使用注解@Subscribe()表示

``` java
@Subscribe(threadMode = ThreadMode.MAIN)
public void onMessageEvent(EventBusMsg event) {
    Log.d(TAG, "接收到信息");
    tv_console.setText("EventBus 数据 : " + event.name);
}
```
- 使用@Subscribe()注解表明这个方法是EventBus事件接收的方法。
- 事件接收方法的参数必须和EventBus发送的事件类是同一类型，比如例子中是EventBusMsg。
- 在@Subscribe()中可以设置threadMode来设置不同的模式，ThreadMode.Main表示运行在主线程。

### 发布事件
- 比较简单，调用EventBus的post方法即可

``` java
public void sendMsg(View view) {
  // 发送消息
  EventBus.getDefault().post(new EventBusMsg("我是EventBus发送的数据"));
  // 销毁当前Activity
  finish();
}
```
- 上面的流程是要先订阅事件，再发布事件才能接收到事件。

## EventBus 事件回调方法线程（Delivery Thread）
- EventBus会处理事件接收线程的问题，比如：在线程A中发布事件，事件传递到线程B中执行，这个功能主要用在处理更新UI的问题上。EventBus可以帮助我们处理UI和后台任务的同步问题，EventBus有四种线程模式，可以通过@Subscribe()朱姐的threadMode参数指定：

### ThreadMode.POSTING
- POSTING是默认参数，即接收事件的方法和发布消息的方法运行在同一个线程中。这种模式优点是：开销最小，不需要切换线程。

``` java
// 运行在发布消息的线程中 (default)
// ThreadMode 是可选设置
@Subscribe(threadMode = ThreadMode.POSTING)
public void onMessage(MessageEvent event) {
  log(event.message);
}
```
###

























+++
