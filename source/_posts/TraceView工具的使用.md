---
title: TraceView工具的使用
date: 2016-10-19 15:50:49
tags: TraceView,工具
categories: 测试工具
---
## TraceView简介
- 在性能分析和定位过程中，TraceView是使用比较多的一个工具，在遇到APP启动时间过长、界面切换时间比较长以及卡顿的时候，都可以先用TraceView来查看方法调用栈，查看比较耗时的方法以及各个线程的执行情况。
- Traceview是Android平台特有的数据采集和分析工具,它主要用于分析Android中应用程序的性能问题。Traceview本身只是一个数据分析工具,而数据的采集则需要使用Android SDK中的Debug类或者利用DDMS工具。

## TraceView的使用
#### 数据采集
- 上面提到，数据采集有两种方式，使用Android SDK中的Debug类或者利用DDMS工具，下面就分别介绍下：

##### Android SDK中Debug类
- 开发者在一些关键代码段开始前调用Android SDK中Debug类的startMethodTracing函数,并在关键代码段结束前调用stopMethodTracing函数。这两个函数运行过程中将采集运行时间内该应用所有线程（注意,只能是Java线程）的函数执行情况,并将采集数据保存到/mnt/sdcard/下的一个文件中。

##### DDMS工具
- 借助Android SDK中的DDMS工具。DDMS可采集系统中某个正在运行的进程的函数调用信息。对开发者而言,此方法适用于没有目标应用源代码的情况。DDMS工具中Traceview的使用如图所示。
![DDMS_traceView的使用](/upload/image/Traceview_ddms.jpg)
- 在对Android4.4以上手机点击TraceView按钮的时候，会出现2中方式的选择对话框，如下图所示：
![TraceView DDMS](/upload/image/traceview_choose.png)
- Sample based profiling：以固定的频率像VM发送中断,并搜集调用栈信息。低版本手机也是采用该方式来采集样本的,默认是1毫秒采集一次。精确度和采集的频率有关,间隔频率越小会越精确,但运行也会相应的更慢。一般我们默认用1000微秒就足够了。
- Trace based profiling：不论多小的函数,都会跟踪整个函数的执行过程,所以开销也会很大。运行起来会非常的慢,不适合检测滑动性能。

##### 上面两种方式比较
- 使用Android Debug类一般还需要去熟悉代码，且采集到的数据还要pull到本地，然后转换成MAT（参考MAT的使用），使用起来相对麻烦，所以对于测试同学来说DDMS是比较好的方式，但是对于某些场景是需要用Debug类方式来测试的，比如APP首次启动，因为进程还没创建，所以DDMS没法用。
- 在做性能分析的过程中,基本都是采用DDMS工具中来启动TraceView,这样简单易用,随便哪个地方的代码都可以跟踪。所以一般测试过程中，我们用DDMS工具的方式比较多。

#### 数据分析
##### 初识Traceview界面
- 使用DDMS工具采集数据的方式，暂停之后会自动打开一个分析数据的界面，如下图所示：![TraceView面板](/upload/image/trace_view_panel.png)
- 不过需要注意的是，在DDMS工具中打开的Traceview界面中的搜索框是没作用的，你不管输入任何东西也过滤不出来。如果想使用搜索功能（搜索功能其实还挺有用），可以通过 Android SDK tools 下的TraceView命令打开数据采集生成的trace文件（一般存放在C:\Users\xxx\AppData\Local\Temp\xxxx.trace）
- 如上图所示，Traceview其UI划分为上下两个面板,即Timeline Panel和Profile Panel。
- Timeline Panel左边是测试数据中所采集的线程信息,右边Pane所示为时间线,时间线上是每个线程测试时间段内所涉及的函数调用信息。内容的丰富代表该时间段执行的函数多,从而可以反应线程的繁忙状态。也可以看出线程的启动时间和结束时间等。开发者可以在时间线Pane中移动时间线纵轴。纵轴上边将显示当前时间点中某线程正在执行的函数信息。
- Profile Panel是Traceview的核心界面,其内涵非常丰富。它主要展示了某个线程（先在Timeline Panel中选择线程）中各个函数调用的情况,包括CPU使用时间、调用次数等信息。而这些信息正是查找性能瓶颈的关键依据。
- 两个面板之间是相互联动的，点击下面的函数，可以在时间轴上显示对应的位置（如上图所示，点击draw方法，在时间线main线程上有很多下拉框）。另外在时间线上拉伸可以放大时间线，双击顶部的时间条区域可以缩小原始状态。

##### Profile Panel中各列的含义
- 网上关于这个的介绍非常多，我这里就再搬过来写一遍，省去大家去查找的时间了。


  列名 | 描述
---- | ---- :|
Name | 该线程运行过程中所调用的函数名
Incl Cpu Time | 某函数占用的CPU时间,包含内部调用其它函数的CPU时间
Excl Cpu Time | 某函数占用的CPU时间,但不含内部调用其它函数所占用的CPU时间
Incl Real Time | 某函数运行的真实时间（以毫秒为单位）,内含调用其它函数所占用的真实时间
Excl Real Time | 数运行的真实时间（以毫秒为单位）,不含调用其它函数所占用的真实时间
Call+Recur Calls/Total | 某函数被调用次数以及递归调用次数/总调用次数
Cpu Time/Call | 某函数调用CPU时间与调用次数的比。相当于该函数平均执行时间
Real Time/Call | 同CPU Time/Call类似,只不过统计单位换成了真实时间

###### 上面这些指标中我们经常用到的有
- Incl Cpu Time 可以用来排序查找比较耗时的逻辑。
- Call+Recur Calls/Total 可以用来查看是否有重复调用的情况，比如：ListView在滑动过程中，是否多次调用getview()。
- Cpu Time/Call 可以用来查看一个方法的平均执行时间，比如：App首次启动过程中某个初始化方法init()执行时间比较长。
- 这几个指标可以排序，结合起来排查问题。
