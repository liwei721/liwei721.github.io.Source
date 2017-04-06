---
title: 常见Android内存泄漏
date: 2016-11-03 14:17:22
tags: Android,内存泄漏
categories: 测试技术
---

## 背景
- 感觉我们公司的App，比较严重的是内存问题，而内存问题比较严重的是泄漏问题，所以搞清楚常见的内存泄漏问题，有助于我们平时去排查内存泄漏问题。
- 这篇文章是参考[Android内存泄漏的八种可能](http://www.jianshu.com/p/ac00e370f83d)，然后准备结合着具体的小例子来分析。
- Android开发中，最容易引起内存泄漏问题的是Context，比如之前测安全邮件的时候，退出APP之后进程没有被杀死，所以它引用的大量的view、工具类对象都不能被释放，一泄漏就是一大片的对象。

#### java 内存分配策略
- 这个话题网上应该挺多总结的，我大概的提一下，方便大家对java内存管理有个印象。
- 静态存储区（方法区）：主要存放静态数据、全局static数据和常量。这块数据在编译时就已经分配好，并且在程序运行期间都存在，这也是静态变量会引起内存泄漏的原因，下面会多次提到。
- 栈区：当方法被执行时，方法体内的局部变量都在栈上创建，并在方法执行结束时这些局部变量所持有的内存将会自动被释放。因为栈内存分配运算内置于处理器的指令集中，效率很高，但是分配的内存容量有限，栈区的大小可以通过-xss配置。
- 堆区：又称动态内存分配，通常就是指在程序运行时直接 new 出来的内存。这部分内存在不使用时将会由 Java 垃圾回收器来负责回收。

#### 下面就分析下常见的内存泄漏问题：
## static Activities
#### 问题出现原因
- 在某个类中定义了静态的Activity变量，把当前运行的Activity实例赋值于这个静态变量（比较常见的是某些单例xxxxHelper在Activity中使用时将Activity设置进去）。静态变量的生命周期比较长，因为垃圾回收并不回收静态变量，除非我们手动的将静态变量释放掉。因此使用静态变量要特别小心，很容易导致泄漏。
- 我这里写了个例子，有两个Activity，其中一个Activity中静态变量引用另一个Activity，然后多次启动Activity。下面是FirstActivity的代码：
```java
@Override
   protected void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       setContentView(R.layout.activity_first);

       Button button = (Button) findViewById(R.id.jump_btn);
       button.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               SecondActivity.setContext(FirstActivity.this);
               Intent intent = new Intent();
               intent.setClass(FirstActivity.this, SecondActivity.class);
               FirstActivity.this.startActivity(intent);
           }
       });
   }
```
- 两个Activity跳转几次后，GC两次，然后用AndroidStudio memory Monitor抓取hprof（抓取内存快照的方法参考{% post_link MAT工具的使用  MAT工具的使用 %}），我这里用MAT进行分析（也可以用AndroidStudio分析，更方便），结果如下图所示：![staticActivity.PNG](/upload/image/zlw/staticActivity.PNG)
- 从图中可以看出来，SecondActivity通过mContext持有了FirstActivity对象的引用，造成FirstActivity不能被释放。

#### 解决方法
- 上面提到对于静态变量，需要我们自己处理，虽然静态变量不可控，但是Activity的生命周期是已知的，所以我们只需要在有静态变量的类销毁时，将静态变量置为null就可以了。在这个例子中我们是在SecondActivity销毁时，将静态变量置空
```java
@Override
protected void onDestroy() {
    super.onDestroy();
    mContext = null;
}
```

- 当然有时候可能这个静态变量是不能被立即释放掉的，这个时候我们可以用WeakReference，关于java中的引用类型，有不明白的可以Google下不难理解。
用WeakReference不会阻止对象的释放，即当GC扫描到WeakReference对象时，会立即将它回收掉。

- 修改之后再抓取Hprof，会发现FirstActivity不会被泄漏了。

## static Views
- 静态变量持有View，有时候开发同学基于自己逻辑的某些需要（比如另一个类中需要用到当前类的某些View，或者想用来缓存当前View）会将View写成静态的。我们先直接看个例子：
```java
static Button staticBtn;
  @Override
  protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_first);

      staticBtn = (Button) findViewById(R.id.jump_btn);
      staticBtn.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
//                SecondActivity.setContext(FirstActivity.this);
              Intent intent = new Intent();
              intent.setClass(FirstActivity.this, SecondActivity.class);
              FirstActivity.this.startActivity(intent);
          }
      });
  }
```
- 用MAT分析抓到的hprof，搜索发现FirstActivity泄漏，如下图：
![staticActivity.PNG](/upload/image/zlw/static_view.PNG.PNG)

- 从图中可以看出，FirstActivity通过staticBtn引用了AppCompatButton，最后又通过mContext引用了FirstActivity，又绕回来了。这是因为Activity中的View会持有对Activity的引用。所以最终还是造成Activity内存泄漏。

#### 解决方法
- 参考 static Activity的解决方法，是相似的。

## Inner Class
- 内部类我觉得在平时开发中用到的非常多，主要是因为感觉没必要重新写一个新的类，同时还能有一定的封装性。
- 内部类显著的一个特点是它持有外部类的一个引用。所以使用不当，非常容易造成内存泄漏。
- 再改造下之前的例子，让内部类持有一个静态变量的引用，主要代码如下：
``` java
staticBtn.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
//                SecondActivity.setContext(FirstActivity.this);
        createInnerClass();
        Intent intent = new Intent();
        intent.setClass(FirstActivity.this, SecondActivity.class);
        FirstActivity.this.startActivity(intent);
    }
});
}
static InnerClass mInnerClass;
class InnerClass{
}
void createInnerClass(){
mInnerClass = new InnerClass();
}
```
- 用MAT分析抓到的hprof文件，如下图所示：
![innerclass.PNG](/upload/image/zlw/innerclass.PNG)
- 从上图可以看出因为内部类因为静态变量mInnerClass,造成FirstActivity内存泄漏。正是因为内部类InnerClass会持有外部类FirstActivity的实例，而InnerClass因为静态变量无法释放造成的泄漏。

#### 解决方法
- 可以考虑使用静态内部类，静态内部类不会持有外部类的引用。
- 慎用静态变量，比如上面的例子中将mInnerClass改成非静态的是可以避免这一类的内存泄漏的。

## 匿名内部类
- 匿名内部类在Android开发中用的非常广，因为方便，比如我们设置监听事件、开启一个AsyncTask都可以用匿名内部类方便的实现。
- 匿名内部类同样会持有外部类的一个引用，这也是容易泄露的原因。这里的例子是用AsyncTask开启一个永远完不成的任务，代码如下所示：
```java
/**
    *  开启AsyncTask
    */
   void startAsyncTask(){
       new AsyncTask<Void, Void, Void>(){

           @Override
           protected Void doInBackground(Void... params) {
               while (true);
           }
       }.execute();
   }
```
- 用MAT分析hprof，经过分析，得到如下结果：
![anonymous.PNG](/upload/image/zlw/anonymous.PNG)
- 因为AsyncTask内部是线程在工作，而匿名内部类会持有外部的引用，所以当AsyncTask任务没完成时，会造成外部类引用释放不了，重而内存泄漏。所以当见到GCRoot[GCroot的概念参考{% post_link MAT工具的使用  MAT工具的使用 %}]是Thread时，就可以怀疑是不是Thread的任务没完成，造成的内存泄漏。

#### 解决方法
- 使用静态内部类，静态内部类没有外部类的引用。
- 在页面退出时，终止线程的任务，比如本例中的AsyncTask，在退出页面时可以cancel任务。这样也能避免内存泄漏。

## Handler
- 开发中也经常定义一个匿名Runnable对象并交给Handler去处理，比如做延时操作等。这种写法也是非常容易造成内存泄漏的。因为Runnable对象间接引用了外部类Activity对象，然后Runnable会被提交到Handler的MessageQueue中，所以如果Activity 销毁时Runnable还没有被执行，那么activity就会被泄漏掉。
- 我们写个例子，用来执行一个延时任务，代码如下所示：
```java
/**
  * 创建一个Handler
  */
 void createHandler(){
     new Handler(){
         @Override
         public void handleMessage(Message msg) {
             super.handleMessage(msg);
         }
     }.postDelayed(new Runnable() {
         @Override
         public void run() {
             while (true);
         }
     }, Long.MAX_VALUE >> 1);
 }
```
- 页面切换几次后，用AndroidStudio抓取hprof数据，用MAT分析结果如下图所示：
![handler.PNG](/upload/image/zlw/handler.PNG)
- 这个内存泄漏结果是比较常见的，GCroot  是Thread，它以java Local的形式被Message持有，表示MessageQueue中还有message在处理，造成了内存泄漏。

#### 解决方法
- 参考上面匿名内部类的处理方法

## service Manager
- 通过Context.getSystemService(int name)可以获取服务，这些服务工作在各自的进程中，帮助应用处理后台任务，处理硬件交互。如果需要使用这些服务，可以注册监听器，这会导致服务持有了Context的引用，如果在Activity销毁的时候没有注销这些监听器，会导致内存泄漏。
- 我们就用SensorManager来写个例子，验证下，代码如下：
```java
/**
    *  注册Listener
    */
   void registerListener(){
       SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
       Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ALL);
       sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
   }
```

- 用MAT分析hprof数据，如下图所示：
![service.PNG](/upload/image/zlw/service.PNG)
- 可以看到，注册Listener的时候，sensor会持有外部类的一个实例。造成内存泄漏。

#### 解决方法
- 在页面退出时，能够unregisteListener，这样就能够释放外部类引用，避免内存泄漏。
- 在开发的过程中，非常容易忘记去UNregisterListener，所以好的习惯是，写一个register之后立马写一个Unregister，这样可以避免忘记反注册。

## 集合类的泄漏
- 集合在平时开发中也是被大量使用的，如果这个集合类是由静态变量引用，并且只添加了元素，但是在不用的时候并没有释放元素，那么就有可能造成内存泄漏。不过由于如果元素不清理的话可能会影响程序的逻辑，所以一般可以避免这种情况，但是稍有不留意，也是非常容易出现内存泄漏的。
- 这里就不举例子说明了，这种场景相对简单。

#### 解决方案
- 在页面退出时，要清空不用的数据集合。

## 其他常见泄漏场景
- 资源未关闭造成的内存泄漏， 比如：File、Cursor、Stream、Bitmap等资源，他们在不使用的时候一定要及时销毁回收。特别是Bitmap，会占用非常大的内存空间。

## 总结
我们从上面的例子中可以总结出，造成内存泄漏的原因有下面两种：
- 过多的使用static变量，并且没有正确释放不用的对象。
- 对象的生命周期（比如Thread）比Activity的生命周期长，造成内存泄漏。

这里就先总结了常见的内存泄漏场景，后面碰见比较经典的问题再总结。
