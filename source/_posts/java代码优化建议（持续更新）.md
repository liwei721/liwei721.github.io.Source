---
title: java代码优化建议（持续更新）
date: 2017-03-02 16:47:37
tags: java ,代码优化, findbugs
categories: java知识
---

## 背景
- 最近做静态代码扫描过程中，收集了一些开发经常使用，但是可以优化的代码，整理出来，供大家参考和交流。
- 使用的静态代码扫描工具：findbugs、360火线。

## 问题记录
### Integer.valueOf("XXXXX")
- 这种写法主要会影响代码性能，建议使用Integer.parseInt("XXXX")
- 通过阅读源码我们可以知道，Integer.valueOf("a") 当a在[-128, 128]之间时本地有缓存返回int类型，但是不在这个区间，比如大于128时，会new Integer(a)返回。而Integer.paseInt("a") 始终返回的是int类型。
- 所以Integer.valueOf("XXXXX")这种写法，会多了一次从int到Integer（自动装箱）和 从Integer到int（自动拆箱）的过程，从而影响效率。

### 循环中使用 + 来拼接String
- 这种写法对性能影响很大。用 加号 拼接String，比较方便，所以很多人习惯性的就这么用。
- 以 cc = "aa" + "bb"为例，编译器在处理时会 cc = new StringBuffer("aa").append("bb").toString()。
- 这明显有两个影响效率的方面：1. 每次会 new 一个对象，（循环中会增加内存碎片）  2. 每次还要调用toString 转成字符串。 如果在循环中这么用，对性能影响是相当大的。

### stream && resource 忘记关闭
- 这个问题是老生常谈的问题。但是findbugs还是扫出来很多没有close的stream。
- 不关闭资源会造成资源浪费，并且可能造成内存泄漏。
- 建议写这类代码时，只要new 出来一个资源，就立即在finally中去close资源，防止逻辑写多了，忘记close。

### 对空指针考虑不充分
- 这个问题可能在某些场景下出现，直接造成程序崩溃。下面就直接贴几段代码
``` java
    SyncPowerData syncPowerData = null;
    try {
            syncPowerData = apiFactory.getAccountApi().getSyncPowerRequest(map).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        int code = syncPowerData.getCode();
```
``` java
        FileChannel in = null;
        FileChannel out = null;
        FileInputStream inStream = null;
        FileOutputStream outStream = null;
        try {
            inStream = new FileInputStream(srcFile);
            outStream = new FileOutputStream(destFile);
            in = inStream.getChannel();
            out = outStream.getChannel();
            in.transferTo(0, in.size(), out);
        } catch (IOException e) {
            e.printStackTrace();
            LogUtil.getUtils().e("copyFile failed when copy data" + e.getMessage(),e);
            throw new FileIOException(FileIOException.ERROR_COPY_FILE);
        } finally {
            try {
                inStream.close();
                in.close();
                outStream.close();
                out.close();
            } catch (IOException e) {
```
- 上面两个例子其实类似，当有异常发生，造成对象没有初始化成功时，那么再使用对象就会抛出nullpointerException。
- 所以要养成良好的编程习惯，在可能出现null的地方判null。增加程序的健壮性

### 单例模式写法不规范
- 这个问题主要在多线程情况下会产生影响，可能多个线程调用返回的是不同的对象，从而失去了单例的意义。
- 建议使用双重校验锁模式，代码如下所示：
``` java
public class Singleton{  
  private volatile static Singleton single;    //声明静态的单例对象的变量  
  private Singleton(){}    //私有构造方法   

  public static Singleton getSingle(){    //外部通过此方法可以获取对象    
    if(single == null){     
        synchronized (Singleton.class) {   //保证了同一时间只能只能有一个对象访问此同步块        
            if(single == null){      
                single = new Singleton();          
        }     
      }  
    }    
    return single;   //返回创建好的对象   
  }  
}  
```
- 注意这种方法一定要加volatile，主要在于instance = new Singleton()这句，这并非是一个原子操作，事实上在 JVM 中这句话大概做了下面 3 件事情:
>- 给 instance 分配内存
>- 调用 Singleton 的构造函数来初始化成员变量
>- 将instance对象指向分配的内存空间（执行完这步 instance 就为非 null 了）
>- 但是在 JVM 的即时编译器中存在指令重排序的优化。也就是说上面的第二步和第三步的顺序是不能保证的，最终的执行顺序可能是 1-2-3 也可能是 1-3-2。如果是后者，则在 3 执行完毕、2 未执行之前，被线程二抢占了，这时 instance 已经是非 null 了（但却没有初始化），所以线程二会直接返回 instance，然后使用，然后顺理成章地报错。

- 加volatile最主要的作用是禁止指令重排。关于指令重排的知识，大家可以网上查资料[单例详细介绍](http://wuchong.me/blog/2014/08/28/how-to-correctly-write-singleton-pattern/)
